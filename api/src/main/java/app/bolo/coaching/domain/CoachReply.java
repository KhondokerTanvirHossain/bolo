package app.bolo.coaching.domain;

import java.util.List;

/** What the Coach model returns (structured output); validated by {@link ReplyCheck} before anything is shown. */
/** {@code topic}: the Coach's 2–5 word name for the talk (PRG-8); may be missing in older replies. */
/** {@code standupTip} (SCN-2): only asked for in a Standup rehearsal; null otherwise. */
public record CoachReply(Strength strength, List<Upgrade> upgrades, String sentenceLevel, String vocabularyLevel, String topic,
                         StandupTip standupTip) {

    public CoachReply(Strength strength, List<Upgrade> upgrades, String sentenceLevel, String vocabularyLevel, String topic) {
        this(strength, upgrades, sentenceLevel, vocabularyLevel, topic, null);
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
}
