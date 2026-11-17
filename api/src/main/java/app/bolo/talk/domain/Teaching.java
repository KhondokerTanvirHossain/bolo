package app.bolo.talk.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The English teacher hat (founder 2026-11-16: "teach when it's worth it"; 2026-11-18: "a little more aggressive"):
 * when Bolo may teach a better sentence, and how the learner's retry is judged. Never the first learner turn (it answers
 * "anything you'd like to learn today?"); the next teach may come right after the try (teach, try, teach); at most 6 a
 * talk in 5 minutes, 10 in 10 minutes. One per talk; the talk's lock guards it.
 */
public final class Teaching {

    static final int MAX = 6;
    static final int MAX_LONG = 10;
    static final int GAP = 2;

    /** What kind of better the check found (founder 2026-11-18: vocabulary, grammar and idioms). */
    public enum Kind {
        GRAMMAR("A smoother way to say that"), WORD("A stronger word for that"), PHRASE("A more natural way to say that"),
        IDIOM("An idiom for that");

        private final String intro;

        Kind(String intro) {
            this.intro = intro;
        }

        public String dbValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The check's kind; anything unknown is grammar. */
        public static Kind of(String value) {
            if (value != null) for (Kind k : values()) if (k.dbValue().equals(value.strip().toLowerCase(Locale.ROOT))) return k;
            return GRAMMAR;
        }
    }

    private final int max;

    public Teaching() {
        this(MAX);
    }

    private Teaching(int max) {
        this.max = max;
    }

    /** 6 teaching moments in a 5-minute talk, 10 in a 10-minute one. */
    public static Teaching forMinutes(int minutes) {
        return new Teaching(minutes >= 10 ? MAX_LONG : MAX);
    }

    /** The judgement of a retry: never a score, never "wrong". */
    public enum Result {
        NAILED("Nailed it!"), CLOSER("Closer! Nice."), GOOD_TRY("Good try.");

        private final String line;

        Result(String line) {
            this.line = line;
        }

        public String line() {
            return line;
        }

        public String dbValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private int taught;
    private int lastTurn = -100;
    private String pending;

    /** {@code learnerTurn}: 1-based, including the turn just finished. */
    public boolean mayTeach(int learnerTurn) {
        return pending == null && learnerTurn > 1 && taught < max && learnerTurn >= lastTurn + GAP;
    }

    public void taught(int learnerTurn, String better) {
        taught++;
        lastTurn = learnerTurn;
        pending = better;
    }

    /** The better sentence the next turn is a try at, if any. */
    public Optional<String> pending() {
        return Optional.ofNullable(pending);
    }

    public void drop() {
        pending = null;
    }

    /** What Bolo says when it teaches. */
    public static String line(String better) {
        return line(Kind.GRAMMAR, better);
    }

    /** The spoken line names the kind; the sentence always ends with punctuation so it doesn't run into "Try saying it". */
    public static String line(Kind kind, String better) {
        return kind.intro + ": \"" + ended(better) + "\" Try saying it.";
    }

    static String ended(String s) {
        String t = s == null ? "" : s.strip();
        return t.isEmpty() || ".!?".indexOf(t.charAt(t.length() - 1)) >= 0 ? t : t + ".";
    }

    /** The try's words against the better sentence, in order (longest common subsequence); repeats are ignored. */
    public static Result judge(String said, String better) {
        List<String> a = collapse(words(said)), b = words(better);
        if (b.isEmpty()) return Result.GOOD_TRY;
        int[][] l = new int[a.size() + 1][b.size() + 1];
        for (int i = a.size() - 1; i >= 0; i--) {
            for (int j = b.size() - 1; j >= 0; j--) {
                l[i][j] = a.get(i).equals(b.get(j)) ? l[i + 1][j + 1] + 1 : Math.max(l[i + 1][j], l[i][j + 1]);
            }
        }
        double r = (double) l[0][0] / b.size();
        return r >= 0.8 ? Result.NAILED : r >= 0.5 ? Result.CLOSER : Result.GOOD_TRY;
    }

    /** The better sentence's words the learner didn't say (shown highlighted). */
    public static List<String> changed(String said, String better) {
        Set<String> had = new HashSet<>(words(said));
        List<String> out = new ArrayList<>();
        for (String w : words(better)) if (!had.contains(w) && !out.contains(w)) out.add(w);
        return out;
    }

    static List<String> words(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String w : s.toLowerCase(Locale.ROOT).split("[^a-z']+")) if (!w.isBlank()) out.add(w);
        return out;
    }

    private static List<String> collapse(List<String> ws) {
        List<String> out = new ArrayList<>();
        for (String w : ws) if (out.isEmpty() || !out.getLast().equals(w)) out.add(w);
        return out;
    }
}
