package app.bolo.assessment.application.port.in;

import app.bolo.common.id.LearnerId;

import java.time.Instant;
import java.util.List;

/** For Home's run (P1f): when the learner made practice attempts. */
public interface PracticeDaysUseCase {

    List<Instant> practiceDays(LearnerId learner);
}
