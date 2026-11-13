package app.bolo.speech.application.port.in;

import app.bolo.common.id.LearnerId;

/** P3c (SET-5, 2026-11-13): "Hear it": a short sample of one of Bolo's voices, as a WAV. */
public interface VoiceSampleUseCase {

    /**
     * The sample for {@code voiceKey} ("american-male"): made once per voice, then served from memory. Refuses an unknown
     * key with "voice" and more than 20 a minute per learner with "slow-down".
     */
    byte[] sample(LearnerId learner, String voiceKey);
}
