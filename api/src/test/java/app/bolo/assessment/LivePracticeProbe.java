package app.bolo.assessment;

import app.bolo.WavFixture;
import app.bolo.common.audio.Wav;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Practice against the deployed Bolo with real Azure: "ischool / istation" is caught, a clean sentence is nailed. */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "BOLO_LIVE_URL", matches = "https://.+")
class LivePracticeProbe {

    static final String URL = System.getenv("BOLO_LIVE_URL");
    final HttpClient http = HttpClient.newHttpClient();

    @Test
    void theExtraVowelIsCaughtAndACleanSentenceIsNailed() throws Exception {
        String cookie = send("/api/session", null, "{}").headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
        send("/api/consent", cookie, "{\"keepRecordings\":false,\"over13\":true,\"policyVersion\":\"2026-11-v1\"}");

        long t0 = System.currentTimeMillis();
        HttpResponse<String> ischool = attempt(cookie, "/audio/practice-ischool-16k.wav", "I study at a small school near the station.");
        long ms = System.currentTimeMillis() - t0;
        System.out.println("ischool (" + ms + " ms): " + ischool.body());
        assertThat(ischool.statusCode()).isEqualTo(200);
        List<String> kinds = JsonPath.read(ischool.body(), "$.focus[*].kind");
        List<String> words = JsonPath.read(ischool.body(), "$.focus[*].word");
        assertThat(kinds).contains("EXTRA_VOWEL");
        assertThat(words).contains("school");

        HttpResponse<String> clean = attempt(cookie, "/audio/practice-very-16k.wav", "It is very important to visit the village.");
        System.out.println("clean: " + clean.body());
        assertThat((String) JsonPath.read(clean.body(), "$.level")).isEqualTo("NAILED");
    }

    HttpResponse<String> attempt(String cookie, String fixture, String reference) throws Exception {
        byte[] wav = Wav.wrap(WavFixture.pcm(fixture), 16000);
        String b = "bolo" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(("--" + b + "\r\nContent-Disposition: form-data; name=\"reference\"\r\n\r\n" + reference + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(("--" + b + "\r\nContent-Disposition: form-data; name=\"audio\"; filename=\"a.wav\"\r\nContent-Type: audio/wav\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.writeBytes(wav);
        body.writeBytes(("\r\n--" + b + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return http.send(HttpRequest.newBuilder(URI.create(URL + "/api/attempts")).header("Origin", URL).header("X-Bolo", "1")
                .header("Cookie", cookie).header("Content-Type", "multipart/form-data; boundary=" + b)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> send(String path, String cookie, String json) throws Exception {
        HttpRequest.Builder r = HttpRequest.newBuilder(URI.create(URL + path)).header("Origin", URL).header("X-Bolo", "1")
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json));
        if (cookie != null) r.header("Cookie", cookie);
        return http.send(r.build(), HttpResponse.BodyHandlers.ofString());
    }
}
