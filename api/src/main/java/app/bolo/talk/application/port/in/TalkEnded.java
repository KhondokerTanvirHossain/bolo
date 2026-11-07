package app.bolo.talk.application.port.in;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;

/** Published once when a talk ends; the Coach listens. {@code reason} is the EndReason's db value. */
public record TalkEnded(TalkSessionId id, LearnerId learner, int learnerTurns, String reason) {
}
