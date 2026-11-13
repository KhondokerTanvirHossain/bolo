package app.bolo.learner.application.port.in;

import app.bolo.common.id.LearnerId;

public interface UpdateSettingsUseCase {

    /**
     * Saves Bolo's speed ("normal" | "slower"), captions and the talk view ("chat" | "classic"; null keeps the current
     * one). Refuses with "buddy-speed" or "talk-view". Applies from the next talk.
     * P3c: Bolo's voice (gender, accent) and mood; each null keeps the current one. Refuses with "voice-gender",
     * "voice-accent" or "mood".
     * AX-7, AX-8 (2026-11-14): Time to answer ("auto" | "quick" | "normal" | "relaxed") and I stammer; null keeps the
     * current one. Refuses with "answer-time". Bolo's automatic pick is never set here.
     */
    void update(LearnerId learner, String buddySpeed, boolean captions, String talkView, String voiceGender, String voiceAccent,
                String mood, String answerTime, Boolean stammer);
}
