package app.bolo.speech.adapter.out.fake;

import app.bolo.speech.application.port.in.Voice;
import app.bolo.speech.application.port.out.TextToSpeechPort;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import java.util.function.Consumer;

/** A quiet 24 kHz hum, 1200 bytes (25 ms) per character, in 4800-byte chunks. Can be told to fail. */
public class FakeTextToSpeech implements TextToSpeechPort {

    private volatile RuntimeException failWith;
    private final List<Voice> voices = new CopyOnWriteArrayList<>();

    /** Every voice asked for, in order (tests). */
    public List<Voice> voices() {
        return voices;
    }

    @Override
    public void synthesize(Voice voice, String text, java.util.function.Consumer<byte[]> pcm24kChunk) {
        voices.add(voice);
        synthesize(text, pcm24kChunk);
    }

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
