package app.bolo.speech.adapter.out.azure;

import app.bolo.speech.application.port.in.HeardBangla;
import app.bolo.speech.application.port.out.BanglaSpeechPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Azure's REST short-audio recognition in Bangla (bn-IN; Azure has no bn-BD speech-to-text). Spike 2026-11-18: Bangla and
 * mixed Bangla-English clips come back in Bangla script at 0.67-0.87 confidence.
 */
public class AzureBanglaSpeech implements BanglaSpeechPort {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PATH = "/speech/recognition/conversation/cognitiveservices/v1?language=bn-IN&format=detailed";

    private final String key;
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    /** baseUrl: https://{region}.stt.speech.microsoft.com. */
    public AzureBanglaSpeech(String key, String baseUrl) {
        this.key = key;
        this.baseUrl = baseUrl;
    }

    @Override
    public Optional<HeardBangla> recognise(byte[] wav16k, long timeoutMs) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + PATH))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Ocp-Apim-Subscription-Key", key)
                .header("Content-Type", "audio/wav; codecs=audio/pcm; samplerate=16000")
                .POST(HttpRequest.BodyPublishers.ofByteArray(wav16k))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            throw new IllegalStateException("azure bangla unreachable: " + e.getClass().getSimpleName());
        }
        if (response.statusCode() != 200) throw new IllegalStateException("azure bangla: HTTP " + response.statusCode());
        return parse(response.body());
    }

    static Optional<HeardBangla> parse(String body) {
        JsonNode root = JSON.readTree(body);
        if (!"Success".equals(root.path("RecognitionStatus").asString(""))) return Optional.empty();
        JsonNode best = root.path("NBest").path(0);
        String text = best.path("Display").asString(root.path("DisplayText").asString("")).strip();
        if (text.isEmpty()) return Optional.empty();
        return Optional.of(new HeardBangla(text, best.path("Confidence").asDouble(0)));
    }
}
