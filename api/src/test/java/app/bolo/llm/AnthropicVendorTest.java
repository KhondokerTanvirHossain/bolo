package app.bolo.llm;

import app.bolo.llm.adapter.out.anthropic.AnthropicLlm;
import app.bolo.llm.application.port.in.LlmMessage;
import app.bolo.llm.application.port.in.LlmRequest;
import app.bolo.llm.application.port.in.LlmResult;
import app.bolo.talk.domain.BuddyPrompt;
import app.bolo.talk.domain.Exchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** A real Claude call; runs only where BOLO_ANTHROPIC_API_KEY is set. */
@EnabledIfEnvironmentVariable(named = "BOLO_ANTHROPIC_API_KEY", matches = ".+")
class AnthropicVendorTest {

    @Test
    void bolosReplyIsShortAndAsksAQuestion() throws Exception {
        var llm = new AnthropicLlm(System.getenv("BOLO_ANTHROPIC_API_KEY"));
        var history = List.of(new Exchange(Exchange.Speaker.BUDDY, "Hi, I'm Bolo. What do you do?"),
                new Exchange(Exchange.Speaker.LEARNER, "I work in a bank."));
        LlmResult r = llm.stream(new LlmRequest("claude-haiku-5-5", BuddyPrompt.system("job_interviews", "interviews", 5),
                BuddyPrompt.messages(history).stream().map(e -> e.speaker() == Exchange.Speaker.BUDDY
                        ? LlmMessage.assistant(e.text()) : LlmMessage.user(e.text())).toList(), 300), d -> { }).done().get(10, TimeUnit.SECONDS);
        assertThat(r.text().trim()).endsWith("?");
        assertThat(r.text().split("\\s+").length).isLessThanOrEqualTo(45);
        assertThat(r.inputTokens()).isPositive();
    }
}
