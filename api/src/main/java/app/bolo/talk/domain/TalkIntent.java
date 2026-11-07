package app.bolo.talk.domain;

import java.util.List;
import java.util.Locale;

/** TLK-13: short requests Java answers without the LLM — repeat Bolo's last line, or slow Bolo down for the talk. */
public enum TalkIntent {
    NONE, REPEAT, SLOWER;

    private static final int MAX_WORDS = 6;
    private static final List<String> SLOWER_WORDS = List.of("slower", "slowly", "slow down");
    private static final List<String> REPEAT_WORDS = List.of("sorry", "pardon", "repeat", "say that again", "say it again",
            "come again", "what did you say", "once more");

    public static TalkIntent of(String text) {
        String t = " " + (text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z' ]", " ").replaceAll("\\s+", " ").trim()) + " ";
        if (t.isBlank() || t.trim().split(" ").length > MAX_WORDS) return NONE;
        if (SLOWER_WORDS.stream().anyMatch(w -> t.contains(" " + w + " "))) return SLOWER;
        if (REPEAT_WORDS.stream().anyMatch(w -> t.contains(" " + w + " "))) return REPEAT;
        return NONE;
    }
}
