package app.bolo.home.application.port.in;

import app.bolo.common.id.LearnerId;

public interface HomeUseCase {

    /** card is null when Home shows only the Talk button. */
    record HomeView(String primary, Card card) {
    }

    record Card(String kind, String talkId, String pattern, String title) {
    }

    HomeView home(LearnerId learner);
}
