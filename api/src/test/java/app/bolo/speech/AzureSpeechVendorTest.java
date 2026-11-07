package app.bolo.speech;

import app.bolo.speech.adapter.out.azure.AzureSpeechToText;
import app.bolo.speech.adapter.out.azure.AzureTextToSpeech;
import app.bolo.speech.application.port.in.TranscriptListener;
import app.bolo.speech.application.port.in.TranscriptionStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Real Azure calls; runs only where BOLO_AZURE_SPEECH_KEY is set (never in CI without the secret). */
@EnabledIfEnvironmentVariable(named = "BOLO_AZURE_SPEECH_KEY", matches = ".+")
class AzureSpeechVendorTest {

    static final String KEY = System.getenv("BOLO_AZURE_SPEECH_KEY");
    static final String REGION = System.getenv().getOrDefault("BOLO_AZURE_SPEECH_REGION", "centralindia");

    @Test
    void transcribesTheJobInterviewFixture() throws Exception {
        byte[] pcm = app.bolo.WavFixture.pcm("/audio/job-interview-16k.wav");
        AtomicReference<String> heard = new AtomicReference<>("");
        CountDownLatch gotIt = new CountDownLatch(1);
        TranscriptionStream stream = new AzureSpeechToText(KEY, REGION, "en-IN").open(new TranscriptListener() {
            @Override
            public void onPartial(String textSoFar) {
                heard.set(textSoFar);
                if (textSoFar.toLowerCase().contains("job interview")) gotIt.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                heard.set("FAILED: " + e.getMessage());
            }
        });
        try {
            for (int i = 0; i + 640 <= pcm.length; i += 640) stream.write(Arrays.copyOfRange(pcm, i, i + 640));
            for (int i = 0; i < 75; i++) stream.write(new byte[640]);
            assertThat(gotIt.await(10, TimeUnit.SECONDS)).as("heard: %s", heard.get()).isTrue();
        } finally {
            stream.close();
        }
    }

    @Test
    void speaksInBolosVoice() {
        AtomicInteger bytes = new AtomicInteger();
        new AzureTextToSpeech(KEY, REGION, "en-IN-NeerjaNeural", "-5%").synthesize("Hi, I'm Bolo.", c -> bytes.addAndGet(c.length));
        assertThat(bytes.get()).isGreaterThanOrEqualTo(24_000);
    }
}
