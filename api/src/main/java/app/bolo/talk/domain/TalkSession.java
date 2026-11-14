package app.bolo.talk.domain;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;

import java.time.Instant;

/** A talk with Bolo. Live while {@code endedAt} is null. SCN-1: {@code scenario} "free" | "standup"; {@code difficulty} null for free. */
public record TalkSession(TalkSessionId id, LearnerId learnerId, int minutes, Instant startedAt, Instant endedAt, String scenario,
                          String difficulty) {

    public TalkSession(TalkSessionId id, LearnerId learnerId, int minutes, Instant startedAt, Instant endedAt) {
        this(id, learnerId, minutes, startedAt, endedAt, "free", null);
    }

    public TalkSession {
        if (minutes != 5 && minutes != 10) throw new IllegalArgumentException("minutes must be 5 or 10");
    }
}
