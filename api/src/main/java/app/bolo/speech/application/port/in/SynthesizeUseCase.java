package app.bolo.speech.application.port.in;

import app.bolo.common.id.TalkContext;

import java.util.function.Consumer;

public interface SynthesizeUseCase {

    /** Speaks {@code text} in Bolo's voice as PCM s16le, mono, 24 kHz chunks; blocks until done. */
    default void synthesize(TalkContext ctx, String text, Consumer<byte[]> pcm24kChunk) {
        synthesize(ctx, Voice.BUDDY, text, pcm24kChunk);
    }

    default void synthesize(TalkContext ctx, Voice voice, String text, Consumer<byte[]> pcm24kChunk) {
        synthesize(ctx, voice, null, text, pcm24kChunk);
    }

    /**
     * P3c (SET-5): in the learner's chosen voice. {@code buddyVoice} is a key such as "american-male" (accent-gender),
     * null for the default; the Coach always keeps its own voice.
     */
    void synthesize(TalkContext ctx, Voice voice, String buddyVoice, String text, Consumer<byte[]> pcm24kChunk);
}
