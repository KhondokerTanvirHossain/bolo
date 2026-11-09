package app.bolo.coaching.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where a fix's words sit in what the learner said (DBF-6): matched word by word, the same way the Coach's quotes are
 * accepted ({@link ReplyCheck#quoteIn}), so punctuation inside the words ("Yesterday, I go") doesn't lose the mark.
 * Offsets come from the original text, so letters that change length when lowercased can't shift them.
 */
public final class ChatMarks {

    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9']+");

    private ChatMarks() {
    }

    /** @return {start, end} from the first word to the end of the last, or null when the words aren't there */
    public static int[] find(String text, String words) {
        String q = ReplyCheck.normalize(words);
        if (q.isEmpty()) return null;
        String[] want = q.split(" ");
        record Word(String w, int start, int end) {
        }
        List<Word> got = new ArrayList<>();
        Matcher m = WORD.matcher(text);
        while (m.find()) got.add(new Word(m.group().toLowerCase(Locale.ROOT), m.start(), m.end()));
        outer:
        for (int i = 0; i + want.length <= got.size(); i++) {
            for (int j = 0; j < want.length; j++) if (!got.get(i + j).w().equals(want[j])) continue outer;
            return new int[]{got.get(i).start(), got.get(i + want.length - 1).end()};
        }
        return null;
    }
}
