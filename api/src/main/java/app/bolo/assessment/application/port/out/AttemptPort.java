package app.bolo.assessment.application.port.out;

import app.bolo.common.id.LearnerId;

import java.time.Instant;

public interface AttemptPort {

    java.util.List<java.time.Instant> attemptTimes(app.bolo.common.id.LearnerId learner);

    void save(LearnerId learner, String reference, String level, boolean heard, double accuracy, String focusJson, Instant at);

    /** A guest joined an account (LearnerMerged): its rows become the account's. */
    void moveLearner(LearnerId from, LearnerId to);
}
