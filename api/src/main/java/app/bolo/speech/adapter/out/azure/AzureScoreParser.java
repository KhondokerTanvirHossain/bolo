package app.bolo.speech.adapter.out.azure;

import app.bolo.speech.application.port.in.ScoredPhoneme;
import app.bolo.speech.application.port.in.ScoredSegment;
import app.bolo.speech.application.port.in.ScoredWord;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads Azure's detailed recognition JSON with pronunciation assessment. Scores sit flat on each word and phoneme in
 * the REST response and under "PronunciationAssessment" in the SDK's; both are accepted. Times are 100 ns ticks.
 */
public final class AzureScoreParser {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final long TICKS_PER_MS = 10_000;

    private AzureScoreParser() {
    }

    public static Optional<ScoredSegment> parse(String json) {
        try {
            JsonNode best = JSON.readTree(json).path("NBest").path(0);
            if (best.isMissingNode() || !best.path("Words").isArray()) return Optional.empty();
            List<ScoredWord> words = new ArrayList<>();
            for (JsonNode w : best.path("Words")) {
                List<ScoredPhoneme> phonemes = new ArrayList<>();
                for (JsonNode p : w.path("Phonemes")) phonemes.add(new ScoredPhoneme(p.path("Phoneme").asString(""), score(p)));
                long start = w.path("Offset").asLong(0) / TICKS_PER_MS;
                long end = start + w.path("Duration").asLong(0) / TICKS_PER_MS;
                words.add(new ScoredWord(w.path("Word").asString(""), start, end, score(w), errorType(w), List.copyOf(phonemes)));
            }
            String text = best.path("Display").asString(json.isEmpty() ? "" : JSON.readTree(json).path("DisplayText").asString(""));
            return Optional.of(new ScoredSegment(text, List.copyOf(words)));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static double score(JsonNode n) {
        JsonNode nested = n.path("PronunciationAssessment").path("AccuracyScore");
        return nested.isMissingNode() ? n.path("AccuracyScore").asDouble(0) : nested.asDouble(0);
    }

    private static String errorType(JsonNode n) {
        JsonNode nested = n.path("PronunciationAssessment").path("ErrorType");
        return (nested.isMissingNode() ? n.path("ErrorType") : nested).asString("None");
    }
}
