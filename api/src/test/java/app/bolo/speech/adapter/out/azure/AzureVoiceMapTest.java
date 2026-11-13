package app.bolo.speech.adapter.out.azure;

import org.junit.jupiter.api.Test;

import java.util.Map;

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
}
