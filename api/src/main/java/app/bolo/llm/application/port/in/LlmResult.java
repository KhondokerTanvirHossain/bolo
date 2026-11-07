package app.bolo.llm.application.port.in;

public record LlmResult(String text, long inputTokens, long outputTokens) {
}
