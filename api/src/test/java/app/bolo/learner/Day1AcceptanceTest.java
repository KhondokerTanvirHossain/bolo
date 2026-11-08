package app.bolo.learner;

import app.bolo.PostgresTestBase;
import app.bolo.talk.TalkTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** ONB-4 / ACC-7: the Day 1 clip is kept with the learner (only if they chose to keep recordings) and comes back on a new device. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Day1AcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;
    TalkTestClient client;
    final HttpClient http = HttpClient.newHttpClient();
    static final byte[] WEBM = webm();

    static byte[] webm() {
        byte[] b = new byte[2000];
        b[0] = 0x1A; b[1] = 0x45; b[2] = (byte) 0xDF; b[3] = (byte) 0xA3;
        for (int i = 4; i < b.length; i++) b[i] = (byte) i;
        return b;
    }

    @BeforeEach
    void setUp() {
        client = new TalkTestClient(port);
    }

    HttpResponse<byte[]> put(String cookie, String type, byte[] clip) throws Exception {
        String b = "bolo" + System.nanoTime();
        var body = new java.io.ByteArrayOutputStream();
        body.writeBytes(("--" + b + "\r\nContent-Disposition: form-data; name=\"audio\"; filename=\"day1\"\r\nContent-Type: " + type + "\r\n\r\n").getBytes());
        body.writeBytes(clip);
        body.writeBytes(("\r\n--" + b + "--\r\n").getBytes());
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/me/day1")).header("Origin", TalkTestClient.ORIGIN)
                .header("X-Bolo", "1").header("Cookie", cookie).header("Content-Type", "multipart/form-data; boundary=" + b)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    HttpResponse<byte[]> get(String cookie) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/me/day1")).header("Cookie", cookie).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    void aKeptDay1ClipIsStoredAndComesBack() throws Exception {
        String cookie = client.consentedGuest(5);
        assertThat(put(cookie, "audio/webm;codecs=opus", WEBM).statusCode()).isEqualTo(204);
        var back = get(cookie);
        assertThat(back.statusCode()).isEqualTo(200);
        assertThat(back.body()).isEqualTo(WEBM);
        assertThat(back.headers().firstValue("Content-Type").orElse("")).startsWith("audio/webm");
    }

    @Test
    void noConsentNoUpload() throws Exception {
        String cookie = client.guest();
        client.send("POST", "/api/consent", cookie, "{\"keepRecordings\":false,\"over13\":true,\"policyVersion\":\"2026-11-v1\"}");
        var r = put(cookie, "audio/webm", WEBM);
        assertThat(r.statusCode()).isEqualTo(422);
        assertThat(new String(r.body())).contains("no-consent");
    }

    @Test
    void somethingThatIsntAClipIsRefused() throws Exception {
        String cookie = client.consentedGuest(5);
        assertThat(put(cookie, "audio/webm", "hello".getBytes()).statusCode()).isEqualTo(422);
        assertThat(put(cookie, "audio/webm", new byte[3_000_000]).statusCode()).isIn(413, 422);
    }

    @Test
    void anotherLearnerCantReadIt() throws Exception {
        String cookie = client.consentedGuest(5);
        put(cookie, "audio/webm", WEBM);
        assertThat(get(client.consentedGuest(5)).statusCode()).isEqualTo(404);
    }
}
