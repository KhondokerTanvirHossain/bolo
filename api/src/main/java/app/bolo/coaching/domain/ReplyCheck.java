package app.bolo.coaching.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Guards against a model that invents or overreaches (US-3.4, US-4.1, US-4.4): a quote must really be in the
 * learner's turn, an upgrade keeps to 25 words and actually changes something, and nothing the learner reads uses the
 * words the design system bans.
 */
public final class ReplyCheck {

    static final int MAX_UPGRADE_WORDS = 25;
    static final Set<String> BANNED = Set.of("wrong", "bad", "error", "mistake", "mistakes", "fail", "failed", "incorrect",
            "poor", "phoneme", "schwa", "fricative", "labiodental", "accent");

    private ReplyCheck() {
    }

    public static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9' ]", " ").replaceAll("\\s+", " ").trim();
    }

    public static boolean quoteIn(String turnText, String quote) {
        String q = normalize(quote);
        return !q.isEmpty() && (" " + normalize(turnText) + " ").contains(" " + q + " ");
    }

    public static boolean upgradeOk(String said, String better) {
        String b = normalize(better);
        return !b.isEmpty() && b.split(" ").length <= MAX_UPGRADE_WORDS && !b.equals(normalize(said)) && clean(better);
    }

    public static boolean clean(String learnerFacing) {
        return Arrays.stream(normalize(learnerFacing).split(" ")).noneMatch(BANNED::contains);
    }

    /** Words in {@code better} that aren't in {@code said}, for highlighting (US-4.4). */
    public static List<String> changedWords(String said, String better) {
        // A learner repeats words ("to go to"); Set.of would throw on the duplicate.
        Set<String> before = Set.copyOf(Arrays.asList(normalize(said).split(" ")));
        List<String> changed = new ArrayList<>();
        for (String w : better.split("\\s+")) {
            String n = normalize(w);
            if (!n.isEmpty() && !before.contains(n)) changed.add(w.replaceAll("[^A-Za-z0-9']", ""));
        }
        return changed;
    }
}
