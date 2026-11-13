package app.bolo.talk.domain;

import java.util.List;
import java.util.Optional;

/**
 * AX-7 (changed 2026-11-14): the automatic Time to answer. The median of the learner's recent start delays (how long
 * they were quiet before starting an answer) picks the speed: under 3 s quick, up to 6 s normal, else relaxed.
 */
public final class AnswerSpeed {

    public static final int RECENT = 30;
    static final int AT_LEAST = 5;

    private AnswerSpeed() {
    }

    /** Empty with fewer than 5 answers: nothing changes. */
    public static Optional<String> pick(List<Integer> startMs) {
        if (startMs.size() < AT_LEAST) return Optional.empty();
        List<Integer> sorted = startMs.stream().sorted().toList();
        int n = sorted.size();
        double median = n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
        return Optional.of(median < 3_000 ? "quick" : median <= 6_000 ? "normal" : "relaxed");
    }
}
