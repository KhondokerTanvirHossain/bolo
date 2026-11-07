package app.bolo.talk.application.port.in;

import app.bolo.common.id.LearnerId;

public interface TalkRelayUseCase {

    /** Starts the conversation on a socket that has passed the handshake; Bolo speaks first. */
    TalkConnection open(TalkView talk, LearnerId learner, TalkOutput out);
}
