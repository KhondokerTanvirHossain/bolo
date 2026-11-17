package app.bolo.speech.adapter.out.azure;

import app.bolo.speech.application.port.in.HeardBangla;
import app.bolo.speech.application.port.out.BanglaSpeechPort;
import com.microsoft.cognitiveservices.speech.AutoDetectSourceLanguageConfig;
import com.microsoft.cognitiveservices.speech.AutoDetectSourceLanguageResult;
import com.microsoft.cognitiveservices.speech.PropertyId;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.audio.AudioInputStream;
import com.microsoft.cognitiveservices.speech.audio.AudioStreamFormat;
import com.microsoft.cognitiveservices.speech.audio.PushAudioInputStream;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Azure's language identification (English or Bangla) on one finished turn, which also recognises it in the language it
 * found. Spike 2026-11-18: through the live SDK with pronunciation scoring, the English recogniser is 0.47-0.81
 * "confident" on Bangla speech (0.91-0.95 on English), so confidence alone can't tell them apart; language
 * identification named all 9 test clips right (3 English, 4 Bangla, 2 mixed), in 2-3 s. Empty unless it's Bangla.
 */
public class AzureBanglaSpeech implements BanglaSpeechPort {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    static final String BANGLA = "bn-IN";

    private final String key;
    private final String region;

    public AzureBanglaSpeech(String key, String region) {
        this.key = key;
        this.region = region;
    }

    @Override
    public Optional<HeardBangla> recognise(byte[] wav16k, long timeoutMs) {
        SpeechConfig config = SpeechConfig.fromSubscription(key, region);
        AutoDetectSourceLanguageConfig languages = AutoDetectSourceLanguageConfig.fromLanguages(List.of("en-US", BANGLA));
        PushAudioInputStream push = AudioInputStream.createPushStream(AudioStreamFormat.getWaveFormatPCM(16000L, (short) 16, (short) 1));
        AudioConfig audio = AudioConfig.fromStreamInput(push);
        try (SpeechRecognizer recognizer = new SpeechRecognizer(config, languages, audio)) {
            push.write(Arrays.copyOfRange(wav16k, Math.min(44, wav16k.length), wav16k.length));
            push.close();
            var result = recognizer.recognizeOnceAsync().get(timeoutMs, TimeUnit.MILLISECONDS);
            if (result.getReason() != ResultReason.RecognizedSpeech) return Optional.empty();
            String language = AutoDetectSourceLanguageResult.fromResult(result).getLanguage();
            if (!BANGLA.equals(language)) return Optional.empty();
            String text = result.getText() == null ? "" : result.getText().strip();
            if (text.isEmpty()) return Optional.empty();
            return Optional.of(new HeardBangla(text, confidence(result.getProperties().getProperty(PropertyId.SpeechServiceResponse_JsonResult))));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (java.util.concurrent.TimeoutException e) {
            return Optional.empty();
        } catch (Exception e) {
            throw new IllegalStateException("azure language check failed: " + e.getClass().getSimpleName());
        } finally {
            audio.close();
            languages.close();
            config.close();
        }
    }

    /** The recognition's confidence when the JSON carries one; otherwise a "found Bangla" default. */
    static double confidence(String json) {
        try {
            var best = JSON.readTree(json == null ? "{}" : json).path("NBest").path(0).path("Confidence");
            return best.isNumber() ? best.asDouble() : 0.8;
        } catch (RuntimeException e) {
            return 0.8;
        }
    }
}
