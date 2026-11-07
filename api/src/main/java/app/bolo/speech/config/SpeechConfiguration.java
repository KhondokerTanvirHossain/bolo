package app.bolo.speech.config;

import app.bolo.speech.adapter.out.azure.AzureScriptedAssessment;
import app.bolo.speech.adapter.out.azure.AzureSpeechToText;
import app.bolo.speech.adapter.out.azure.AzureTextToSpeech;
import app.bolo.speech.adapter.out.fake.FakeScriptedAssessment;
import app.bolo.speech.adapter.out.fake.FakeSpeechToText;
import app.bolo.speech.adapter.out.fake.FakeTextToSpeech;
import app.bolo.speech.application.port.out.ScriptedAssessmentPort;
import app.bolo.speech.application.port.out.SpeechToTextPort;
import app.bolo.speech.application.port.out.TextToSpeechPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The vendor is a property (bolo.speech.vendor: azure | fake), never a code change. */
@Configuration
class SpeechConfiguration {

    @Configuration
    @ConditionalOnProperty(name = "bolo.speech.vendor", havingValue = "azure")
    static class Azure {

        @Bean
        SpeechToTextPort azureSpeechToText(@Value("${bolo.speech.azure.key}") String key,
                                           @Value("${bolo.speech.azure.region}") String region,
                                           @Value("${bolo.speech.azure.stt-language}") String language) {
            return new AzureSpeechToText(requireKey(key), region, language);
        }

        @Bean
        TextToSpeechPort azureTextToSpeech(@Value("${bolo.speech.azure.key}") String key,
                                           @Value("${bolo.speech.azure.region}") String region,
                                           @Value("${bolo.speech.azure.buddy-voice}") String voice,
                                           @Value("${bolo.speech.azure.buddy-rate}") String rate,
                                           @Value("${bolo.speech.azure.coach-voice}") String coachVoice) {
            return new AzureTextToSpeech(requireKey(key), region, voice, rate, coachVoice);
        }

        @Bean
        ScriptedAssessmentPort azureScriptedAssessment(@Value("${bolo.speech.azure.key}") String key,
                                                       @Value("${bolo.speech.azure.region}") String region) {
            return new AzureScriptedAssessment(requireKey(key), "https://" + region + ".stt.speech.microsoft.com");
        }

        private static String requireKey(String key) {
            if (key == null || key.isBlank()) {
                throw new IllegalStateException("bolo.speech.vendor=azure but BOLO_AZURE_SPEECH_KEY is empty");
            }
            return key;
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "bolo.speech.vendor", havingValue = "fake")
    static class Fake {

        @Bean
        FakeSpeechToText fakeSpeechToText() {
            return new FakeSpeechToText();
        }

        @Bean
        FakeTextToSpeech fakeTextToSpeech() {
            return new FakeTextToSpeech();
        }

        @Bean
        FakeScriptedAssessment fakeScriptedAssessment() {
            return new FakeScriptedAssessment();
        }
    }
}
