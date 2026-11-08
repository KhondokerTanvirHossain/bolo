package app.bolo.learner.application.port.in;

import app.bolo.common.id.LearnerId;

public interface UpdateSettingsUseCase {

    /** Saves Bolo's speed ("normal" | "slower") and captions. Refuses with "buddy-speed". Applies from the next talk. */
    void update(LearnerId learner, String buddySpeed, boolean captions);
}
