package app.bolo.speech.adapter.out.azure;

import app.bolo.speech.application.port.in.SpeechUnavailableException;
import app.bolo.speech.application.port.in.TranscriptListener;
import app.bolo.speech.application.port.in.TranscriptionStream;
import app.bolo.speech.application.port.out.SpeechToTextPort;
import com.microsoft.cognitiveservices.speech.CancellationReason;
import com.microsoft.cognitiveservices.speech.PronunciationAssessmentConfig;
import com.microsoft.cognitiveservices.speech.PronunciationAssessmentGradingSystem;
import com.microsoft.cognitiveservices.speech.PronunciationAssessmentGranularity;
import com.microsoft.cognitiveservices.speech.PropertyId;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.audio.AudioInputStream;
import com.microsoft.cognitiveservices.speech.audio.AudioStreamFormat;
import com.microsoft.cognitiveservices.speech.audio.PushAudioInputStream;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Azure continuous recognition over a push stream. Logs never carry the recognised text. */
@Slf4j
public class AzureSpeechToText implements SpeechToTextPort {

    private final String key;
    private final String region;
    private final String language;

    public AzureSpeechToText(String key, String region, String language) {
        this.key = key;
        this.region = region;
        this.language = language;
    }

    @Override
    public String vendor() {
        return "azure";
    }

    @Override
    public TranscriptionStream open(TranscriptListener listener) {
        SpeechConfig config = SpeechConfig.fromSubscription(key, region);
        config.setSpeechRecognitionLanguage(language);
        PushAudioInputStream push = AudioInputStream.createPushStream(AudioStreamFormat.getWaveFormatPCM(16000L, (short) 16, (short) 1));
        AudioConfig audio = AudioConfig.fromStreamInput(push);
        SpeechRecognizer recognizer = new SpeechRecognizer(config, audio);
        // Unscripted pronunciation assessment rides on the live recognition: each finished segment arrives scored,
        // word by word and sound by sound (IPA), with no second request.
        PronunciationAssessmentConfig assessment = new PronunciationAssessmentConfig("",
                PronunciationAssessmentGradingSystem.HundredMark, PronunciationAssessmentGranularity.Phoneme, false);
        assessment.setPhonemeAlphabet("IPA");
        assessment.applyTo(recognizer);
        Utterance utterance = new Utterance(listener);
        recognizer.recognizing.addEventListener((s, e) -> utterance.partial(e.getResult().getText()));
        recognizer.recognized.addEventListener((s, e) -> {
            if (e.getResult().getReason() != ResultReason.RecognizedSpeech) return;
            utterance.fin(e.getResult().getText());
            String json = e.getResult().getProperties().getProperty(PropertyId.SpeechServiceResponse_JsonResult);
            AzureScoreParser.parse(json == null ? "" : json).ifPresentOrElse(listener::onScored,
                    () -> log.debug("azure stt: no pronunciation scores on a segment"));
        });
        recognizer.canceled.addEventListener((s, e) -> {
            if (e.getReason() == CancellationReason.Error) {
                log.warn("azure stt canceled: {} {}", e.getErrorCode(), e.getErrorDetails());
                listener.onFailure(new SpeechUnavailableException("azure stt: " + e.getErrorCode()));
            }
        });
        try {
            recognizer.startContinuousRecognitionAsync().get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            closeAll(recognizer, audio, push, config);
            throw new SpeechUnavailableException("azure stt did not start", e);
        }
        return new TranscriptionStream() {
            private volatile boolean closed;

            @Override
            public void write(byte[] pcm16k) {
                if (!closed) push.write(pcm16k);
            }

            @Override
            public void mark() {
                utterance.mark();
            }

            @Override
            public void close() {
                if (closed) return;
                closed = true;
                try {
                    push.close();
                    recognizer.stopContinuousRecognitionAsync().get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("azure stt stop: {}", e.getClass().getSimpleName());
                } finally {
                    assessment.close();
                    closeAll(recognizer, audio, null, config);
                }
            }
        };
    }

    private static void closeAll(SpeechRecognizer r, AudioConfig a, PushAudioInputStream p, SpeechConfig c) {
        try { r.close(); } catch (Exception ignored) { }
        try { a.close(); } catch (Exception ignored) { }
        if (p != null) try { p.close(); } catch (Exception ignored) { }
        try { c.close(); } catch (Exception ignored) { }
    }

    /**
     * Final segments since the last mark, plus the newest partial. The listener is called outside this object's lock:
     * it takes the conversation's lock, and the conversation calls mark() - holding both in opposite orders deadlocks.
     */
    static final class Utterance {

        private final TranscriptListener listener;
        private final List<String> finals = new ArrayList<>();
        private String partial = "";

        Utterance(TranscriptListener listener) {
            this.listener = listener;
        }

        void partial(String text) {
            String now;
            synchronized (this) {
                partial = text == null ? "" : text;
                now = textSoFar();
            }
            listener.onPartial(now);
        }

        void fin(String text) {
            String now;
            synchronized (this) {
                partial = "";
                if (text != null && !text.isBlank()) finals.add(text.trim());
                now = textSoFar();
            }
            listener.onPartial(now);
        }

        synchronized void mark() {
            finals.clear();
            partial = "";
        }

        private synchronized String textSoFar() {
            List<String> parts = new ArrayList<>(finals);
            if (!partial.isBlank()) parts.add(partial.trim());
            return String.join(" ", parts);
        }
    }
}
