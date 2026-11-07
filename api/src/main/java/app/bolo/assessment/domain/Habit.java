package app.bolo.assessment.domain;

import java.util.Map;
import java.util.Optional;

/** DBF-3 habit fixes: a filler used 5+ times, or answers that average under 8 words. */
public record Habit(Kind kind, String word, int count, double averageWords) {

    public enum Kind { FILLER, SHORT_ANSWERS }

    static final int FILLER_AT_LEAST = 5;
    static final double SHORT_UNDER_WORDS = 8;

    public static Optional<Habit> of(FluencyStats stats) {
        var top = stats.fillers().entrySet().stream().max(Map.Entry.comparingByValue());
        if (top.isPresent() && top.get().getValue() >= FILLER_AT_LEAST) {
            return Optional.of(new Habit(Kind.FILLER, top.get().getKey(), top.get().getValue(), stats.averageWordsPerTurn()));
        }
        if (stats.averageWordsPerTurn() > 0 && stats.averageWordsPerTurn() < SHORT_UNDER_WORDS) {
            return Optional.of(new Habit(Kind.SHORT_ANSWERS, null, 0, stats.averageWordsPerTurn()));
        }
        return Optional.empty();
    }
}
