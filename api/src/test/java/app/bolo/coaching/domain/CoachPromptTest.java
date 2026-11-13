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
}
