package app.bolo.llm.application.port.in;

public record LlmMessage(Role role, String text) {

    public enum Role { USER, ASSISTANT }

    public static LlmMessage user(String text) {
        return new LlmMessage(Role.USER, text);
    }

    public static LlmMessage assistant(String text) {
        return new LlmMessage(Role.ASSISTANT, text);
    }
}
