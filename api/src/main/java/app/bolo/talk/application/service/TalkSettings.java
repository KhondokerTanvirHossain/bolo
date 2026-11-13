package app.bolo.talk.application.service;

import app.bolo.talk.domain.Pace;
import app.bolo.talk.domain.TurnPolicy;

/**
 * bolo.talk.* and bolo.llm.*: turn thresholds, the Buddy model, the length of a "minute" (tests shorten it), and how
 * long past the talk time a learner may keep the floor before Bolo says goodbye anyway. {@code policy} and the quiet
 * values are the normal speed; {@code quick}, {@code relaxed} and the stammer floor are the other paces (AX-7, AX-8).
 */
record TalkSettings(TurnPolicy policy, String model, int maxTokens, long minuteMs, long wrapGraceMs, long replyTimeoutMs,
                    long noAudioTimeoutMs, long burstAllowanceMs, long quietEncourageMs, long quietHintMs, long quietPauseMs,
                    long pausedMaxMs, Pace quick, Pace relaxed, Pace stammerFloor) {

    /** The spec's quick, relaxed and stammer values (2026-11-14), with the unfinished words of {@code policy}. */
    TalkSettings(TurnPolicy policy, String model, int maxTokens, long minuteMs, long wrapGraceMs, long replyTimeoutMs,
                 long noAudioTimeoutMs, long burstAllowanceMs, long quietEncourageMs, long quietHintMs, long quietPauseMs,
                 long pausedMaxMs) {
        this(policy, model, maxTokens, minuteMs, wrapGraceMs, replyTimeoutMs, noAudioTimeoutMs, burstAllowanceMs, quietEncourageMs,
                quietHintMs, quietPauseMs, pausedMaxMs,
                new Pace(5_000, 10_000, 30_000, new TurnPolicy(700, 1_500, 3_000, policy.unfinishedWords())),
                new Pace(30_000, 45_000, 120_000, new TurnPolicy(1_500, 3_500, 5_500, policy.unfinishedWords())),
                new Pace(16_000, 30_000, 90_000, new TurnPolicy(1_500, 4_500, 6_500, policy.unfinishedWords())));
    }

    Pace normal() {
        return new Pace(quietEncourageMs, quietHintMs, quietPauseMs, policy);
    }

    /** The pace for a talk: "quick", "relaxed", else normal; with stammer, every value at least the floor. */
    Pace pace(String speed, boolean stammer) {
        Pace p = "quick".equals(speed) ? quick : "relaxed".equals(speed) ? relaxed : normal();
        return stammer ? p.atLeast(stammerFloor) : p;
    }
}
