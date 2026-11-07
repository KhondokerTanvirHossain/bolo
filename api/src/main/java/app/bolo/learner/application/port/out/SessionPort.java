package app.bolo.learner.application.port.out;

import app.bolo.common.id.LearnerId;

import java.time.Instant;
import java.util.Optional;

public interface SessionPort {

    void create(byte[] tokenHash, LearnerId learner, Instant now);

    /** Returns the learner if the session was seen after {@code notBefore}, and marks it seen at {@code now}. */
    Optional<LearnerId> touch(byte[] tokenHash, Instant notBefore, Instant now);
}
