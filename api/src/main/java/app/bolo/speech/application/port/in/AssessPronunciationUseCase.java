package app.bolo.speech.application.port.in;

import app.bolo.common.id.TalkContext;

/** Scripted pronunciation assessment of a short clip (WAV, 16 kHz mono) against the sentence the learner read. */
public interface AssessPronunciationUseCase {

    PronunciationResult assess(TalkContext ctx, byte[] wav16k, String reference);
}
