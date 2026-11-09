package app.bolo;

import app.bolo.talk.TalkTestClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Review (P1d): with sign-up open, a guest never gets free voice time, not even when sign-in is switched off (Firebase
 * config missing). Only bolo.guests.may-talk (tests, local development) or an unlimited learner changes that.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"bolo.guests.may-talk=false",
        "bolo.speech.vendor=fake", "bolo.llm.vendor=fake", "bolo.talk.max-live-sessions=50"})
class GuestsNeedSignInAcceptanceTest extends PostgresTestBase {

    @LocalServerPort int port;

    @Test
    void withSignInOffAGuestStillCannotTalk() throws Exception {
        TalkTestClient client = new TalkTestClient(port);
        var r = client.startTalk(client.consentedGuest(5));
        assertThat(r.status()).isEqualTo(422);
        assertThat(r.body()).contains("sign-in");
    }

    @Test
    void aGuestCannotPractise() throws Exception {
        String cookie = new TalkTestClient(port).consentedGuest(5);
        String boundary = "bolo" + System.nanoTime();
        byte[] body = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"reference\"\r\n\r\nHello.\r\n--" + boundary
                + "\r\nContent-Disposition: form-data; name=\"audio\"; filename=\"a.wav\"\r\nContent-Type: audio/wav\r\n\r\nRIFF\r\n--"
                + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> r = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/attempts"))
                .header("Origin", TalkTestClient.ORIGIN).header("X-Bolo", "1").header("Cookie", cookie)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(422);
        assertThat(r.body()).contains("sign-in");
    }
}
