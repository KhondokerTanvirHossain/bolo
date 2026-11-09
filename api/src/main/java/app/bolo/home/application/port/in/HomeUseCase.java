package app.bolo.home.application.port.in;

import app.bolo.common.id.LearnerId;

public interface HomeUseCase {

    /** card is null when Home shows only the Talk button; freeTalks is null for guests and unlimited learners (US-8.7). */
    /** lastTopic: the last ready talk's topic for Bolo's line (null when none); week: the gentle run (P1f). */
    record HomeView(String primary, Card card, app.bolo.talk.application.port.in.FreeTalksUseCase.FreeTalks freeTalks,
                    String lastTopic, app.bolo.home.domain.Streak.Week week) {
    }

    record Card(String kind, String talkId, String pattern, String title) {
    }

    HomeView home(LearnerId learner);
}
