package app.bolo.learner.application.port.in;

import app.bolo.common.id.LearnerId;

public interface DeleteLearnerUseCase {

    /** Deletes the learner and all their data at once. Refuses with "confirm" unless {@code confirm} is exactly "DELETE". */
    void deleteEverything(LearnerId learner, String confirm);
}
