package app.bolo.speech.application.service;

import app.bolo.common.id.TalkContext;
import app.bolo.common.usage.UsageRecorder;
import app.bolo.speech.application.port.in.StreamTranscriptionUseCase;
import app.bolo.speech.application.port.in.SynthesizeUseCase;
import app.bolo.speech.application.port.in.TranscriptListener;
import app.bolo.speech.application.port.in.TranscriptionStream;
import app.bolo.speech.application.port.out.SpeechToTextPort;
import app.bolo.speech.application.port.out.TextToSpeechPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Delegates to the configured vendor and writes the usage ledger: audio milliseconds heard, characters spoken. */
@Service
@RequiredArgsConstructor
public class SpeechService implements StreamTranscriptionUseCase, SynthesizeUseCase {

    /** 16 kHz × 2 bytes per sample. */
    private static final int BYTES_PER_MS_16K = 32;

    private final SpeechToTextPort stt;
    private final TextToSpeechPort tts;
    private final UsageRecorder usage;

    @Override
    public TranscriptionStream open(TalkContext ctx, TranscriptListener listener) {
        TranscriptionStream inner = stt.open(listener);
        AtomicLong bytes = new AtomicLong();
        AtomicBoolean closed = new AtomicBoolean();
        return new TranscriptionStream() {
            @Override
            public void write(byte[] pcm16k) {
                bytes.addAndGet(pcm16k.length);
                inner.write(pcm16k);
            }

            @Override
            public void mark() {
                inner.mark();
            }

            @Override
            public void close() {
                if (!closed.compareAndSet(false, true)) return;
                try {
                    inner.close();
                } finally {
                    usage.record(ctx.learnerId(), ctx.sessionId(), stt.vendor(), "stt", bytes.get() / BYTES_PER_MS_16K, "audio_ms");
                }
            }
        };
    }

    @Override
    public void synthesize(TalkContext ctx, String text, Consumer<byte[]> pcm24kChunk) {
        try {
            tts.synthesize(text, pcm24kChunk);
        } finally {
            usage.record(ctx.learnerId(), ctx.sessionId(), tts.vendor(), "tts", text.length(), "chars");
        }
    }
}
