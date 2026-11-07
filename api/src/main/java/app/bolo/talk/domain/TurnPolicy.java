package app.bolo.talk.domain;

import java.util.Set;

/**
 * TLK-4 thresholds, all from config (bolo.talk.*): speculate a reply after {@code speculateAfterMs} of silence, end
 * the turn after {@code endAfterMs}, or {@code unfinishedEndAfterMs} when the last word suggests more is coming.
 */
public record TurnPolicy(long speculateAfterMs, long endAfterMs, long unfinishedEndAfterMs, Set<String> unfinishedWords) {
}
