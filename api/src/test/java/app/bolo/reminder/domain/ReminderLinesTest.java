package app.bolo.reminder.domain;

import app.bolo.reminder.domain.ReminderRules.Kind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderLinesTest {

    @Test
    void privateAlwaysTheShortLine() {
        assertThat(ReminderLines.text(Kind.NORMAL, true, "Bolo wants to know how the demo went.", 2)).isEqualTo("Time for today's talk.");
    }

    @Test
    void storyLineWhenThereIsOne() {
        assertThat(ReminderLines.text(Kind.NORMAL, false, "Bolo wants to know how the demo went.", 1)).isEqualTo("Bolo wants to know how the demo went.");
    }

    @Test
    void plainLinesRotate() {
        assertThat(ReminderLines.text(Kind.NORMAL, false, null, 0)).isEqualTo("Time for today's talk.");
        assertThat(ReminderLines.text(Kind.NORMAL, false, null, 1)).isEqualTo("Bolo has a question for you today.");
        assertThat(ReminderLines.text(Kind.NORMAL, false, null, 2)).isEqualTo("Bolo is on the hilltop. Got 5 minutes?");
        assertThat(ReminderLines.text(Kind.NORMAL, false, null, 3)).isEqualTo("Time for today's talk.");
    }

    @Test
    void talksBackAndComebackExactCopy() {
        assertThat(ReminderLines.text(Kind.TALKS_BACK, false, "x", 0)).isEqualTo("Your 2 free talks are back.");
        assertThat(ReminderLines.text(Kind.COMEBACK, true, "x", 0)).isEqualTo("We're here whenever you're ready.");
    }

    @Test
    void validRejectsLongEmojiAndBannedWords() {
        assertThat(ReminderLines.valid("Bolo wants to know how the demo went.")).isTrue();
        assertThat(ReminderLines.valid("B".repeat(81))).isFalse();
        assertThat(ReminderLines.valid("B".repeat(80))).isTrue();
        assertThat(ReminderLines.valid("Bolo misses you 🙂")).isFalse();
        assertThat(ReminderLines.valid("Bolo wants to hear your English")).isFalse();
        assertThat(ReminderLines.valid("Time for your lesson")).isFalse();
        assertThat(ReminderLines.valid("Bolo wants you to practise")).isFalse();
        assertThat(ReminderLines.valid("Two lines\nhere")).isFalse();
        assertThat(ReminderLines.valid("  ")).isFalse();
        assertThat(ReminderLines.valid(null)).isFalse();
    }

    @Test
    void slotNames() {
        assertThat(ReminderLines.slot("08:00")).isEqualTo("morning");
        assertThat(ReminderLines.slot("13:30")).isEqualTo("lunch");
        assertThat(ReminderLines.slot("18:30")).isEqualTo("evening");
        assertThat(ReminderLines.slot("22:00")).isEqualTo("night");
        assertThat(ReminderLines.slot("07:15")).isEqualTo("exact");
    }
}
