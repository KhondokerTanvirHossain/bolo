package app.bolo.speech.adapter.out.fake;

import app.bolo.speech.application.port.in.HeardBangla;
import app.bolo.speech.application.port.out.BanglaSpeechPort;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** For tests and local development: answers a set Bangla text (default: nothing heard), maybe slowly or failing. */
public class FakeBanglaSpeech implements BanglaSpeechPort {

    private volatile HeardBangla reply;
    private volatile long delayMs;
    private volatile boolean fail;
    private final AtomicInteger calls = new AtomicInteger();

    public void reply(String text, double confidence) {
        this.reply = text == null ? null : new HeardBangla(text, confidence);
    }

    public void delayMs(long ms) {
        this.delayMs = ms;
    }

    public void fail(boolean fail) {
        this.fail = fail;
    }

    public int calls() {
        return calls.get();
    }

    public void reset() {
        reply = null;
        delayMs = 0;
        fail = false;
        calls.set(0);
    }

    @Override
    public Optional<HeardBangla> recognise(byte[] wav16k, long timeoutMs) {
        calls.incrementAndGet();
        if (delayMs > 0) {
            try {
                Thread.sleep(Math.min(delayMs, timeoutMs));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
            if (delayMs > timeoutMs) return Optional.empty();
        }
        if (fail) throw new IllegalStateException("fake bangla down");
        return Optional.ofNullable(reply);
    }
}
