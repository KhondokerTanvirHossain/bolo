package app.bolo.coaching.application.port.out;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;

import java.time.Instant;

public interface RatingPort {

    boolean exists(TalkSessionId talk);

    /** Inserts or updates; a null score keeps the saved one; a null note keeps the saved note (review I2). */
    void upsert(TalkSessionId talk, LearnerId learner, Integer score, String note, Instant at);

    /** A guest's ratings move to the account they joined (US-8.1, US-8.4). */
    void move(LearnerId from, LearnerId to);
}
