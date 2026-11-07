package app.bolo.speech.adapter.out.fake;

import app.bolo.speech.application.port.in.ScoredPhoneme;
import app.bolo.speech.application.port.in.ScoredSegment;
import app.bolo.speech.application.port.in.ScoredWord;
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
    private volatile List<ScoredWord> nextWords = defaultWords();

    /** The scored words the fake reports with each "hello bolo" (times are offsets from the utterance's first loud frame). */
    public void nextWords(List<ScoredWord> words) {
        this.nextWords = words == null ? defaultWords() : List.copyOf(words);
    }

    static List<ScoredWord> defaultWords() {
        return List.of(
                new ScoredWord("hello", 0, 150, 95, "None", List.of(new ScoredPhoneme("h", 95), new ScoredPhoneme("ɛ", 95))),
                new ScoredWord("bolo", 150, 300, 95, "None", List.of(new ScoredPhoneme("b", 95), new ScoredPhoneme("oʊ", 95))));
    }

    @Override
    public String vendor() {
        return "fake";
    }

    @Override
    public TranscriptionStream open(TranscriptListener listener) {
        Stream s = new Stream(listener, this);
        streams.add(s);
        return s;
    }

    public List<Stream> streams() {
        return streams;
    }

    public static final class Stream implements TranscriptionStream {

        private final TranscriptListener listener;
        private final FakeSpeechToText owner;
        private final AtomicInteger loudFrames = new AtomicInteger();
        private int loudInUtterance;
        private long streamMs;
        private long utteranceStartMs = -1;
        private volatile boolean closed;

        Stream(TranscriptListener listener, FakeSpeechToText owner) {
            this.listener = listener;
            this.owner = owner;
        }

        @Override
        public void write(byte[] pcm16k) {
            ScoredSegment scored = null;
            boolean heard = false;
            synchronized (this) {
                long at = streamMs;
                streamMs += pcm16k.length / 32;
                if (closed || !loud(pcm16k)) return;
                loudFrames.incrementAndGet();
                if (utteranceStartMs < 0) utteranceStartMs = at;
                if (++loudInUtterance == FRAMES_TO_HEAR) {
                    heard = true;
                    long base = utteranceStartMs;
                    scored = new ScoredSegment("hello bolo", owner.nextWords.stream()
                            .map(w -> new ScoredWord(w.word(), base + w.startMs(), base + w.endMs(), w.accuracy(), w.errorType(), w.phonemes()))
                            .toList());
                }
            }
            if (heard) {
                listener.onPartial("hello bolo");
                listener.onScored(scored);
            }
        }

        @Override
        public synchronized void mark() {
            loudInUtterance = 0;
            utteranceStartMs = -1;
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
