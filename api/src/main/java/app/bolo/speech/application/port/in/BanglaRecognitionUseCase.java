package app.bolo.speech.application.port.in;

import app.bolo.common.id.TalkContext;

import java.util.Optional;

/**
 * Founder 2026-11-18: recognises one finished learner turn in Bangla. Never throws: anything slow, failed or too long is
 * empty, and the talk goes on as before.
 */
public interface BanglaRecognitionUseCase {

    /** {@code wav16k}: the turn's audio as a 16 kHz mono WAV; waits at most {@code timeoutMs}. */
    Optional<HeardBangla> recognise(TalkContext ctx, byte[] wav16k, long timeoutMs);
}
