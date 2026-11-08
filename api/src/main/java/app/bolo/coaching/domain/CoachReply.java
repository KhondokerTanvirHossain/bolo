package app.bolo.coaching.domain;

import java.util.List;

/** What the Coach model returns (structured output); validated by {@link ReplyCheck} before anything is shown. */
/** {@code topic}: the Coach's 2–5 word name for the talk (PRG-8); may be missing in older replies. */
public record CoachReply(Strength strength, List<Upgrade> upgrades, String sentenceLevel, String vocabularyLevel, String topic) {

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
}
