package app.bolo.talk.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SentenceSplitterTest {

    @Test
    void joinsShortPiecesAndKeepsTheTailForFlush() {
        SentenceSplitter s = new SentenceSplitter();
        String text = "Oh nice. What was the bug doing? Tell me";
        List<String> out = new ArrayList<>();
        for (int i = 0; i < text.length(); i += 3) out.addAll(s.push(text.substring(i, Math.min(text.length(), i + 3))));
        assertThat(out).containsExactly("Oh nice. What was the bug doing?");
        assertThat(s.flush()).contains("Tell me");
        assertThat(s.flush()).isEmpty();
    }

    @Test
    void emitsEachLongSentenceAsSoonAsItEnds() {
        SentenceSplitter s = new SentenceSplitter();
        assertThat(s.push("That sounds like a busy day at work! ")).containsExactly("That sounds like a busy day at work!");
        assertThat(s.push("What happened next?")).isEmpty();
        assertThat(s.flush()).contains("What happened next?");
    }
}
