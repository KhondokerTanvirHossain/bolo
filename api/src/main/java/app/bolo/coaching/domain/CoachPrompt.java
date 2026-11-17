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
        return system(false);
    }

    /** AX-8: with {@code stammer}, the Coach never picks or "fixes" a sentence for its repeats, blocks or pauses. */
    public static String system(boolean stammer) {
        return system(stammer, false);
    }

    /** SCN-2 (2026-11-15): a Standup rehearsal also asks for one standup tip. */
    public static String system(boolean stammer, boolean standup) {
        return (standup ? BASE + STANDUP : BASE) + (stammer ? STAMMER + "\n" : "");
    }

    static final String STANDUP = """
            This talk was a standup rehearsal: Bolo played their manager and asked for their update. Also return:
            - standupTip: ONE way their update could be clearer for a manager, from this list: outcome_first (say what's \
            done first, then the detail), give_a_date (an ETA with a day or time instead of "soon"), blocker_and_ask (name \
            the blocker and ask for what they need). "said" is copied exactly from one learner turn, "turn" that turn's \
            number, "better" their sentence rewritten that way (at most 25 words), "why" one warm sentence, at most 15 words.
            """;

    static final String STAMMER = "This learner stammers. Never choose a sentence, or suggest a better version, because of "
            + "repeated sounds or words, blocks or pauses, and never mention them.";

    private static final String BASE = """
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
                - grammar: at most ONE grammar point that would help them most (a list with one item, or empty when \
                there's none worth teaching). "said" is copied exactly from one learner turn, "turn" that turn's number, \
                "better" the same sentence with the grammar fixed (at most 25 words), "title" the point in plain words, at \
                most 8 words, no grammar jargon (e.g. "Past events: went, not go"), "why" at most 10 words. Not the same \
                sentence as an upgrade.
                - vocabulary: at most ONE word to grow (a list with one item, or empty). "said" is copied exactly from \
                one learner turn, "turn" that turn's number, "used" the plain word or words they used, copied from said \
                (or a word they said in Bangla), "word" a stronger, everyday English word or short phrase for it (at most \
                4 words, one they will really use, never rare or showy), "example" a natural sentence of at most 15 words \
                about their topic that uses the word, "why" at most 12 words.
                - sentenceLevel and vocabularyLevel: BUILDING, GOOD or STRONG for this talk.
                - topic: name the talk in 2 to 5 plain words, the way the learner would say it, e.g. "The payment bug demo".
                Judge clarity, not accent. Never use the words: mistake, wrong, error, bad, fail, incorrect, poor, accent.
                """;

    public static String transcript(List<Line> lines) {
        StringBuilder b = new StringBuilder("The talk, in order. Learner turns are numbered.\n");
        for (Line l : lines) {
            b.append(l.learner() ? "Learner turn " + l.turn() + ": " : "Bolo: ").append(l.text()).append('\n');
        }
        return b.toString();
    }

    public static Map<String, Object> schema() {
        return schema(false);
    }

    /** JSON schema for structured output; with {@code standup}, the required standupTip too. */
    public static Map<String, Object> schema(boolean standup) {
        Map<String, Object> level = Map.of("type", "string", "enum", List.of("BUILDING", "GOOD", "STRONG"));
        Map<String, Object> strength = Map.of("type", "object", "additionalProperties", false,
                "required", List.of("turn", "quote", "text"),
                "properties", Map.of("turn", Map.of("type", "integer"), "quote", Map.of("type", "string"),
                        "text", Map.of("type", "string")));
        Map<String, Object> upgrade = Map.of("type", "object", "additionalProperties", false,
                "required", List.of("turn", "said", "better"),
                "properties", Map.of("turn", Map.of("type", "integer"), "said", Map.of("type", "string"),
                        "better", Map.of("type", "string")));
        Map<String, Object> str = Map.of("type", "string");
        Map<String, Object> grammar = Map.of("type", "object", "additionalProperties", false,
                "required", List.of("turn", "said", "better", "title", "why"),
                "properties", Map.of("turn", Map.of("type", "integer"), "said", str, "better", str, "title", str, "why", str));
        Map<String, Object> vocabulary = Map.of("type", "object", "additionalProperties", false,
                "required", List.of("turn", "said", "used", "word", "example", "why"),
                "properties", Map.of("turn", Map.of("type", "integer"), "said", str, "used", str, "word", str, "example", str, "why", str));
        Map<String, Object> props = new java.util.LinkedHashMap<>(Map.of("strength", strength, "upgrades", Map.of("type", "array", "items", upgrade),
                "grammar", Map.of("type", "array", "items", grammar), "vocabulary", Map.of("type", "array", "items", vocabulary),
                "sentenceLevel", level, "vocabularyLevel", level, "topic", Map.of("type", "string")));
        List<String> required = new java.util.ArrayList<>(List.of("strength", "upgrades", "grammar", "vocabulary", "sentenceLevel",
                "vocabularyLevel", "topic"));
        if (standup) {
            props.put("standupTip", Map.of("type", "object", "additionalProperties", false,
                    "required", List.of("rule", "turn", "said", "better", "why"),
                    "properties", Map.of("rule", Map.of("type", "string", "enum", List.of("outcome_first", "give_a_date", "blocker_and_ask")),
                            "turn", Map.of("type", "integer"), "said", Map.of("type", "string"), "better", Map.of("type", "string"),
                            "why", Map.of("type", "string"))));
            required.add("standupTip");
        }
        return Map.of("type", "object", "additionalProperties", false, "required", required, "properties", props);
    }
}
