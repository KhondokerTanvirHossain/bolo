package app.bolo.learner.application.port.in;

import app.bolo.common.id.LearnerId;

/** What other modules and the web adapter may know about a learner. {@code consent} is null until given. */
public record LearnerView(LearnerId learnerId, String kind, ConsentView consent) {

    public record ConsentView(boolean keepRecordings, String policyVersion) {
    }
}
