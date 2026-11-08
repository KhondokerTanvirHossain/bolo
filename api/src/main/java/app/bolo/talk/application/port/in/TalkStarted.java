package app.bolo.talk.application.port.in;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;

/** Published when a talk's socket attaches and Bolo starts (analytics: session_started). */
public record TalkStarted(TalkSessionId id, LearnerId learner) {
}
