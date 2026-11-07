package app.bolo.coaching.application.port.out;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;

import java.time.Instant;
import java.util.Optional;

public interface DebriefPort {

    record Stored(LearnerId learner, String status, String body) {
    }

    void start(TalkSessionId talk, Instant at);

    void save(TalkSessionId talk, String status, String body, int attempts, Instant at);

    Optional<Stored> find(TalkSessionId talk);

    /** Ready debriefs this learner already had, other than this talk's. */
    int readyCountFor(LearnerId learner, TalkSessionId except);

    void logDispute(TalkSessionId talk, int fixIndex, String kind, Instant at);

    /** After a restart nothing is scoring any more. */
    void failAllScoring(Instant at);
}
