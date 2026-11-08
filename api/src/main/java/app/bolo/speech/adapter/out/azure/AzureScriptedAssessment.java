package app.bolo.speech.adapter.out.azure;

import app.bolo.speech.application.port.in.PronunciationResult;
import app.bolo.speech.application.port.in.SpeechBusyException;
import app.bolo.speech.application.port.in.SpeechUnavailableException;
import app.bolo.speech.application.port.out.ScriptedAssessmentPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Azure's scripted pronunciation assessment over the REST short-audio endpoint (one clip, one request). A known
 * reference turns on miscue detection: inserted sounds ("ischool") and omitted words come back as error types.
 */
public class AzureScriptedAssessment implements ScriptedAssessmentPort {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PATH = "/speech/recognition/conversation/cognitiveservices/v1?language=en-US&format=detailed";

    private final String key;
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final Duration timeout;

    /** baseUrl: https://{region}.stt.speech.microsoft.com. A learner's attempt waits at most 8 s. */
    public AzureScriptedAssessment(String key, String baseUrl) {
        this(key, baseUrl, Duration.ofSeconds(8));
    }

    /** A longer {@code timeout} is for long readings (the accuracy harness sends up to 60 s). */
    public AzureScriptedAssessment(String key, String baseUrl, Duration timeout) {
        this.key = key;
        this.baseUrl = baseUrl;
        this.timeout = timeout;
    }

    @Override
    public String vendor() {
        return "azure";
    }

    @Override
    public PronunciationResult assess(byte[] wav16k, String reference) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("ReferenceText", reference);
        config.put("GradingSystem", "HundredMark");
        config.put("Granularity", "Phoneme");
        config.put("Dimension", "Comprehensive");
        config.put("EnableMiscue", true);
        config.put("PhonemeAlphabet", "IPA");
        String header = Base64.getEncoder().encodeToString(JSON.writeValueAsString(config).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + PATH))
                .timeout(timeout)
                .header("Ocp-Apim-Subscription-Key", key)
                .header("Content-Type", "audio/wav; codecs=audio/pcm; samplerate=16000")
                .header("Pronunciation-Assessment", header)
                .POST(HttpRequest.BodyPublishers.ofByteArray(wav16k))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SpeechUnavailableException("interrupted", e);
        } catch (Exception e) {
            throw new SpeechUnavailableException("azure assessment unreachable", e);
        }
        if (response.statusCode() == 429) throw new SpeechBusyException();
        if (response.statusCode() != 200) throw new SpeechUnavailableException("azure assessment: HTTP " + response.statusCode());
        JsonNode root = JSON.readTree(response.body());
        if (!"Success".equals(root.path("RecognitionStatus").asString(""))) return PronunciationResult.nothingHeard();
        var segment = AzureScoreParser.parse(response.body());
        if (segment.isEmpty()) return PronunciationResult.nothingHeard();
        JsonNode best = root.path("NBest").path(0);
        double accuracy = best.path("PronunciationAssessment").path("AccuracyScore").isMissingNode()
                ? best.path("AccuracyScore").asDouble(0) : best.path("PronunciationAssessment").path("AccuracyScore").asDouble(0);
        return new PronunciationResult(true, accuracy, segment.get().words());
    }
}
