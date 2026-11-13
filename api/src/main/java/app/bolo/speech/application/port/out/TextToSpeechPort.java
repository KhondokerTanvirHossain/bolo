package app.bolo.speech.application.port.out;

import app.bolo.speech.application.port.in.Voice;

import java.util.function.Consumer;

public interface TextToSpeechPort {

    String vendor();

    void synthesize(String text, Consumer<byte[]> pcm24kChunk);

    default void synthesize(Voice voice, String text, Consumer<byte[]> pcm24kChunk) {
        synthesize(text, pcm24kChunk);
    }

    /** P3c (SET-5): {@code buddyVoice} is the learner's voice key ("american-male"), null for the default; ignored for the Coach. */
    default void synthesize(Voice voice, String buddyVoice, String text, Consumer<byte[]> pcm24kChunk) {
        synthesize(voice, text, pcm24kChunk);
    }
}
