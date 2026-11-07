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
}
