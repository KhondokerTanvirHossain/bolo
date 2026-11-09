package app.bolo.learner;

import app.bolo.PostgresTestBase;
import app.bolo.media.application.port.in.AudioStoreUseCase;
import app.bolo.talk.TalkTestClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** PRG-2 / US-6.1: the intro again on Day 7, 14, 21 (then every 30 days), audio kept only by the learner's choice. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VoiceIntroAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired AudioStoreUseCase audio;
    TalkTestClient client;
    final HttpClient http = HttpClient.newHttpClient();
    static final byte[] WEBM = Day1AcceptanceTest.webm();

    @BeforeEach
    void setUp() {
        client = new TalkTestClient(port);
    }

    /** A learner who signed up {@code daysAgo} days ago, keeping recordings or not. */
    String learner(int daysAgo, boolean keep) throws Exception {
        String cookie = client.guest();
        client.send("POST", "/api/consent", cookie, "{\"keepRecordings\":" + keep + ",\"over13\":true,\"policyVersion\":\"2026-11-v1\"}");
        jdbc.sql("update learner set created_at = now() - make_interval(days => ?) where id = ?").params(daysAgo, id(cookie)).update();
        return cookie;
    }

    UUID id(String cookie) throws Exception {
        return UUID.fromString(JsonPath.read(client.send("GET", "/api/me", cookie, "").body(), "$.learnerId"));
    }

    HttpResponse<String> record(String cookie, int day, byte[] clip) throws Exception {
        String b = "bolo" + System.nanoTime();
        var body = new java.io.ByteArrayOutputStream();
        if (clip != null) {
            body.writeBytes(("--" + b + "\r\nContent-Disposition: form-data; name=\"audio\"; filename=\"intro\"\r\nContent-Type: audio/webm\r\n\r\n").getBytes());
            body.writeBytes(clip);
            body.writeBytes("\r\n".getBytes());
        } else {
            body.writeBytes(("--" + b + "\r\nContent-Disposition: form-data; name=\"kept\"\r\n\r\ndevice\r\n").getBytes());
        }
        body.writeBytes(("--" + b + "--\r\n").getBytes());
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/me/intro/" + day)).header("Origin", TalkTestClient.ORIGIN)
                .header("X-Bolo", "1").header("Cookie", cookie).header("Content-Type", "multipart/form-data; boundary=" + b)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<byte[]> clip(String cookie, int day) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/me/intro/" + day)).header("Cookie", cookie).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    String voice(String cookie) throws Exception {
        return client.send("GET", "/api/me/voice", cookie, "").body();
    }

    @Test
    void voiceShowsTheScheduleFromSignUp() throws Exception {
        String me = learner(6, true);
        String v = voice(me);
        assertThat((Integer) JsonPath.read(v, "$.due")).isEqualTo(7);
        assertThat((Integer) JsonPath.read(v, "$.nextDay")).isEqualTo(14);
        assertThat((List<?>) JsonPath.read(v, "$.recordings")).isEmpty();
        assertThat(voice(learner(2, true))).contains("\"due\":null");
    }

    @Test
    void aKeptIntroIsStoredAndServedToItsOwner() throws Exception {
        String me = learner(6, true);
        assertThat(record(me, 7, WEBM).statusCode()).isEqualTo(204);
        var back = clip(me, 7);
        assertThat(back.statusCode()).isEqualTo(200);
        assertThat(back.body()).isEqualTo(WEBM);
        String v = voice(me);
        assertThat((Integer) JsonPath.read(v, "$.recordings[0].day")).isEqualTo(7);
        assertThat((Boolean) JsonPath.read(v, "$.recordings[0].onServer")).isTrue();
        assertThat(v).contains("\"due\":null");
    }

    @Test
    void notKeptRefusesAudio() throws Exception {
        String me = learner(6, false);
        var r = record(me, 7, WEBM);
        assertThat(r.statusCode()).isEqualTo(422);
        assertThat(r.body()).contains("not-kept");
        assertThat(clip(me, 7).statusCode()).isEqualTo(404);
    }

    @Test
    void notKeptRecordsTheDateOnly() throws Exception {
        String me = learner(6, false);
        assertThat(record(me, 7, null).statusCode()).isEqualTo(204);
        String v = voice(me);
        assertThat((Boolean) JsonPath.read(v, "$.recordings[0].onServer")).isFalse();
        assertThat(v).contains("\"due\":null");
        assertThat(clip(me, 7).statusCode()).isEqualTo(404);
    }

    @Test
    void aDayNotYetUnlockedIsRefused() throws Exception {
        String me = learner(2, true);
        assertThat(record(me, 7, WEBM).body()).contains("not-yet");
        assertThat(record(learner(30, true), 8, WEBM).body()).contains("not-yet");
    }

    @Test
    void recordingAgainReplaces() throws Exception {
        String me = learner(6, true);
        record(me, 7, WEBM);
        byte[] second = WEBM.clone();
        second[100] = 42;
        record(me, 7, second);
        assertThat(clip(me, 7).body()).isEqualTo(second);
        assertThat((List<?>) JsonPath.read(voice(me), "$.recordings")).hasSize(1);
    }

    @Test
    void anotherLearnerGets404() throws Exception {
        String me = learner(6, true);
        record(me, 7, WEBM);
        assertThat(clip(learner(6, true), 7).statusCode()).isEqualTo(404);
    }

    @Test
    void deleteEverythingRemovesIntros() throws Exception {
        String me = learner(6, true);
        UUID id = id(me);
        record(me, 7, WEBM);
        assertThat(client.send("POST", "/api/me/delete", me, "{\"confirm\":\"DELETE\"}").status()).isEqualTo(204);
        assertThat(audio.children("learner/" + id)).isEmpty();
        assertThat(jdbc.sql("select count(*) from voice_intro where learner_id = ?").param(id).query(Integer.class).single()).isZero();
    }
}
