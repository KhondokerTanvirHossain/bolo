package app.bolo.talk.application.port.out;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;
import app.bolo.talk.domain.EndReason;
import app.bolo.talk.domain.TalkSession;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TalkSessionPort {

    /**
     * Inserts the talk unless {@code cap} talks still hold a slot; atomic. A live talk holds a slot if it attached a
     * socket and started after {@code liveSince}, or never attached and started after {@code unattachedSince}.
     */
    boolean insertIfBelowCap(TalkSession session, int cap, Instant liveSince, Instant unattachedSince);

    /** Marks the talk attached to a socket; false if it's not this learner's, ended, already attached or too old. */
    boolean attach(TalkSessionId id, LearnerId learner, Instant unattachedSince, Instant at);

    void endOpenFor(LearnerId learner, EndReason reason, Instant at);

    void endAllOpen(EndReason reason, Instant at);

    /** Ended talks whose audio is due for deletion (NFR-14), not yet purged. */
    List<TalkSessionId> audioDue(Instant noConsentEndedBefore, Instant anyEndedBefore);

    void markAudioPurged(TalkSessionId id, Instant at);

    Optional<TalkSession> findLive(TalkSessionId id, LearnerId learner);

    void end(TalkSessionId id, EndReason reason, Instant at);
}
