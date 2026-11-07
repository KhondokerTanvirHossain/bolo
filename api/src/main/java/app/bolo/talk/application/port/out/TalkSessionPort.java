package app.bolo.talk.application.port.out;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;
import app.bolo.talk.domain.EndReason;
import app.bolo.talk.domain.TalkSession;

import java.time.Instant;
import java.util.Optional;

public interface TalkSessionPort {

    /** Inserts the talk unless {@code cap} talks started after {@code liveSince} are still live; atomic. */
    boolean insertIfBelowCap(TalkSession session, int cap, Instant liveSince);

    Optional<TalkSession> findLive(TalkSessionId id, LearnerId learner);

    void end(TalkSessionId id, EndReason reason, Instant at);
}
