package app.bolo.coaching.application.port.in;

import app.bolo.common.id.LearnerId;

import java.util.Optional;

/** The learner's most recent talk that has a debrief, for Home (US-6.9). */
public interface LatestDebriefUseCase {

    /**
     * status: scoring | ready | too_short | failed. soundPattern/soundTitle: the first sound fix's pattern key (e.g.
     * "V_BH") and its name ("The v sound"), or null when the debrief has none.
     */
    record Latest(String talkId, String status, boolean viewed, String soundPattern, String soundTitle) {
    }

    Optional<Latest> latestFor(LearnerId learner);
}
