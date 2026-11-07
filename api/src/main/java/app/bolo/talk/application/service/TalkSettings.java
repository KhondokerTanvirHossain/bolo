package app.bolo.talk.application.service;

import app.bolo.talk.domain.TurnPolicy;

/**
 * bolo.talk.* and bolo.llm.*: turn thresholds, the Buddy model, the length of a "minute" (tests shorten it), and how
 * long past the talk time a learner may keep the floor before Bolo says goodbye anyway.
 */
record TalkSettings(TurnPolicy policy, String model, int maxTokens, long minuteMs, long wrapGraceMs, long replyTimeoutMs,
                    long noAudioTimeoutMs, long burstAllowanceMs) {
}
