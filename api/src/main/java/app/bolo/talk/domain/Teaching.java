package app.bolo.talk.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The English teacher hat (founder 2026-11-16: "teach when it's worth it"): when Bolo may teach a better sentence, and
 * how the learner's retry is judged. Never the first learner turn; the next teach at least 3 learner turns after the
 * last (teach, retry, one ordinary turn); at most 4 a talk. One per talk; the talk's lock guards it.
 */
public final class Teaching {

    static final int MAX = 4;
    static final int GAP = 3;

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
        return pending == null && learnerTurn > 1 && taught < MAX && learnerTurn >= lastTurn + GAP;
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
        return "A smoother way to say that: \"" + better + "\" Try saying it.";
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
