package app.bolo.learner.domain;

import java.util.Optional;

/** "Which moment feels hardest?" (ONB-3). */
public enum FearedMoment {
    SPEAKING_UP("speaking_up"), INTERVIEWS("interviews"), PHONE_CALLS("phone_calls"), SMALL_TALK("small_talk");

    private final String key;

    FearedMoment(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<FearedMoment> fromKey(String key) {
        for (FearedMoment f : values()) if (f.key.equals(key)) return Optional.of(f);
        return Optional.empty();
    }
}
