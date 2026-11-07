package app.bolo.speech.application.port.in;

import app.bolo.common.id.TalkContext;

public interface StreamTranscriptionUseCase {

    TranscriptionStream open(TalkContext ctx, TranscriptListener listener);
}
