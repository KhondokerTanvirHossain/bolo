package app.bolo.coaching.domain;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Where a fix's words sit in what the learner said (DBF-6): character offsets, case-insensitive. */
public final class ChatMarks {

    private ChatMarks() {
    }

    /**
     * @param wholeWord a sound fix's single word must match as a word ("very", not "every"); an upgrade's phrase may
     *                  differ in its final punctuation
     * @return {start, end} or null when the words aren't there
     */
    public static int[] find(String text, String words, boolean wholeWord) {
        String w = words.trim();
        if (wholeWord) {
            Matcher m = Pattern.compile("(?<![\\p{L}\\p{N}'])" + Pattern.quote(w) + "(?![\\p{L}\\p{N}'])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text);
            return m.find() ? new int[]{m.start(), m.end()} : null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String phrase = w.toLowerCase(Locale.ROOT);
        int at = lower.indexOf(phrase);
        if (at < 0) {
            phrase = phrase.replaceAll("[.!?,;:]+$", "");
            at = phrase.isEmpty() ? -1 : lower.indexOf(phrase);
        }
        return at < 0 ? null : new int[]{at, at + phrase.length()};
    }
}
