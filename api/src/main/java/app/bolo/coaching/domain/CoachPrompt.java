package app.bolo.coaching.domain;

import java.util.List;
import java.util.Map;

/** The Coach (PRD §4: warm, specific, brief; "here's what you said → the better way → try it"). */
public final class CoachPrompt {

    private CoachPrompt() {
    }

    public record Line(boolean learner, int turn, String text) {
    }

    public static String system() {
        return """
                You are the learner's English speaking coach. The learner is a Bangladeshi adult who just had a casual \
                spoken conversation with Bolo, a friendly buddy. You read the transcript afterwards. The transcript \
                comes from speech recognition, so ignore small recognition glitches.
                Return:
                - strength: ONE specific thing the learner did well in this talk, citing a real moment. "quote" must be \
                words copied exactly from one learner turn, and "turn" that turn's number. "text" is one warm sentence \
                to the learner, in the second person, at most 20 words. Never generic praise like "Good job".
                - upgrades: up to 2 learner sentences to make one step better. "said" is copied exactly from one \
                learner turn (a contiguous part of it). "better" keeps their meaning and their words where possible, \
                fixes the grammar, adds at most one linking word or detail, sounds natural when spoken, and is at most \
                25 words. Pick sentences where the upgrade helps most. Skip a sentence that is already fine.
                - sentenceLevel and vocabularyLevel: BUILDING, GOOD or STRONG for this talk.
                - topic: name the talk in 2 to 5 plain words, the way the learner would say it, e.g. "The payment bug demo".
                Judge clarity, not accent. Never use the words: mistake, wrong, error, bad, fail, incorrect, poor, accent.
                """;
    }

    public static String transcript(List<Line> lines) {
        StringBuilder b = new StringBuilder("The talk, in order. Learner turns are numbered.\n");
        for (Line l : lines) {
            b.append(l.learner() ? "Learner turn " + l.turn() + ": " : "Bolo: ").append(l.text()).append('\n');
        }
        return b.toString();
    }

    /** JSON schema for structured output. */
    public static Map<String, Object> schema() {
        Map<String, Object> level = Map.of("type", "string", "enum", List.of("BUILDING", "GOOD", "STRONG"));
        Map<String, Object> strength = Map.of("type", "object", "additionalProperties", false,
                "required", List.of("turn", "quote", "text"),
                "properties", Map.of("turn", Map.of("type", "integer"), "quote", Map.of("type", "string"),
                        "text", Map.of("type", "string")));
        Map<String, Object> upgrade = Map.of("type", "object", "additionalProperties", false,
                "required", List.of("turn", "said", "better"),
                "properties", Map.of("turn", Map.of("type", "integer"), "said", Map.of("type", "string"),
                        "better", Map.of("type", "string")));
        return Map.of("type", "object", "additionalProperties", false,
                "required", List.of("strength", "upgrades", "sentenceLevel", "vocabularyLevel", "topic"),
                "properties", Map.of("strength", strength, "upgrades", Map.of("type", "array", "items", upgrade),
                        "sentenceLevel", level, "vocabularyLevel", level, "topic", Map.of("type", "string")));
    }
}
