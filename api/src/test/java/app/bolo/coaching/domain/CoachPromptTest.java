package app.bolo.coaching.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** AX-8: the Coach is told about a stammer only when the learner turned I stammer on. */
class CoachPromptTest {

    static final String LINE = "This learner stammers. Never choose a sentence, or suggest a better version, because of "
            + "repeated sounds or words, blocks or pauses, and never mention them.";

    @Test
    void theStammerLineOnlyWhenOn() {
        assertThat(CoachPrompt.system(true)).contains(LINE);
        assertThat(CoachPrompt.system(false)).doesNotContain("stammer");
        assertThat(CoachPrompt.system()).isEqualTo(CoachPrompt.system(false));
    }

    /** Founder 2026-11-18: Bangla turns are marked, so the Coach never "fixes" them (only Vocabulary may use one). */
    @Test
    void banglaTurnsAreMarked() {
        String t = CoachPrompt.transcript(java.util.List.of(new CoachPrompt.Line(true, 1, "আমার বস পরিকল্পনা বদলে দিয়েছে।"),
                new CoachPrompt.Line(false, 2, "In English, you can say..."), new CoachPrompt.Line(true, 3, "My boss changed the plan.")));
        assertThat(t).contains("Learner turn 1 (said in Bangla): আমার").contains("Learner turn 3: My boss");
        assertThat(CoachPrompt.system()).contains("said in Bangla");
    }
}
