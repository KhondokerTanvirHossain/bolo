package app.bolo.llm.application.port.in;

import java.util.List;
import java.util.Map;

/**
 * messages alternate user/assistant and end with a user message (current models reject assistant prefill).
 * {@code jsonSchema}: when set, the reply is JSON matching it (structured output). {@code fast}: thinking off at low
 * effort, for latency-bound turns (Bolo); otherwise adaptive thinking at low effort (the Coach).
 */
public record LlmRequest(String model, String system, List<LlmMessage> messages, int maxTokens, Map<String, Object> jsonSchema,
                         boolean fast) {

    public LlmRequest(String model, String system, List<LlmMessage> messages, int maxTokens) {
        this(model, system, messages, maxTokens, null, true);
    }
}
