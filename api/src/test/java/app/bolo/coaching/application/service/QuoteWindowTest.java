package app.bolo.coaching.application.service;

import app.bolo.speech.application.port.in.ScoredWord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Review #5: a quote that can't be placed in a long turn gets no clip, never an unrelated 12 s. */
class QuoteWindowTest {

    static ScoredWord w(String word, long start, long end) {
        return new ScoredWord(word, start, end, 90, "None", List.of());
    }

    @Test
    void aPlacedQuoteIsItsOwnSpan() {
        var words = List.of(w("i", 1000, 1100), w("fix", 1200, 1500), w("bug", 1600, 1900));
        assertThat(CoachService.quoteWindow(words, 1000, "I fix bug", 20_000)).containsExactly(0L - 300 + 0, 900L + 300);
    }

    @Test
    void anUnplaceableQuoteInAShortTurnIsTheWholeTurn() {
        assertThat(CoachService.quoteWindow(List.of(w("five", 0, 300)), 0, "5 years", 8_000)).containsExactly(0L, 8_000L);
    }

    @Test
    void anUnplaceableQuoteInALongTurnHasNoClip() {
        assertThat(CoachService.quoteWindow(List.of(w("five", 0, 300)), 0, "5 years", 40_000)).isNull();
    }
}
