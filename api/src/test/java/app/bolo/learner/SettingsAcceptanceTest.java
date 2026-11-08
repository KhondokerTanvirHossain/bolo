package app.bolo.learner;

import app.bolo.PostgresTestBase;
import app.bolo.talk.TalkTestClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/** SET-1/SET-2: Bolo's speed and captions, kept per learner and returned on /api/me. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SettingsAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    TalkTestClient client;
    String cookie;

    @BeforeEach
    void setUp() throws Exception {
        client = new TalkTestClient(port);
        cookie = client.consentedGuest(5);
    }

    @Test
    void defaultsBeforeAnyChoice() throws Exception {
        String me = client.send("GET", "/api/me", cookie, "").body();
        assertThat((String) JsonPath.read(me, "$.settings.buddySpeed")).isEqualTo("normal");
        assertThat((Boolean) JsonPath.read(me, "$.settings.captions")).isTrue();
    }

    @Test
    void savedSettingsComeBackOnMe() throws Exception {
        assertThat(client.send("PUT", "/api/me/settings", cookie, "{\"buddySpeed\":\"slower\",\"captions\":false}").status()).isEqualTo(204);
        String me = client.send("GET", "/api/me", cookie, "").body();
        assertThat((String) JsonPath.read(me, "$.settings.buddySpeed")).isEqualTo("slower");
        assertThat((Boolean) JsonPath.read(me, "$.settings.captions")).isFalse();
    }

    @Test
    void aBadSpeedIs422() throws Exception {
        var r = client.send("PUT", "/api/me/settings", cookie, "{\"buddySpeed\":\"turbo\",\"captions\":true}");
        assertThat(r.status()).isEqualTo(422);
        assertThat(r.body()).contains("buddy-speed");
    }

    @Test
    void signedOutIs401() throws Exception {
        assertThat(client.send("PUT", "/api/me/settings", null, "{\"buddySpeed\":\"slower\",\"captions\":true}").status()).isEqualTo(401);
    }
}
