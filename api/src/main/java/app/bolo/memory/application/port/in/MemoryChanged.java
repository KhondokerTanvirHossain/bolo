package app.bolo.memory.application.port.in;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;

/** Bolo's notes were rewritten after {@code talk} (reminders write their next line from the new follow-up). */
public record MemoryChanged(LearnerId learner, TalkSessionId talk) {
}
