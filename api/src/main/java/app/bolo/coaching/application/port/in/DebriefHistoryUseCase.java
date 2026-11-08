package app.bolo.coaching.application.port.in;

import app.bolo.common.id.LearnerId;

import java.time.Instant;
import java.util.List;

/** A learner's past talks with their debriefs, oldest first (Progress and History, PRG-1/3/8). */
public interface DebriefHistoryUseCase {

    /** status: scoring | ready | too_short | failed; body is the debrief JSON (null while scoring). */
    record PastDebrief(String talkId, Instant startedAt, int minutes, String status, String body) {
    }

    List<PastDebrief> list(LearnerId learner);
}
