package app.bolo.learner.application.port.in;

import app.bolo.common.id.LearnerId;

import java.util.Optional;

public interface CurrentLearnerUseCase {

    /** The learner a session token belongs to; empty for unknown or expired (90 days idle, NFR-17) tokens. */
    Optional<LearnerId> byToken(String token);

    LearnerView view(LearnerId id);
}
