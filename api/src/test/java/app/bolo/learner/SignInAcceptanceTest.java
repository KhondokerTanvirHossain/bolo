package app.bolo.learner;

import app.bolo.PostgresTestBase;
import app.bolo.talk.TalkTestClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/** ACC-1, US-8.1, US-8.4: Continue with Google (fake identity vendor). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"bolo.talk.max-live-sessions=50"})
class SignInAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    TalkTestClient client;

    @BeforeEach
    void setUp() {
        jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
        client = new TalkTestClient(port);
    }

    static String uid() {
        return "u" + UUID.randomUUID().toString().substring(0, 8);
    }

    TalkTestClient.Reply signIn(String cookie, String uid) throws Exception {
        return client.send("POST", "/api/auth/google", cookie, "{\"idToken\":\"fake:" + uid + ":" + uid + "@example.com\"}");
    }

    String learnerId(String cookie) throws Exception {
        return JsonPath.read(client.send("GET", "/api/me", cookie, "").body(), "$.learnerId");
    }

    int talksOf(String learnerId) {
        return jdbc.sql("select count(*) from talk_session where learner_id = ?").param(UUID.fromString(learnerId)).query(Integer.class).single();
    }

    @Test
    void aGuestBecomesAnAccountAndKeepsItsTalks() throws Exception {
        String guest = client.consentedGuest(5);
        String id = learnerId(guest);
        client.startTalk(guest);
        var r = signIn(guest, uid());
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.cookie()).isNotEqualTo(guest);
        assertThat((String) JsonPath.read(r.body(), "$.kind")).isEqualTo("account");
        assertThat(learnerId(r.cookie())).isEqualTo(id);
        assertThat(talksOf(id)).isEqualTo(1);
        assertThat(client.send("GET", "/api/me", guest, "").status()).as("the guest cookie is retired").isEqualTo(401);
    }

    @Test
    void signingIntoAnExistingAccountMovesTheGuestsTalksAndDebriefs() throws Exception {
        String u = uid();
        String account = signIn(client.consentedGuest(5), u).cookie();
        String accountId = learnerId(account);

        String guest = client.consentedGuest(5);
        String guestId = learnerId(guest);
        String talk1 = TalkTestClient.id(client.startTalk(guest));
        jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where id = ?").param(UUID.fromString(talk1)).update();
        jdbc.sql("insert into debrief (session_id, status, body, updated_at) values (?, 'ready', '{\"status\":\"ready\"}'::jsonb, now())")
                .param(UUID.fromString(talk1)).update();
        TalkTestClient.id(client.startTalk(guest));

        var r = signIn(guest, u);
        assertThat(r.status()).isEqualTo(200);
        assertThat(learnerId(r.cookie())).isEqualTo(accountId);
        assertThat(talksOf(accountId)).isEqualTo(2);
        assertThat(jdbc.sql("select count(*) from learner where id = ?").param(UUID.fromString(guestId)).query(Integer.class).single()).isZero();
        assertThat(client.send("GET", "/api/talk/" + talk1 + "/debrief", r.cookie(), "").status()).isEqualTo(200);
    }

    @Test
    void aNewDeviceWithNoGuestSignsStraightIn() throws Exception {
        String u = uid();
        String accountId = learnerId(signIn(client.consentedGuest(5), u).cookie());
        var r = signIn(null, u);
        assertThat(r.status()).isEqualTo(200);
        assertThat(learnerId(r.cookie())).isEqualTo(accountId);
    }

    @Test
    void twoRacingSignInsMakeOneAccount() throws Exception {
        String guest = client.consentedGuest(5);
        String u = uid();
        var a = CompletableFuture.supplyAsync(() -> { try { return signIn(guest, u); } catch (Exception e) { throw new RuntimeException(e); } });
        var b = CompletableFuture.supplyAsync(() -> { try { return signIn(guest, u); } catch (Exception e) { throw new RuntimeException(e); } });
        List<TalkTestClient.Reply> both = List.of(a.get(), b.get());
        assertThat(both).extracting(TalkTestClient.Reply::status).containsOnly(200);
        assertThat(jdbc.sql("select count(*) from account where firebase_uid = ?").param(u).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void aBadTokenIs401AndCreatesNothing() throws Exception {
        int before = jdbc.sql("select count(*) from learner").query(Integer.class).single();
        var r = client.send("POST", "/api/auth/google", null, "{\"idToken\":\"nope\"}");
        assertThat(r.status()).isEqualTo(401);
        assertThat(r.body()).contains("sign-in-failed");
        assertThat(jdbc.sql("select count(*) from learner").query(Integer.class).single()).isEqualTo(before);
    }

    @Test
    void signOutEndsTheSession() throws Exception {
        String account = signIn(client.consentedGuest(5), uid()).cookie();
        assertThat(client.send("POST", "/api/auth/sign-out", account, "{}").status()).isEqualTo(204);
        assertThat(client.send("GET", "/api/me", account, "").status()).isEqualTo(401);
    }

    @Test
    void meShowsTheAccountEmail() throws Exception {
        String u = uid();
        String me = client.send("GET", "/api/me", signIn(client.consentedGuest(5), u).cookie(), "").body();
        assertThat((String) JsonPath.read(me, "$.account.email")).isEqualTo(u + "@example.com");
        assertThat(client.send("GET", "/api/me", client.consentedGuest(5), "").body()).contains("\"account\":null");
    }

    @Test
    void consentAndThe13PlusAnswerCarryOver() throws Exception {
        String me = client.send("GET", "/api/me", signIn(client.consentedGuest(5), uid()).cookie(), "").body();
        assertThat((String) JsonPath.read(me, "$.kind")).isEqualTo("account");
        assertThat((Boolean) JsonPath.read(me, "$.consent.keepRecordings")).isTrue();
        assertThat((String) JsonPath.read(me, "$.profile.goal")).isEqualTo("job_interviews");
    }
}
