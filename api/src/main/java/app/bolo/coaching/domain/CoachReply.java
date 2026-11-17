package app.bolo.coaching.domain;

import java.util.List;

/** What the Coach model returns (structured output); validated by {@link ReplyCheck} before anything is shown. */
/** {@code topic}: the Coach's 2–5 word name for the talk (PRG-8); may be missing in older replies. */
/** {@code standupTip} (SCN-2): only asked for in a Standup rehearsal; null otherwise. */
/** {@code grammar}, {@code vocabulary} (founder 2026-11-18): at most one each; null or empty in older replies. */
public record CoachReply(Strength strength, List<Upgrade> upgrades, String sentenceLevel, String vocabularyLevel, String topic,
                         StandupTip standupTip, List<Grammar> grammar, List<Vocabulary> vocabulary) {

    public CoachReply(Strength strength, List<Upgrade> upgrades, String sentenceLevel, String vocabularyLevel, String topic) {
        this(strength, upgrades, sentenceLevel, vocabularyLevel, topic, null);
    }

    public CoachReply(Strength strength, List<Upgrade> upgrades, String sentenceLevel, String vocabularyLevel, String topic,
                      StandupTip standupTip) {
        this(strength, upgrades, sentenceLevel, vocabularyLevel, topic, standupTip, null, null);
    }

    public List<Grammar> grammarOrEmpty() {
        return grammar == null ? List.of() : grammar;
    }

    public List<Vocabulary> vocabularyOrEmpty() {
        return vocabulary == null ? List.of() : vocabulary;
    }

    /** SCN-2 (2026-11-15): the tip ids and their card titles. */
    public static final java.util.Map<String, String> STANDUP_TIPS = java.util.Map.of(
            "outcome_first", "Lead with the outcome",
            "give_a_date", "Give a date, not \"soon\"",
            "blocker_and_ask", "Say the blocker and what you need");


    public static final String DEFAULT_TOPIC = "Free talk";

    /** A short, one-line name, or "Free talk". */
    public String topicOrDefault() {
        if (topic == null) return DEFAULT_TOPIC;
        String t = topic.strip().replaceAll("^[\"\'“”]+|[\"\'“”.]+$", "");
        boolean ok = !t.isEmpty() && t.length() <= 40 && !t.contains("\n") && t.split("\\s+").length <= 6;
        return ok ? t : DEFAULT_TOPIC;
    }


    public record Strength(int turn, String quote, String text) {
    }

    public record Upgrade(int turn, String said, String better) {
    }

    public record StandupTip(String rule, int turn, String said, String better, String why) {
    }

    /** One grammar point: their sentence, the better one, a short plain title and why. */
    public record Grammar(int turn, String said, String better, String title, String why) {
    }

    /** One word to grow: {@code used} (their word, maybe Bangla) → {@code word}, shown in {@code example}. */
    public record Vocabulary(int turn, String said, String used, String word, String example, String why) {
    }
}
