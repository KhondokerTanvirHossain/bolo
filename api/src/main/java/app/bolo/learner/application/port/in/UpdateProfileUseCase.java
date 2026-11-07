package app.bolo.learner.application.port.in;

import app.bolo.common.id.LearnerId;

public interface UpdateProfileUseCase {

    /** Stores the setup answers by their stable keys. Refuses with "goal", "feared-moment" or "talk-minutes". */
    void update(LearnerId learner, String goal, String fearedMoment, int talkMinutes);
}
