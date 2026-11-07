package app.bolo.speech.adapter.out.fake;

import app.bolo.speech.application.port.in.TranscriptListener;
import app.bolo.speech.application.port.in.TranscriptionStream;
import app.bolo.speech.application.port.out.SpeechToTextPort;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * For tests and local development without keys: "hears" {@code hello bolo} once an utterance has 300 ms of loud
 * audio (RMS above -40 dBFS). Keeps every stream so tests can check what reached the recogniser.
 */
public class FakeSpeechToText implements SpeechToTextPort {

    static final int FRAMES_TO_HEAR = 15;

    private final List<Stream> streams = new CopyOnWriteArrayList<>();

    @Override
    public String vendor() {
        return "fake";
    }

    @Override
    public TranscriptionStream open(TranscriptListener listener) {
        Stream s = new Stream(listener);
        streams.add(s);
        return s;
    }

    public List<Stream> streams() {
        return streams;
    }

    public static final class Stream implements TranscriptionStream {

        private final TranscriptListener listener;
        private final AtomicInteger loudFrames = new AtomicInteger();
        private int loudInUtterance;
        private volatile boolean closed;

        Stream(TranscriptListener listener) {
            this.listener = listener;
        }

        @Override
        public synchronized void write(byte[] pcm16k) {
            if (closed || !loud(pcm16k)) return;
            loudFrames.incrementAndGet();
            if (++loudInUtterance == FRAMES_TO_HEAR) listener.onPartial("hello bolo");
        }

        @Override
        public synchronized void mark() {
            loudInUtterance = 0;
        }

        @Override
        public void close() {
            closed = true;
        }

        public int loudFrames() {
            return loudFrames.get();
        }

        public boolean closed() {
            return closed;
        }

        private static boolean loud(byte[] pcm) {
            double sum = 0;
            int n = pcm.length / 2;
            for (int i = 0; i + 1 < pcm.length; i += 2) {
                short s = (short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8));
                sum += (double) s * s;
            }
            return n > 0 && Math.sqrt(sum / n) / 32768.0 > 0.01;
        }
    }
}
