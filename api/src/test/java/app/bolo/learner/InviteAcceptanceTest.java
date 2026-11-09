package app.bolo.learner;

import app.bolo.PostgresTestBase;
import app.bolo.talk.TalkTestClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The closed beta: a new guest or a new Google account needs an invite link; returning learners never do. */
class InviteAcceptanceTest {

    static String invite(JdbcClient jdbc, int maxUses) {
        String code = UUID.randomUUID().toString().substring(0, 10);
        jdbc.sql("insert into invite (code, label, max_uses, created_at) values (?, 'test', ?, now())").params(code, maxUses).update();
        return code;
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "bolo.beta.invites-required=true")
    class Required extends PostgresTestBase {
        @LocalServerPort int port;
        @Autowired JdbcClient jdbc;

        TalkTestClient.Reply newGuest(String code) throws Exception {
            return new TalkTestClient(port).send("POST", "/api/session", null, code == null ? "{}" : "{\"invite\":\"" + code + "\"}");
        }

        @Test
        void withInvitesRequiredANewGuestNeedsACode() throws Exception {
            var r = newGuest(null);
            assertThat(r.status()).isEqualTo(422);
            assertThat(r.body()).contains("invite");
            assertThat(newGuest("not-a-code").status()).isEqualTo(422);
        }

        @Test
        void aCodeWorksUntilItsLastUse() throws Exception {
            String code = invite(jdbc, 2);
            assertThat(newGuest(code).status()).isEqualTo(200);
            assertThat(newGuest(code).status()).isEqualTo(200);
            assertThat(newGuest(code).status()).isEqualTo(422);
        }

        @Test
        void aReturningLearnerIsNeverAsked() throws Exception {
            String cookie = newGuest(invite(jdbc, 1)).cookie();
            var again = new TalkTestClient(port).send("POST", "/api/session", cookie, "{}");
            assertThat(again.status()).isEqualTo(200);
        }

        @Test
        void aNewGoogleAccountNeedsACodeToo() throws Exception {
            TalkTestClient c = new TalkTestClient(port);
            String u = "inv" + UUID.randomUUID();
            assertThat(c.send("POST", "/api/auth/google", null, "{\"idToken\":\"fake:" + u + ":a@x.com\"}").status()).isEqualTo(422);
            String code = invite(jdbc, 1);
            assertThat(c.send("POST", "/api/auth/google", null, "{\"idToken\":\"fake:" + u + ":a@x.com\",\"invite\":\"" + code + "\"}").status())
                    .isEqualTo(200);
            assertThat(c.send("POST", "/api/auth/google", null, "{\"idToken\":\"fake:" + u + ":a@x.com\"}").status())
                    .as("an existing account signs in without a code").isEqualTo(200);
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    class Off extends PostgresTestBase {
        @LocalServerPort int port;

        @Test
        void withInvitesOffNobodyIsAsked() throws Exception {
            assertThat(new TalkTestClient(port).send("POST", "/api/session", null, "{}").status()).isEqualTo(200);
        }

        @Autowired JdbcClient jdbc;

        boolean unlimitedGuest(String code) throws Exception {
            TalkTestClient c = new TalkTestClient(port);
            var r = c.send("POST", "/api/session", null, "{\"invite\":\"" + code + "\"}");
            assertThat(r.status()).isEqualTo(200);
            return com.jayway.jsonpath.JsonPath.read(c.send("GET", "/api/me", r.cookie(), "").body(), "$.unlimited");
        }

        /** P1d: the probes (and the founder) aren't limited by the free plan; they come in through an unlimited invite. */
        @Test
        void anUnlimitedInviteMakesAnUnlimitedGuest() throws Exception {
            String code = invite(jdbc, 5);
            jdbc.sql("update invite set unlimited = true where code = ?").param(code).update();
            assertThat(unlimitedGuest(code)).isTrue();
        }

        @Test
        void anOrdinaryInviteMakesAnOrdinaryGuest() throws Exception {
            assertThat(unlimitedGuest(invite(jdbc, 5))).isFalse();
        }

        @Test
        void withInvitesOffAnUnknownCodeIsIgnored() throws Exception {
            assertThat(unlimitedGuest("not-a-code")).isFalse();
        }
    }
}
