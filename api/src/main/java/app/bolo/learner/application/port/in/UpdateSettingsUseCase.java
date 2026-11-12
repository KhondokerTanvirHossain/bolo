package app.bolo.learner.application.port.in;

import app.bolo.common.id.LearnerId;

public interface UpdateSettingsUseCase {

    /**
     * Saves Bolo's speed ("normal" | "slower"), captions and the talk view ("chat" | "classic"; null keeps the current
     * one). Refuses with "buddy-speed" or "talk-view". Applies from the next talk.
     */
    void update(LearnerId learner, String buddySpeed, boolean captions, String talkView);
}
