package app.bolo.billing;

import app.bolo.PostgresTestBase;
import app.bolo.talk.FreePlanAcceptanceTest;
import app.bolo.talk.TalkTestClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/** P2d: invite 3 friends, get a month of Premium (ACC-8 / US-8.10, Premium per the founder). */
class ReferralAcceptanceTest {

    abstract static class Base extends PostgresTestBase {
        @LocalServerPort int port;
        @Autowired JdbcClient jdbc;
        TalkTestClient client;

        @BeforeEach
        void setUp() {
            client = new TalkTestClient(port);
        }

        static String uid() {
            return UUID.randomUUID().toString();
        }

        /** Signs in to Google account {@code uid} from {@code cookie} (null: a fresh browser), with an optional ref code. */
        TalkTestClient.Reply signIn(String cookie, String uid, String name, String ref) throws Exception {
            String body = "{\"idToken\":\"fake:" + uid + ":" + uid.substring(0, 8) + "@x.com:" + name + "\""
                    + (ref == null ? "" : ",\"ref\":\"" + ref + "\"") + "}";
            return client.send("POST", "/api/auth/google", cookie, body);
        }

        String account(String name) throws Exception {
            return signIn(client.consentedGuest(5), uid(), name, null).cookie();
        }

        String code(String cookie) throws Exception {
            var r = client.send("GET", "/api/referral", cookie, "");
            assertThat(r.status()).as(r.body()).isEqualTo(200);
            return JsonPath.read(r.body(), "$.code");
        }

        /** A new friend joins through the code; talks: how many counted talks they then have. */
        String friend(String code, String name, int talks) throws Exception {
            String cookie = signIn(client.consentedGuest(5), uid(), name, code).cookie();
            if (talks > 0) FreePlanAcceptanceTest.talkTimes(client, jdbc, cookie, talks);
            return cookie;
        }

        String status(String cookie) throws Exception {
            return client.send("GET", "/api/referral", cookie, "").body();
        }

        String plan(String cookie) throws Exception {
            String home = client.send("GET", "/api/home", cookie, "").body();
            return JsonPath.read(home, "$.freeTalks.plan");
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    class Offer extends Base {

        @Test
        void threeFriendsWhoTalkGiveAMonthOfPremium() throws Exception {
            String me = account("Rafi Ahmed");
            String code = code(me);
            assertThat(code).matches("[a-z0-9]{8}");
            friend(code, "Nadia Islam", 1);
            friend(code, "Tanim Rahman", 1);
            assertThat((List<String>) JsonPath.read(status(me), "$.friends")).containsExactlyInAnyOrder("Nadia", "Tanim");
            assertThat(plan(me)).isEqualTo("free");
            friend(code, "Sumi Akter", 1);
            String s = status(me);
            assertThat((Integer) JsonPath.read(s, "$.counted")).isEqualTo(3);
            assertThat((Integer) JsonPath.read(s, "$.rewards")).isEqualTo(1);
            assertThat((Boolean) JsonPath.read(s, "$.newReward")).isTrue();
            assertThat((String) JsonPath.read(s, "$.premiumUntil")).isNotNull();
            assertThat(plan(me)).isEqualTo("premium");
            client.send("POST", "/api/referral/seen", me, "{}");
            assertThat((Boolean) JsonPath.read(status(me), "$.newReward")).isFalse();
        }

        @Test
        void aFriendWhoHasntTalkedDoesntCount() throws Exception {
            String me = account("Rafi");
            String code = code(me);
            friend(code, "Quiet One", 0);
            String s = status(me);
            assertThat((Integer) JsonPath.read(s, "$.counted")).isZero();
            assertThat((List<String>) JsonPath.read(s, "$.friends")).isEmpty();
        }

        @Test
        void signingIntoAnExistingAccountDoesntCount() throws Exception {
            String me = account("Rafi");
            String code = code(me);
            String u = uid();
            String old = signIn(client.consentedGuest(5), u, "Old Friend", null).cookie();
            FreePlanAcceptanceTest.talkTimes(client, jdbc, old, 1);
            signIn(client.consentedGuest(5), u, "Old Friend", code);
            assertThat((Integer) JsonPath.read(status(me), "$.counted")).isZero();
        }

        @Test
        void yourOwnLinkDoesntCount() throws Exception {
            String u = uid();
            String me = signIn(client.consentedGuest(5), u, "Rafi", null).cookie();
            String code = code(me);
            signIn(null, u, "Rafi", code);
            assertThat(jdbc.sql("select count(*) from referral where referrer_id = friend_id").query(Integer.class).single()).isZero();
            assertThat((Integer) JsonPath.read(status(me), "$.counted")).isZero();
        }

        @Test
        void aRecreatedDeletedAccountDoesntCount() throws Exception {
            String me = account("Rafi");
            String code = code(me);
            String u = uid();
            String first = signIn(client.consentedGuest(5), u, "Again", null).cookie();
            FreePlanAcceptanceTest.talkTimes(client, jdbc, first, 1);
            assertThat(client.send("POST", "/api/me/delete", first, "{\"confirm\":\"DELETE\"}").status()).isEqualTo(204);
            String again = signIn(client.consentedGuest(5), u, "Again", code).cookie();
            FreePlanAcceptanceTest.talkTimes(client, jdbc, again, 1);
            assertThat((Integer) JsonPath.read(status(me), "$.counted")).isZero();
        }

        @Test
        void twoLoadsAtOnceGrantOnce() throws Exception {
            String me = account("Rafi");
            String code = code(me);
            for (int i = 0; i < 3; i++) friend(code, "Friend" + i, 1);
            var a = CompletableFuture.supplyAsync(() -> { try { return status(me); } catch (Exception e) { throw new RuntimeException(e); } });
            var b = CompletableFuture.supplyAsync(() -> { try { return status(me); } catch (Exception e) { throw new RuntimeException(e); } });
            a.join();
            b.join();
            assertThat(jdbc.sql("select count(*) from subscription s join account c on c.learner_id = s.learner_id "
                    + "where s.source = 'referral' and s.learner_id = (select learner_id from referral_code where code = ?)")
                    .param(code).query(Integer.class).single()).isEqualTo(1);
        }

        @Test
        void atMostThreeRewards() throws Exception {
            String me = account("Rafi");
            String code = code(me);
            for (int i = 0; i < 12; i++) friend(code, "Friend" + i, 1);
            String s = status(me);
            assertThat((Integer) JsonPath.read(s, "$.rewards")).isEqualTo(3);
            assertThat((Integer) JsonPath.read(s, "$.maxRewards")).isEqualTo(3);
        }

        @Test
        void aGuestIsAskedToSignInAndNoCookieIs401() throws Exception {
            assertThat(client.send("GET", "/api/referral", client.consentedGuest(5), "").status()).isEqualTo(422);
            assertThat(client.send("GET", "/api/referral", null, "").status()).isEqualTo(401);
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "bolo.referral.until=2020-01-01")
    class OfferEnded extends Base {

        @Test
        void afterTheOfferEndsNoOfferAndNoNewFriends() throws Exception {
            String me = account("Rafi");
            String s = status(me);
            assertThat((Boolean) JsonPath.read(s, "$.offer")).isFalse();
            friend(code(me), "Late Friend", 1);
            assertThat((Integer) JsonPath.read(status(me), "$.counted")).isZero();
        }
    }
}
