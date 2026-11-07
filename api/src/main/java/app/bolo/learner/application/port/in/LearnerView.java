package app.bolo.learner.application.port.in;

import app.bolo.common.id.LearnerId;

/** What other modules and the web adapter may know about a learner. {@code consent}/{@code profile} are null until given. */
public record LearnerView(LearnerId learnerId, String kind, ConsentView consent, ProfileView profile) {

    public record ConsentView(boolean keepRecordings, String policyVersion) {
    }

    /** Stable keys, e.g. goal "job_interviews"; talkMinutes is 5 or 10. */
    public record ProfileView(String goal, String fearedMoment, int talkMinutes) {
    }
}
