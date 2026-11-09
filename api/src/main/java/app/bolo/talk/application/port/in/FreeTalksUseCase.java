package app.bolo.talk.application.port.in;

import app.bolo.common.id.LearnerId;

import java.time.LocalDate;
import java.util.Optional;

/** The free plan's allowance (ACC-5, US-8.7) for Home and Me; empty for guests and unlimited learners. */
public interface FreeTalksUseCase {

    /** window: "first_week" or "month"; resetsOn: the Bangladesh date talks come back (null before the first talk). */
    record FreeTalks(String window, int left, int total, LocalDate resetsOn) {
    }

    Optional<FreeTalks> allowance(LearnerId learner);
}
