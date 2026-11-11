package app.bolo.learner.application.port.in;

import app.bolo.common.id.LearnerId;

/** What the admin portal may change about a learner (P2a). */
public interface LearnerAdminUseCase {

    /** Outside the free plan (no counter, no limit) or back on it. */
    void setUnlimited(LearnerId learner, boolean on);
}
