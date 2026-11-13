package app.bolo.speech.adapter.out.azure;

import org.junit.jupiter.api.Test;

import app.bolo.speech.application.port.in.Voice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/** P3c (SET-5): the learner's voice key picks the Azure voice; the Coach's voice is never Bolo's. */
class AzureVoiceMapTest {

    static final Map<String, String> VOICES = Map.of("british-female", "en-GB-SoniaNeural", "american-male", "en-US-GuyNeural",
            "sneaky", "en-US-AndrewNeural");

    AzureTextToSpeech tts = new AzureTextToSpeech("k", "centralindia", "en-GB-SoniaNeural", "-5%", "en-US-AndrewNeural", VOICES);

    @Test
    void aKeyPicksItsVoice() {
        assertThat(tts.voiceFor("american-male")).isEqualTo("en-US-GuyNeural");
    }

    @Test
    void unknownOrMissingIsTheDefault() {
        assertThat(tts.voiceFor("klingon-female")).isEqualTo("en-GB-SoniaNeural");
        assertThat(tts.voiceFor(null)).isEqualTo("en-GB-SoniaNeural");
    }

    @Test
    void theCoachsVoiceIsNeverBolos() {
        assertThat(tts.voiceFor("sneaky")).isEqualTo("en-GB-SoniaNeural");
    }

    /** Review (P3c, Important): a chosen voice Azure can't speak falls back to the default voice; the talk goes on. */
    @Test
    void aVoiceAzureCantSpeakFallsBackToTheDefault() {
        List<String> spoken = new ArrayList<>();
        AzureTextToSpeech flaky = new AzureTextToSpeech("k", "centralindia", "en-GB-SoniaNeural", "-5%", "en-US-AndrewNeural", VOICES) {
            @Override
            void speak(String voiceName, String voiceRate, String text, Consumer<byte[]> pcm) {
                spoken.add(voiceName);
                if (voiceName.equals("en-US-GuyNeural")) throw new IllegalStateException("voice not available");
                pcm.accept(new byte[]{1, 2});
            }
        };
        List<byte[]> audio = new ArrayList<>();
        flaky.synthesize(Voice.BUDDY, "american-male", "Hello there", audio::add);
        assertThat(spoken).containsExactly("en-US-GuyNeural", "en-GB-SoniaNeural");
        assertThat(audio).hasSize(1);
    }
}
