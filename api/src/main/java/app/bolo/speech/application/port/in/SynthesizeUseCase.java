package app.bolo.speech.application.port.in;

import app.bolo.common.id.TalkContext;

import java.util.function.Consumer;

public interface SynthesizeUseCase {

    /** Speaks {@code text} in Bolo's voice as PCM s16le, mono, 24 kHz chunks; blocks until done. */
    void synthesize(TalkContext ctx, String text, Consumer<byte[]> pcm24kChunk);
}
