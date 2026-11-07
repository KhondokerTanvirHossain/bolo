package app.bolo.speech.adapter.out.azure;

import app.bolo.speech.application.port.in.SpeechUnavailableException;
import app.bolo.speech.application.port.out.TextToSpeechPort;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisCancellationDetails;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisOutputFormat;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisResult;
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Bolo's voice: SSML with the configured voice and rate, streamed as raw 24 kHz PCM while it is synthesised. */
@Slf4j
public class AzureTextToSpeech implements TextToSpeechPort {

    private final String key;
    private final String region;
    private final String voice;
    private final String rate;

    public AzureTextToSpeech(String key, String region, String voice, String rate) {
        this.key = key;
        this.region = region;
        this.voice = voice;
        this.rate = rate;
    }

    @Override
    public String vendor() {
        return "azure";
    }

    @Override
    public void synthesize(String text, Consumer<byte[]> pcm24kChunk) {
        SpeechConfig config = SpeechConfig.fromSubscription(key, region);
        config.setSpeechSynthesisOutputFormat(SpeechSynthesisOutputFormat.Raw24Khz16BitMonoPcm);
        SpeechSynthesizer synthesizer = new SpeechSynthesizer(config, (AudioConfig) null);
        try {
            synthesizer.Synthesizing.addEventListener((s, e) -> {
                byte[] audio = e.getResult().getAudioData();
                if (audio != null && audio.length > 0) pcm24kChunk.accept(audio);
            });
            SpeechSynthesisResult result = synthesizer.SpeakSsmlAsync(ssml(text)).get(20, TimeUnit.SECONDS);
            try {
                if (result.getReason() != ResultReason.SynthesizingAudioCompleted) {
                    var details = SpeechSynthesisCancellationDetails.fromResult(result);
                    log.warn("azure tts canceled: {} {}", details.getErrorCode(), details.getErrorDetails());
                    throw new SpeechUnavailableException("azure tts: " + details.getErrorCode());
                }
            } finally {
                result.close();
            }
        } catch (SpeechUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new SpeechUnavailableException("azure tts failed", e);
        } finally {
            synthesizer.close();
            config.close();
        }
    }

    String ssml(String text) {
        return "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xml:lang=\"en-IN\">"
                + "<voice name=\"" + escape(voice) + "\"><prosody rate=\"" + escape(rate) + "\">" + escape(text)
                + "</prosody></voice></speak>";
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}
