package app.bolo.assessment.domain;

import java.util.List;

/** A word the learner said, with its time in the turn's stream and its pronunciation scores. */
public record SpokenWord(String word, long startMs, long endMs, double accuracy, String errorType, List<SpokenSound> phonemes) {
}
