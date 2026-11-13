package app.bolo.speech.adapter.out.azure;

import app.bolo.speech.application.port.in.SpeechUnavailableException;
import app.bolo.speech.application.port.in.Voice;
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
    private final String coachVoice;

    public AzureTextToSpeech(String key, String region, String voice, String rate) {
        this(key, region, voice, rate, voice);
    }

    /** P3c (SET-5): the learner's voice key ("american-male") to an Azure voice; the Coach's voice is never one of them. */
    private java.util.Map<String, String> buddyVoices = java.util.Map.of();

    public AzureTextToSpeech(String key, String region, String voice, String rate, String coachVoice,
                             java.util.Map<String, String> buddyVoices) {
        this(key, region, voice, rate, coachVoice);
        this.buddyVoices = buddyVoices.entrySet().stream().filter(e -> !e.getValue().equals(coachVoice))
                .collect(java.util.stream.Collectors.toUnmodifiableMap(java.util.Map.Entry::getKey, java.util.Map.Entry::getValue));
    }

    public AzureTextToSpeech(String key, String region, String voice, String rate, String coachVoice) {
        this.key = key;
        this.region = region;
        this.voice = voice;
        this.rate = rate;
        this.coachVoice = coachVoice;
    }

    /** The Azure voice for a key; an unknown or missing key is the default Bolo voice. */
    String voiceFor(String buddyVoice) {
        return buddyVoice == null ? voice : buddyVoices.getOrDefault(buddyVoice, voice);
    }

    @Override
    public void synthesize(Voice who, String buddyVoice, String text, Consumer<byte[]> pcm24kChunk) {
        switch (who) {
            case COACH -> speak(coachVoice, "0%", text, pcm24kChunk);
            case BUDDY_SLOW -> speak(voiceFor(buddyVoice), "-25%", text, pcm24kChunk);
            default -> speak(voiceFor(buddyVoice), rate, text, pcm24kChunk);
        }
    }

    @Override
    public void synthesize(Voice who, String text, Consumer<byte[]> pcm24kChunk) {
        switch (who) {
            case COACH -> speak(coachVoice, "0%", text, pcm24kChunk);
            case BUDDY_SLOW -> speak(voice, "-25%", text, pcm24kChunk);
            default -> speak(voice, rate, text, pcm24kChunk);
        }
    }

    @Override
    public String vendor() {
        return "azure";
    }

    @Override
    public void synthesize(String text, Consumer<byte[]> pcm24kChunk) {
        speak(voice, rate, text, pcm24kChunk);
    }

    private void speak(String voiceName, String voiceRate, String text, Consumer<byte[]> pcm24kChunk) {
        SpeechConfig config = SpeechConfig.fromSubscription(key, region);
        config.setSpeechSynthesisOutputFormat(SpeechSynthesisOutputFormat.Raw24Khz16BitMonoPcm);
        SpeechSynthesizer synthesizer = new SpeechSynthesizer(config, (AudioConfig) null);
        try {
            synthesizer.Synthesizing.addEventListener((s, e) -> {
                byte[] audio = e.getResult().getAudioData();
                if (audio != null && audio.length > 0) pcm24kChunk.accept(audio);
            });
            SpeechSynthesisResult result = synthesizer.SpeakSsmlAsync(ssml(voiceName, voiceRate, text)).get(20, TimeUnit.SECONDS);
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

    String ssml(String voiceName, String voiceRate, String text) {
        String lang = voiceName.length() >= 5 ? voiceName.substring(0, 5) : "en-US";
        return "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xml:lang=\"" + escape(lang) + "\">"
                + "<voice name=\"" + escape(voiceName) + "\"><prosody rate=\"" + escape(voiceRate) + "\">" + escape(text)
                + "</prosody></voice></speak>";
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}
