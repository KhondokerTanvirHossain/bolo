package app.bolo.speech.application.port.out;

import app.bolo.speech.application.port.in.Voice;

import java.util.function.Consumer;

public interface TextToSpeechPort {

    String vendor();

    void synthesize(String text, Consumer<byte[]> pcm24kChunk);

    default void synthesize(Voice voice, String text, Consumer<byte[]> pcm24kChunk) {
        synthesize(text, pcm24kChunk);
    }
}
