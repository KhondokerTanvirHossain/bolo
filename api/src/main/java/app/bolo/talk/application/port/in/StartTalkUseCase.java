package app.bolo.talk.application.port.in;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;
import app.bolo.talk.domain.EndReason;

import java.util.Optional;

public interface StartTalkUseCase {

    /**
     * Refused with "no-consent" without consent to the current policy, "busy" when every live slot is taken. Ends the
     * learner's own leftover talks first, so "Try again" is never blocked by itself.
     */
    default TalkView start(LearnerId learner) {
        return start(learner, null);
    }

    /**
     * SCN-1 (review I4): {@code choice} is what the web showed when Talk was tapped; it wins over a settings save still
     * on its way. Null, or an unknown scenario, uses the learner's saved settings.
     */
    TalkView start(LearnerId learner, StartChoice choice);

    record StartChoice(String scenario, String difficulty) {
    }

    /** The learner's own talk, only while it is live. */
    Optional<TalkView> openFor(LearnerId learner, TalkSessionId id);

    /** Attaches the learner's socket to their live talk, once; empty if that isn't allowed (see openFor). */
    Optional<TalkView> attach(LearnerId learner, TalkSessionId id);

    /** Ends a live talk and frees its slot; does nothing if it already ended. */
    void end(TalkSessionId id, EndReason reason);

    /** Is any talk live right now (attached to a socket)? Practice waits for it (the vendor's single slot). */
    boolean anyLive();

    /** After a restart no talk is running: end every open one so none holds a slot. */
    void endAllOpen();
}
