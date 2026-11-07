package app.bolo.llm.application.port.in;

import java.util.List;

/** messages alternate user/assistant and end with a user message (current models reject assistant prefill). */
public record LlmRequest(String model, String system, List<LlmMessage> messages, int maxTokens) {
}
