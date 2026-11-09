package app.bolo.coaching.application.port.in;

import app.bolo.common.id.LearnerId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** For Home (P1f): when the learner completed loops (heard ready feedback), and the last ready talk's topic. */
public interface LoopDaysUseCase {

    List<Instant> loopDays(LearnerId learner);

    Optional<String> lastTopic(LearnerId learner);
}
