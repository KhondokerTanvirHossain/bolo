package app.bolo.learner.application.port.in;

import app.bolo.common.id.LearnerId;

/** What other modules and the web adapter may know about a learner. {@code consent}/{@code profile} are null until given. */
/** {@code settings} is never null: the defaults until the learner changes them. */
public record LearnerView(LearnerId learnerId, String kind, ConsentView consent, ProfileView profile, SettingsView settings) {

    public record ConsentView(boolean keepRecordings, String policyVersion) {
    }

    /** Stable keys, e.g. goal "job_interviews"; talkMinutes is 5 or 10. */
    /** buddySpeed: "normal" | "slower" (SET-1); captions on or off (SET-2). */
    public record SettingsView(String buddySpeed, boolean captions) {
    }

    public record ProfileView(String goal, String fearedMoment, int talkMinutes) {
    }
}
