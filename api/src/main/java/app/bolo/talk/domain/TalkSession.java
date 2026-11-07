package app.bolo.talk.domain;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;

import java.time.Instant;

/** A talk with Bolo. Live while {@code endedAt} is null. */
public record TalkSession(TalkSessionId id, LearnerId learnerId, int minutes, Instant startedAt, Instant endedAt) {

    public TalkSession {
        if (minutes != 5 && minutes != 10) throw new IllegalArgumentException("minutes must be 5 or 10");
    }
}
