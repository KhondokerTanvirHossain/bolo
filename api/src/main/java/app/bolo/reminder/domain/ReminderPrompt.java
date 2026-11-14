package app.bolo.reminder.domain;

import java.util.List;
import java.util.Map;

/** One reminder line from the follow-up Bolo would open the next talk with. */
public final class ReminderPrompt {

    private ReminderPrompt() {
    }

    public static String system() {
        return """
                You write one phone notification for Bolo, a friendly companion the learner chats with every day.
                It picks up one thread from their life so they want to come back and tell Bolo about it.
                Rules: at most 80 characters; third person, starting with "Bolo" (for example "Bolo wants to know how the demo went.");
                warm and curious, never pushy; no emoji; never mention English, lessons, courses, practice, streaks or missed days;
                never include health, religion, politics, money amounts or contact details. Reply as JSON {"line": "..."}.""";
    }

    public static String input(String followUp) {
        return "The thread to pick up: " + followUp;
    }

    public static Map<String, Object> schema() {
        return Map.of("type", "object", "properties", Map.of("line", Map.of("type", "string")),
                "required", List.of("line"), "additionalProperties", false);
    }
}
