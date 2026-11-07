package app.bolo.common.web;

/** The request attribute holding the signed-in learner's {@code LearnerId}, set by the session-cookie filter. */
public final class CurrentLearner {

    public static final String ATTRIBUTE = "bolo.learner";

    private CurrentLearner() {
    }
}
