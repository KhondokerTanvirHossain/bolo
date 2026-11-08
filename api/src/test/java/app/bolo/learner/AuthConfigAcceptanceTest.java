package app.bolo.learner;

import app.bolo.PostgresTestBase;
import app.bolo.talk.TalkTestClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/** The web reads Firebase's public config at runtime; without all four values, sign-in is simply off. */
class AuthConfigAcceptanceTest {

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    class Off extends PostgresTestBase {
        @LocalServerPort int port;

        @Test
        void offWithoutTheFourValues() throws Exception {
            var r = new TalkTestClient(port).send("GET", "/api/auth/config", null, "");
            assertThat(r.status()).isEqualTo(200);
            assertThat(r.body()).isEqualTo("{\"enabled\":false}");
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
            "bolo.firebase.api-key=AIza-test", "bolo.firebase.auth-domain=bolo-test.firebaseapp.com",
            "bolo.firebase.project-id=bolo-test", "bolo.firebase.app-id=1:2:web:3"})
    class On extends PostgresTestBase {
        @LocalServerPort int port;

        @Test
        void onWithThem() throws Exception {
            String body = new TalkTestClient(port).send("GET", "/api/auth/config", null, "").body();
            assertThat(body).contains("\"enabled\":true").contains("\"projectId\":\"bolo-test\"").contains("\"apiKey\":\"AIza-test\"")
                    .contains("\"authDomain\":\"bolo-test.firebaseapp.com\"").contains("\"appId\":\"1:2:web:3\"");
        }
    }
}
