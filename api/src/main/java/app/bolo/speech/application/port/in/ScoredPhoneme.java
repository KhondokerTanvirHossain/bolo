package app.bolo.speech.application.port.in;

/** One sound inside a word: its IPA symbol and how close it was to the target (0-100). */
public record ScoredPhoneme(String symbol, double accuracy) {
}
