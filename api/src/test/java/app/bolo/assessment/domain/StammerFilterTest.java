package app.bolo.assessment.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static app.bolo.assessment.domain.Words.turn;
import static app.bolo.assessment.domain.Words.word;
import static org.assertj.core.api.Assertions.assertThat;

/** AX-8: for a learner who stammers, repeated and broken-off words are not judged for sounds. */
class StammerFilterTest {

    static List<String> kept(TurnScore t) {
        return StammerFilter.drop(List.of(t)).getFirst().words().stream().map(SpokenWord::word).toList();
    }

    @Test
    void dropsAWordRepeatedStraightAfterItself() {
        var t = turn(1, "I I want to to go", word("I", 0, 1, 90), word("I", 2, 3, 90), word("want", 4, 5, 90),
                word("to", 6, 7, 90), word("To", 8, 9, 90), word("go", 10, 11, 90));
        assertThat(kept(t)).containsExactly("I", "want", "To", "go");
    }

    @Test
    void dropsABrokenStartOfTheNextWord() {
        var t = turn(1, "w wa want it", word("w", 0, 1, 90), word("wa", 2, 3, 90), word("want", 4, 5, 90), word("it", 6, 7, 90));
        assertThat(kept(t)).containsExactly("want", "it");
    }

    @Test
    void keepsOrdinaryWordsAndTheTurnText() {
        var t = turn(1, "they think so", word("they", 0, 1, 90), word("think", 2, 3, 90), word("so", 4, 5, 90));
        assertThat(kept(t)).containsExactly("they", "think", "so");
        assertThat(StammerFilter.drop(List.of(t)).getFirst().text()).isEqualTo("they think so");
    }
}
