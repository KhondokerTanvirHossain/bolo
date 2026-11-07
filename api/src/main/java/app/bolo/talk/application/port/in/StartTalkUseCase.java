package app.bolo.talk.application.port.in;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;
import app.bolo.talk.domain.EndReason;

import java.util.Optional;

public interface StartTalkUseCase {

    /** Refused with "no-consent" without consent to the current policy, "busy" when every live slot is taken. */
    TalkView start(LearnerId learner);

    /** The learner's own talk, only while it is live. */
    Optional<TalkView> openFor(LearnerId learner, TalkSessionId id);

    /** Ends a live talk and frees its slot; does nothing if it already ended. */
    void end(TalkSessionId id, EndReason reason);
}
