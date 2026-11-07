package app.bolo.speech.adapter.out.azure;

import app.bolo.speech.application.port.in.TranscriptListener;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** Review #4: the listener runs outside the utterance lock, so a thread holding the conversation can still mark(). */
class UtteranceTest {

    @Test
    void theListenerIsCalledWithoutHoldingTheUtteranceLock() throws Exception {
        AtomicBoolean markedMeanwhile = new AtomicBoolean();
        AzureSpeechToText.Utterance[] u = new AzureSpeechToText.Utterance[1];
        u[0] = new AzureSpeechToText.Utterance(new TranscriptListener() {
            @Override
            public void onPartial(String textSoFar) {
                CountDownLatch done = new CountDownLatch(1);
                Thread.ofPlatform().start(() -> {
                    u[0].mark();
                    done.countDown();
                });
                try {
                    markedMeanwhile.set(done.await(1, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            @Override
            public void onFailure(Exception e) {
            }
        });
        u[0].partial("hello");
        assertThat(markedMeanwhile).isTrue();
    }
}
