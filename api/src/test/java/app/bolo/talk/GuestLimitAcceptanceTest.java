package app.bolo.talk;

import app.bolo.PostgresTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** ACC-2 / US-8.2: three guest talks, then sign in to keep talking. */
class GuestLimitAcceptanceTest {

    static void talkTimes(TalkTestClient client, JdbcClient jdbc, String cookie, int n) throws Exception {
        for (int i = 0; i < n; i++) {
            String id = TalkTestClient.id(client.startTalk(cookie));
            jdbc.sql("update talk_session set attached_at = now(), ended_at = now(), end_reason = 'learner_ended' where id = ?")
                    .param(UUID.fromString(id)).update();
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"bolo.talk.max-live-sessions=50",
            "bolo.firebase.api-key=k", "bolo.firebase.auth-domain=d", "bolo.firebase.project-id=p", "bolo.firebase.app-id=a"})
    class SignInOn extends PostgresTestBase {
        @LocalServerPort int port;
        @Autowired JdbcClient jdbc;
        TalkTestClient client;

        @BeforeEach
        void setUp() {
            jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
            client = new TalkTestClient(port);
        }

        @Test
        void theFourthGuestTalkNeedsSignIn() throws Exception {
            String guest = client.consentedGuest(5);
            talkTimes(client, jdbc, guest, 3);
            var r = client.startTalk(guest);
            assertThat(r.status()).isEqualTo(422);
            assertThat(r.body()).contains("sign-in");
        }

        @Test
        void aCreatedButNeverStartedTalkDoesNotCount() throws Exception {
            String guest = client.consentedGuest(5);
            talkTimes(client, jdbc, guest, 2);
            client.startTalk(guest);
            assertThat(client.startTalk(guest).status()).isEqualTo(201);
        }

        @Test
        void anAccountHasNoGuestLimit() throws Exception {
            String guest = client.consentedGuest(5);
            talkTimes(client, jdbc, guest, 3);
            String account = client.send("POST", "/api/auth/google", guest, "{\"idToken\":\"fake:lim" + UUID.randomUUID() + ":a@x.com\"}").cookie();
            assertThat((String) JsonPath.read(client.send("GET", "/api/me", account, "").body(), "$.kind")).isEqualTo("account");
            assertThat(client.startTalk(account).status()).isEqualTo(201);
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"bolo.talk.max-live-sessions=50"})
    class SignInOff extends PostgresTestBase {
        @LocalServerPort int port;
        @Autowired JdbcClient jdbc;

        @Test
        void withSignInOffGuestsAreNotLimited() throws Exception {
            jdbc.sql("update talk_session set ended_at = now(), end_reason = 'learner_ended' where ended_at is null").update();
            TalkTestClient client = new TalkTestClient(port);
            String guest = client.consentedGuest(5);
            talkTimes(client, jdbc, guest, 3);
            assertThat(client.startTalk(guest).status()).isEqualTo(201);
        }
    }
}
