package app.bolo.speech.application.port.out;

import java.util.function.Consumer;

public interface TextToSpeechPort {

    String vendor();

    void synthesize(String text, Consumer<byte[]> pcm24kChunk);
}
