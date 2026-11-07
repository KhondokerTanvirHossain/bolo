package app.bolo.assessment.domain;

import java.util.List;

/** One learner turn as the Coach sees it: what was said, and the scored words (may be empty). */
public record TurnScore(int idx, String text, List<SpokenWord> words) {
}
