package app.bolo.memory.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** The updater (spec 2026-11-13): after a talk, it rewrites the learner's short note from the old note and the transcript. */
public final class MemoryPrompt {

    private MemoryPrompt() {
    }

    /** One transcript line: who spoke and what they said. */
    public record Said(boolean learner, String text) {
    }

    /** What the model returns. Empty strings stand for "none" (a new line has id "", a plan with no date has due ""). */
    /** {@code drop}: ids of about/interest lines to remove; about and interest lines left out are kept (review, P3b). */
    public record Reply(List<ReplyLine> about, List<ReplyThread> threads, List<ReplyLine> interests, List<String> drop, String topic) {
    }

    public record ReplyLine(String id, String text) {
    }

    public record ReplyThread(String id, String kind, String text, String said, String due) {
    }

    public static String system() {
        return """
                You keep the short notes that Bolo, a friendly English speaking buddy, has about one learner: a \
                Bangladeshi adult practising spoken English. After each talk you get today's date, the current notes and \
                the talk's transcript (from speech recognition, so ignore small glitches). Return the updated notes.
                Only what the learner said about themselves goes in. Never Bolo's lines, never your guesses.
                - about: simple facts about their life: job, field, kind of company, city, family role, what they're \
                preparing for. At most 12.
                - threads: things to follow up next time. kind "plan" for something they will do (due: the date it \
                should happen, if they said one, as YYYY-MM-DD, else ""), "worry" for something on their mind, "prep" \
                for how their English preparation is going. At most 8. Leave out plans they said are finished. When a \
                thread is mentioned again, set its said to today. Keep said as it was for threads not mentioned.
                - interests: things they enjoy talking about. At most 8.
                - topic: name this talk in 2 to 5 plain words, the way the learner would say it.
                About and interest lines you leave out are kept as they are; to remove one (it's no longer true, or they \
                asked you to forget it), put its id in drop. Threads you leave out are removed.
                Keep the id of every line you return, even if you reword it. A new line has id "". Write each line in plain \
                words the way the learner would say it, at most 120 characters, without labels like "user prefers".
                Never write down: health or mental health, religion, politics, sexuality, exact money amounts, phone \
                numbers, addresses, ID numbers, or anything about self-harm or a crisis. About other people, keep at \
                most a first name and their role ("my manager Rahim"). If nothing new was said, return the notes as they are.
                """;
    }

    /** The user message: today's date, the current notes (JSON with ids) and the transcript. */
    public static String input(LocalDate today, String notesJson, List<Said> transcript) {
        StringBuilder b = new StringBuilder("Today is ").append(today).append(".\n\nThe current notes:\n")
                .append(notesJson).append("\n\nThe talk, in order:\n");
        for (Said s : transcript) b.append(s.learner() ? "Learner: " : "Bolo: ").append(s.text()).append('\n');
        return b.toString();
    }

    public static Map<String, Object> schema() {
        Map<String, Object> str = Map.of("type", "string");
        Map<String, Object> line = Map.of("type", "object", "additionalProperties", false, "required", List.of("id", "text"),
                "properties", Map.of("id", str, "text", str));
        Map<String, Object> thread = Map.of("type", "object", "additionalProperties", false,
                "required", List.of("id", "kind", "text", "said", "due"),
                "properties", Map.of("id", str, "kind", Map.of("type", "string", "enum", List.of("plan", "worry", "prep")),
                        "text", str, "said", str, "due", str));
        return Map.of("type", "object", "additionalProperties", false,
                "required", List.of("about", "threads", "interests", "drop", "topic"),
                "properties", Map.of("about", Map.of("type", "array", "items", line),
                        "threads", Map.of("type", "array", "items", thread),
                        "interests", Map.of("type", "array", "items", line), "drop", Map.of("type", "array", "items", str),
                        "topic", str));
    }

    private static final java.time.format.DateTimeFormatter DAY = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH);

    /**
     * The block added to Bolo's instructions when a talk opens (TLK-11 changed 2026-11-13): what Bolo remembers, the one
     * follow-up to open with (if any), recent topics to avoid, and fresh ideas. {@code note} null: remembering is off.
     */
    public static String brief(MemoryNote note, MemoryThread opener, List<String> seeds) {
        StringBuilder b = new StringBuilder();
        if (note != null && !note.isEmpty()) {
            b.append("What you remember about them from earlier talks (use it naturally, like a friend; never list it back):\n");
            note.about().forEach(l -> b.append("- ").append(l.text()).append('\n'));
            note.threads().forEach(t -> b.append("- ").append(switch (t.kind()) {
                case PLAN -> "Plan";
                case WORRY -> "On their mind";
                case PREP -> "Their practice";
            }).append(": ").append(t.text()).append(" (they said this on ").append(DAY.format(t.said()))
                    .append(t.due() == null ? "" : "; for " + DAY.format(t.due())).append(")\n"));
            if (!note.interests().isEmpty()) {
                b.append("- They enjoy: ").append(String.join(", ", note.interests().stream().map(Line::text).toList())).append('\n');
            }
        }
        if (opener != null) {
            b.append("Open the talk with a short hello and one follow-up about: ").append(opener.text())
                    .append(" (they said this on ").append(DAY.format(opener.said())).append("). Ask it once. ")
                    .append("If they move on, follow them and don't bring it back.\n");
        }
        if (note != null && !note.recent().isEmpty()) {
            b.append("Topics you've talked about recently (pick something else): ").append(String.join("; ", note.recent())).append('\n');
        }
        if (!seeds.isEmpty()) {
            b.append("Ideas if the talk needs a new direction: ").append(String.join("; ", seeds)).append('\n');
        }
        return b.toString();
    }
}
