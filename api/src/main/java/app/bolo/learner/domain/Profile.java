package app.bolo.learner.domain;

/** The learner's setup answers. Talk time is 5 or 10 minutes of talking (ONB-3). */
public record Profile(Goal goal, FearedMoment fearedMoment, int talkMinutes) {

    public Profile {
        if (talkMinutes != 5 && talkMinutes != 10) throw new IllegalArgumentException("talkMinutes must be 5 or 10");
    }
}
