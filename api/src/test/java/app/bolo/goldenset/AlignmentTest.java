package app.bolo.goldenset;

import app.bolo.assessment.domain.SpokenWord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AlignmentTest {

    static SpokenWord w(String word, String error) {
        return new SpokenWord(word, 0, 0, 90, error, List.of());
    }

    @Test
    void punctuationAndCaseDoNotMatter() {
        assertThat(Alignment.tokens("Please call Stella. Don't wait!")).containsExactly("please", "call", "stella", "don't", "wait");
        assertThat(Alignment.align(List.of(w("Stella", "None")), List.of("please", "call", "stella"))).isEqualTo(Map.of(0, 2));
    }

    @Test
    void anInsertionTakesNoIndex() {
        var words = List.of(w("i", "Insertion"), w("school", "None"));
        assertThat(Alignment.align(words, List.of("school"))).isEqualTo(Map.of(1, 0));
    }

    @Test
    void anOmissionTakesItsIndex() {
        var words = List.of(w("a", "None"), w("small", "Omission"), w("school", "None"));
        assertThat(Alignment.align(words, List.of("a", "small", "school"))).isEqualTo(Map.of(0, 0, 1, 1, 2, 2));
    }

    @Test
    void anUnmatchedWordIsSkippedNotShifted() {
        var words = List.of(w("a", "None"), w("smoll", "None"), w("school", "None"));
        assertThat(Alignment.align(words, List.of("a", "small", "school"))).isEqualTo(Map.of(0, 0, 2, 2));
    }
}
