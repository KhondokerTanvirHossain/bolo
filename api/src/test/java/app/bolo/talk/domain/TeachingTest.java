package app.bolo.talk.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The English teacher hat (2026-11-16): when Bolo teaches and how a retry is judged. */
class TeachingTest {

    /** Founder 2026-11-18 ("a little more aggressive"): it may teach again right after the try. */
    @Test
    void neverTheFirstTurnAndAgainRightAfterTheTry() {
        Teaching t = new Teaching();
        assertThat(t.mayTeach(1)).isFalse();
        assertThat(t.mayTeach(2)).isTrue();
        t.taught(2, "I need to tell the client.");
        assertThat(t.mayTeach(3)).as("the try turn").isFalse();
        t.drop();
        assertThat(t.mayTeach(3)).as("the try turn is judged, not taught").isFalse();
        assertThat(t.mayTeach(4)).isTrue();
    }

    @Test
    void atMostSixInAFiveMinuteTalk() {
        Teaching t = Teaching.forMinutes(5);
        for (int turn = 2; turn <= 12; turn += 2) {
            assertThat(t.mayTeach(turn)).as("turn " + turn).isTrue();
            t.taught(turn, "x y z");
            t.drop();
        }
        assertThat(t.mayTeach(14)).isFalse();
    }

    @Test
    void atMostTenInATenMinuteTalk() {
        Teaching t = Teaching.forMinutes(10);
        for (int turn = 2; turn <= 20; turn += 2) {
            assertThat(t.mayTeach(turn)).as("turn " + turn).isTrue();
            t.taught(turn, "x y z");
            t.drop();
        }
        assertThat(t.mayTeach(22)).isFalse();
    }

    @Test
    void aPendingRetryIsHeldUntilDropped() {
        Teaching t = new Teaching();
        t.taught(2, "I need to tell the client.");
        assertThat(t.pending()).contains("I need to tell the client.");
        t.drop();
        assertThat(t.pending()).isEmpty();
    }

    @Test
    void judgingComparesTheWordsInOrder() {
        String better = "I need to tell the client the release is delayed.";
        assertThat(Teaching.judge("I need to tell the client the release is delayed", better)).isEqualTo(Teaching.Result.NAILED);
        assertThat(Teaching.judge("I need to tell client release delayed", better)).isEqualTo(Teaching.Result.CLOSER);
        assertThat(Teaching.judge("the client is waiting", better)).isEqualTo(Teaching.Result.GOOD_TRY);
    }

    @Test
    void repeatedWordsDontCountAgainstTheTry() {
        assertThat(Teaching.judge("I I need need to to tell the the client", "I need to tell the client.")).isEqualTo(Teaching.Result.NAILED);
    }

    @Test
    void theLinesAreKind() {
        assertThat(Teaching.Result.NAILED.line()).isEqualTo("Nailed it!");
        assertThat(Teaching.Result.CLOSER.line()).isEqualTo("Closer! Nice.");
        assertThat(Teaching.Result.GOOD_TRY.line()).isEqualTo("Good try.");
        assertThat(Teaching.Result.GOOD_TRY.dbValue()).isEqualTo("good_try");
    }

    @Test
    void theChangedWordsAreTheNewOnes() {
        assertThat(Teaching.changed("I have to tell client release is delay", "I need to tell the client the release is delayed."))
                .containsExactly("need", "the", "delayed");
    }

    @Test
    void theTeachLine() {
        assertThat(Teaching.line("I need to tell the client.")).isEqualTo("A smoother way to say that: \"I need to tell the client.\" Try saying it.");
    }

    /** Founder 2026-11-18: vocabulary, grammar and idioms; the line names the kind, and the sentence always ends. */
    @Test
    void theLineNamesTheKindAndEndsTheSentence() {
        assertThat(Teaching.line(Teaching.Kind.WORD, "The traffic jam was huge today"))
                .isEqualTo("A stronger word for that: \"The traffic jam was huge today.\" Try saying it.");
        assertThat(Teaching.line(Teaching.Kind.PHRASE, "Could you give me a minute?"))
                .isEqualTo("A more natural way to say that: \"Could you give me a minute?\" Try saying it.");
        assertThat(Teaching.line(Teaching.Kind.IDIOM, "We aren't on the same page."))
                .isEqualTo("An idiom for that: \"We aren't on the same page.\" Try saying it.");
        assertThat(Teaching.line(Teaching.Kind.GRAMMAR, "I went home")).isEqualTo("A smoother way to say that: \"I went home.\" Try saying it.");
    }

    @Test
    void anUnknownKindIsGrammar() {
        assertThat(Teaching.Kind.of("idiom")).isEqualTo(Teaching.Kind.IDIOM);
        assertThat(Teaching.Kind.of("word")).isEqualTo(Teaching.Kind.WORD);
        assertThat(Teaching.Kind.of("slang")).isEqualTo(Teaching.Kind.GRAMMAR);
        assertThat(Teaching.Kind.of(null)).isEqualTo(Teaching.Kind.GRAMMAR);
        assertThat(Teaching.Kind.IDIOM.dbValue()).isEqualTo("idiom");
    }
}
