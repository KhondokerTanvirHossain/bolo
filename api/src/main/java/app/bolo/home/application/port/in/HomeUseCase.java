package app.bolo.home.application.port.in;

import app.bolo.common.id.LearnerId;

public interface HomeUseCase {

    /** card is null when Home shows only the Talk button; freeTalks is null for guests and unlimited learners (US-8.7). */
    record HomeView(String primary, Card card, app.bolo.talk.application.port.in.FreeTalksUseCase.FreeTalks freeTalks) {
    }

    record Card(String kind, String talkId, String pattern, String title) {
    }

    HomeView home(LearnerId learner);
}
