package app.bolo.talk.domain;

import java.util.Locale;
import java.util.Set;

/**
 * TLK-13: short requests Java answers without the LLM — repeat Bolo's last line, or slow Bolo down for the talk.
 * The whole utterance must be the request (after an optional "sorry"/"excuse me" and "please"): "Sorry I am late" and
 * "I walk slowly" are answers, and so is anything with crisis words in it.
 */
public enum TalkIntent {
    NONE, REPEAT, SLOWER;

    private static final Set<String> REPEAT_PHRASES = Set.of("", "what", "pardon", "repeat", "repeat that", "say that again",
            "say it again", "come again", "once more", "one more time", "again", "what did you say", "can you repeat",
            "can you repeat that", "could you repeat that", "can you say that again", "could you say that again",
            "i didn't get that", "i did not get that", "i didn't catch that");
    private static final Set<String> SLOWER_PHRASES = Set.of("slower", "slow down", "speak slowly", "speak slower",
            "more slowly", "a bit slower", "a little slower", "can you speak slowly", "can you speak slower",
            "could you speak slowly", "could you speak slower", "can you slow down", "could you slow down");

    public static TalkIntent of(String text) {
        String t = text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z' ]", " ").replaceAll("\\s+", " ").trim();
        if (t.isEmpty() || CrisisScreen.mayBeCrisis(t)) return NONE;
        boolean apology = t.startsWith("sorry") || t.startsWith("excuse me");
        String rest = t.replaceFirst("^(sorry|excuse me)\\b", "").replaceAll("\\bplease\\b", " ").replaceAll("\\s+", " ").trim();
        if (SLOWER_PHRASES.contains(rest)) return SLOWER;
        if (REPEAT_PHRASES.contains(rest) && (apology || !rest.isEmpty())) return REPEAT;
        return NONE;
    }
}
