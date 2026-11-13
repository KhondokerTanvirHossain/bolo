package app.bolo.speech;

import app.bolo.PostgresTestBase;
import app.bolo.speech.adapter.out.fake.FakeTextToSpeech;
import app.bolo.talk.TalkTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** P3c (SET-5, 2026-11-13): "Hear it": a short sample of each of Bolo's voices, made once and cached. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"bolo.speech.vendor=fake", "bolo.llm.vendor=fake"})
class VoiceSampleAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired FakeTextToSpeech tts;
    TalkTestClient client;
    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        client = new TalkTestClient(port);
    }

    HttpResponse<byte[]> get(String key, String cookie) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/voices/" + key + "/sample"))
                .header("Origin", TalkTestClient.ORIGIN);
        if (cookie != null) b.header("Cookie", cookie);
        return http.send(b.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    void aSampleIsAWav() throws Exception {
        String cookie = client.consentedGuest(5);
        var r = get("british-male", cookie);
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v).startsWith("audio/wav"));
        assertThat(new String(r.body(), 0, 4)).isEqualTo("RIFF");
        assertThat(tts.buddyVoices()).contains("british-male");
    }

    @Test
    void samplesAreCached() throws Exception {
        String cookie = client.consentedGuest(5);
        get("indian-male", cookie);
        int before = (int) tts.buddyVoices().stream().filter("indian-male"::equals).count();
        assertThat(get("indian-male", cookie).statusCode()).isEqualTo(200);
        assertThat(tts.buddyVoices().stream().filter("indian-male"::equals).count()).isEqualTo(before);
    }

    @Test
    void tooManySamplesIs429() throws Exception {
        String cookie = client.consentedGuest(5);
        for (int i = 0; i < 20; i++) assertThat(get("british-female", cookie).statusCode()).isEqualTo(200);
        assertThat(get("british-female", cookie).statusCode()).isEqualTo(429);
    }

    @Test
    void anUnknownVoiceIs422() throws Exception {
        var r = get("klingon-female", client.consentedGuest(5));
        assertThat(r.statusCode()).isEqualTo(422);
        assertThat(new String(r.body())).contains("voice");
    }

    @Test
    void signedOutIs401() throws Exception {
        assertThat(get("british-female", null).statusCode()).isEqualTo(401);
    }
}
