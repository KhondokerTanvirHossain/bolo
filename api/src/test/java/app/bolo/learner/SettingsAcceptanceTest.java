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

    /** P3a: Settings → Talk view, Chat (the default) or Classic (TLK-8, TLK-16 changed 2026-11-12). */
    @Test
    void talkViewDefaultsToChat() throws Exception {
        String me = client.send("GET", "/api/me", cookie, "").body();
        assertThat((String) JsonPath.read(me, "$.settings.talkView")).isEqualTo("chat");
    }

    @Test
    void talkViewSavesAndSurvivesOtherChanges() throws Exception {
        assertThat(client.send("PUT", "/api/me/settings", cookie, "{\"buddySpeed\":\"normal\",\"captions\":true,\"talkView\":\"classic\"}").status()).isEqualTo(204);
        assertThat(client.send("PUT", "/api/me/settings", cookie, "{\"buddySpeed\":\"slower\",\"captions\":true}").status()).isEqualTo(204);
        String me = client.send("GET", "/api/me", cookie, "").body();
        assertThat((String) JsonPath.read(me, "$.settings.talkView")).isEqualTo("classic");
        assertThat((String) JsonPath.read(me, "$.settings.buddySpeed")).isEqualTo("slower");
    }

    @Test
    void aBadTalkViewIs422() throws Exception {
        var r = client.send("PUT", "/api/me/settings", cookie, "{\"buddySpeed\":\"normal\",\"captions\":true,\"talkView\":\"cinema\"}");
        assertThat(r.status()).isEqualTo(422);
        assertThat(r.body()).contains("talk-view");
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

    /** P3c (SET-5, SET-6, 2026-11-13): Bolo's voice and mood. */
    @Test
    void voiceAndMoodDefault() throws Exception {
        String me = client.send("GET", "/api/me", cookie, "").body();
        assertThat((String) JsonPath.read(me, "$.settings.voiceGender")).isEqualTo("female");
        assertThat((String) JsonPath.read(me, "$.settings.voiceAccent")).isEqualTo("british");
        assertThat((String) JsonPath.read(me, "$.settings.mood")).isEqualTo("friendly");
    }

    @Test
    void voiceAndMoodSave() throws Exception {
        assertThat(client.send("PUT", "/api/me/settings", cookie,
                "{\"buddySpeed\":\"normal\",\"captions\":true,\"voiceGender\":\"male\",\"voiceAccent\":\"american\",\"mood\":\"funny\"}").status()).isEqualTo(204);
        String me = client.send("GET", "/api/me", cookie, "").body();
        assertThat((String) JsonPath.read(me, "$.settings.voiceGender")).isEqualTo("male");
        assertThat((String) JsonPath.read(me, "$.settings.voiceAccent")).isEqualTo("american");
        assertThat((String) JsonPath.read(me, "$.settings.mood")).isEqualTo("funny");
    }

    @Test
    void oldClientsKeepVoiceAndMood() throws Exception {
        client.send("PUT", "/api/me/settings", cookie,
                "{\"buddySpeed\":\"normal\",\"captions\":true,\"voiceGender\":\"male\",\"voiceAccent\":\"indian\",\"mood\":\"calm\"}");
        assertThat(client.send("PUT", "/api/me/settings", cookie, "{\"buddySpeed\":\"slower\",\"captions\":true}").status()).isEqualTo(204);
        String me = client.send("GET", "/api/me", cookie, "").body();
        assertThat((String) JsonPath.read(me, "$.settings.voiceGender")).isEqualTo("male");
        assertThat((String) JsonPath.read(me, "$.settings.voiceAccent")).isEqualTo("indian");
        assertThat((String) JsonPath.read(me, "$.settings.mood")).isEqualTo("calm");
        assertThat((String) JsonPath.read(me, "$.settings.buddySpeed")).isEqualTo("slower");
    }

    @Test
    void badVoiceOrMoodIs422() throws Exception {
        var g = client.send("PUT", "/api/me/settings", cookie, "{\"buddySpeed\":\"normal\",\"captions\":true,\"voiceGender\":\"robot\"}");
        assertThat(g.status()).isEqualTo(422);
        assertThat(g.body()).contains("voice-gender");
        var a = client.send("PUT", "/api/me/settings", cookie, "{\"buddySpeed\":\"normal\",\"captions\":true,\"voiceAccent\":\"martian\"}");
        assertThat(a.status()).isEqualTo(422);
        assertThat(a.body()).contains("voice-accent");
        var m = client.send("PUT", "/api/me/settings", cookie, "{\"buddySpeed\":\"normal\",\"captions\":true,\"mood\":\"romantic\"}");
        assertThat(m.status()).isEqualTo(422);
        assertThat(m.body()).contains("\"mood\"");
    }
}
