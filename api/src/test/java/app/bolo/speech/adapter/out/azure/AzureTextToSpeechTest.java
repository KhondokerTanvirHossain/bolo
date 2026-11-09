package app.bolo.speech.adapter.out.azure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Review: the SSML language follows the voice, so Bolo's Indian-English voice isn't read as American. */
class AzureTextToSpeechTest {

    @Test
    void theSsmlLanguageIsTheVoicesOwn() {
        var tts = new AzureTextToSpeech("k", "centralindia", "en-IN-NeerjaNeural", "-5%", "en-US-AndrewNeural");
        assertThat(tts.ssml("en-IN-NeerjaNeural", "-5%", "Hi")).contains("xml:lang=\"en-IN\"");
        assertThat(tts.ssml("en-US-AndrewNeural", "0%", "Hi")).contains("xml:lang=\"en-US\"");
    }

    /** Founder (2026-11-10): Bolo speaks British English; the SSML language follows the voice. */
    @org.junit.jupiter.api.Test
    void aBritishVoiceSpeaksBritishEnglish() {
        var tts = new AzureTextToSpeech("k", "centralindia", "en-GB-SoniaNeural", "-5%", "en-US-AndrewNeural");
        assertThat(tts.ssml("en-GB-SoniaNeural", "-5%", "Hi")).contains("xml:lang=\"en-GB\"").contains("en-GB-SoniaNeural");
    }

    @org.junit.jupiter.api.Test
    void bolosVoiceIsBritish() throws Exception {
        String yml = new String(getClass().getResourceAsStream("/application.yml").readAllBytes());
        assertThat(yml).contains("buddy-voice: en-GB-SoniaNeural");
    }
}
