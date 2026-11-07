package app.bolo.learner.domain;

import java.util.Optional;

/** "What do you need English for most?" (US-1.4). */
public enum Goal {
    WORK_MEETINGS("work_meetings"), JOB_INTERVIEWS("job_interviews"),
    STUDY_ABROAD("study_abroad"), DAILY_CONFIDENCE("daily_confidence");

    private final String key;

    Goal(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<Goal> fromKey(String key) {
        for (Goal g : values()) if (g.key.equals(key)) return Optional.of(g);
        return Optional.empty();
    }
}
