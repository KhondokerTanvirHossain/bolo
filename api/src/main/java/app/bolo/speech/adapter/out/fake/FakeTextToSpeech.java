package app.bolo.speech.adapter.out.fake;

import app.bolo.speech.application.port.out.TextToSpeechPort;

import java.util.function.Consumer;

/** A quiet 24 kHz hum, 1200 bytes (25 ms) per character, in 4800-byte chunks. Can be told to fail. */
public class FakeTextToSpeech implements TextToSpeechPort {

    private volatile RuntimeException failWith;

    @Override
    public String vendor() {
        return "fake";
    }

    public void failWith(RuntimeException e) {
        this.failWith = e;
    }

    @Override
    public void synthesize(String text, Consumer<byte[]> pcm24kChunk) {
        if (failWith != null) throw failWith;
        int total = text.length() * 1200;
        for (int off = 0; off < total; off += 4800) {
            byte[] chunk = new byte[Math.min(4800, total - off)];
            for (int i = 0; i + 1 < chunk.length; i += 2) chunk[i] = (byte) ((i / 2) % 40 < 20 ? 40 : -40);
            pcm24kChunk.accept(chunk);
        }
    }
}
