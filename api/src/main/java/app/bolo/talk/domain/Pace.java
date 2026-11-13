package app.bolo.talk.domain;

/**
 * AX-7 (changed 2026-11-14): how long Bolo waits. Silence help before the learner speaks (encourage, a starter card,
 * then a pause of the talk, TLK-6) and when their turn ends after they stop (TLK-4). One per speed: quick, normal,
 * relaxed. AX-8: "I stammer" raises every value to at least a floor ({@link #atLeast}).
 */
public record Pace(long encourageMs, long hintMs, long pauseMs, TurnPolicy policy) {

    /** Each value the larger of this pace's and the floor's; the unfinished words stay this pace's. */
    public Pace atLeast(Pace floor) {
        TurnPolicy p = policy, f = floor.policy;
        return new Pace(Math.max(encourageMs, floor.encourageMs), Math.max(hintMs, floor.hintMs), Math.max(pauseMs, floor.pauseMs),
                new TurnPolicy(Math.max(p.speculateAfterMs(), f.speculateAfterMs()), Math.max(p.endAfterMs(), f.endAfterMs()),
                        Math.max(p.unfinishedEndAfterMs(), f.unfinishedEndAfterMs()), p.unfinishedWords()));
    }
}
