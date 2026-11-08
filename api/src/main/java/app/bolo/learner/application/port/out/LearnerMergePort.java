package app.bolo.learner.application.port.out;

import app.bolo.common.id.LearnerId;

/** The learner module's own rows on a guest → account merge: the account keeps what it has, gains what it lacks. */
public interface LearnerMergePort {

    /** Consent ledger, profile and settings move only when the account has none of its own. */
    void moveIfAccountHasNone(LearnerId from, LearnerId to);
}
