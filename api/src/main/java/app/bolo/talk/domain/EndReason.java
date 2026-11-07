package app.bolo.talk.domain;

import java.util.Locale;

public enum EndReason {
    TIME_UP, LEARNER_ENDED, DISCONNECTED, VOICE_UNAVAILABLE, CRISIS;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
