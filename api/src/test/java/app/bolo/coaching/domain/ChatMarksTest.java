package app.bolo.coaching.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Review (P1e): a mark matches by words, the way the Coach's quotes are accepted (ReplyCheck.quoteIn). */
class ChatMarksTest {

    static String at(String text, String words) {
        int[] r = ChatMarks.find(text, words);
        return r == null ? null : text.substring(r[0], r[1]);
    }

    @Test
    void punctuationInsideTheWordsStillMatches() {
        assertThat(at("Yesterday, I go to market with my brother.", "Yesterday I go to market")).isEqualTo("Yesterday, I go to market");
        assertThat(at("It is a well-known place.", "well known")).isEqualTo("well-known");
        assertThat(at("I said, um, I go there.", "I said um I go there")).isEqualTo("I said, um, I go there");
    }

    @Test
    void aSingleWordMatchesAsAWord() {
        assertThat(at("Every day is very good.", "very")).isEqualTo("very");
        assertThat(at("Every day.", "very")).isNull();
    }

    @Test
    void lettersThatChangeLengthWhenLowercasedDontShiftOffsets() {
        String text = "İstanbul is where i go to school";
        assertThat(at(text, "go to school")).isEqualTo("go to school");
    }
}
