package app.bolo.learner.application.port.in;

import app.bolo.common.id.LearnerId;

import java.util.Optional;

/**
 * A one-way key for a Google account's free-plan use (P1d task 6): SHA-256 of "bolo-free:" + its Firebase uid, so free
 * talks survive "delete everything" without keeping who it was. Empty for guests.
 */
public interface FreePlanKeyUseCase {

    Optional<String> freePlanKey(LearnerId learner);
}
