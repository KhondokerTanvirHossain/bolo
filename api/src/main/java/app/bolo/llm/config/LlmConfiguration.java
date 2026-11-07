package app.bolo.llm.config;

import app.bolo.llm.adapter.out.anthropic.AnthropicLlm;
import app.bolo.llm.adapter.out.fake.FakeLlm;
import app.bolo.llm.application.port.out.LlmPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** bolo.llm.vendor: anthropic | fake. */
@Configuration
class LlmConfiguration {

    @Bean
    @ConditionalOnProperty(name = "bolo.llm.vendor", havingValue = "anthropic")
    LlmPort anthropicLlm(@Value("${bolo.llm.anthropic.key}") String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("bolo.llm.vendor=anthropic but BOLO_ANTHROPIC_API_KEY is empty");
        }
        return new AnthropicLlm(key);
    }

    @Bean
    @ConditionalOnProperty(name = "bolo.llm.vendor", havingValue = "fake")
    FakeLlm fakeLlm() {
        return new FakeLlm();
    }
}
