package app.bolo.talk.application.port.in;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;

/** SCN-2: a Realistic standup cut in on the learner's turn (analytics: standup_interrupted, for tuning). */
public record StandupInterrupted(TalkSessionId id, LearnerId learner, int learnerTurn) {
}
