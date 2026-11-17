package app.bolo.speech.application.port.in;

import java.util.List;

/**
 * One finished stretch of recognised speech with word-level pronunciation scores (unscripted assessment).
 * {@code confidence}: the recogniser's 0-1 confidence, or null when it gave none (founder 2026-11-18: a low one is the
 * sign the learner spoke Bangla).
 */
public record ScoredSegment(String text, List<ScoredWord> words, Double confidence) {

    public ScoredSegment(String text, List<ScoredWord> words) {
        this(text, words, null);
    }
}
