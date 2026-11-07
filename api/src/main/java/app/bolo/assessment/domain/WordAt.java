package app.bolo.assessment.domain;

/** Where a sound issue happened: the learner turn, the word and its stream times, and the sound's score. */
public record WordAt(int turnIdx, String word, long startMs, long endMs, double soundScore) {
}
