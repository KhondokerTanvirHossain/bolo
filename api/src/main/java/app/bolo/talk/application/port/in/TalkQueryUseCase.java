package app.bolo.talk.application.port.in;

import app.bolo.common.id.TalkSessionId;

import java.util.Optional;

public interface TalkQueryUseCase {

    Optional<TalkRecord> record(TalkSessionId id);

    /** Whether the learner started any talk at or after {@code since} (daily reminders: "talked today"). */
    boolean startedSince(app.bolo.common.id.LearnerId learner, java.time.Instant since);
}
