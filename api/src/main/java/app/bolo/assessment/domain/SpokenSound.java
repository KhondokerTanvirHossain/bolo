package app.bolo.assessment.domain;

/** One sound of a spoken word (IPA symbol) and how close it came to the target, 0-100. */
public record SpokenSound(String symbol, double accuracy) {
}
