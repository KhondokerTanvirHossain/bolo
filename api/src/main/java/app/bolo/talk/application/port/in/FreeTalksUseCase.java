package app.bolo.talk.application.port.in;

import app.bolo.common.id.LearnerId;

import java.time.LocalDate;
import java.util.Optional;

/** The free plan's allowance (ACC-5, US-8.7) for Home and Me; empty for guests and unlimited learners. */
public interface FreeTalksUseCase {

    /**
     * plan: free, basic, premium or ultimate; window: "first_week" or "month"; resetsOn: the Bangladesh date talks come
     * back (null before the first talk).
     */
    record FreeTalks(String window, int left, int total, LocalDate resetsOn, String plan) {
    }

    Optional<FreeTalks> allowance(LearnerId learner);

    /**
     * counted: this learner's counted talks; finished: talks that ended after at least 90 s or with feedback (an open or
     * dropped talk isn't finished); returning: the same Google account had talks before (deleted, made again).
     */
    record History(int counted, int finished, boolean returning) {
    }

    History history(LearnerId learner);
}
