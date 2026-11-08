package app.bolo.coaching.application.service;

import app.bolo.coaching.domain.ReplyCheck;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailureWhereTest {

    /** A failed debrief's log names the class and line that threw — never the learner's words in the message. */
    @Test
    void namesTheExceptionAndTheFirstBoloFrame() {
        RuntimeException e = new IllegalArgumentException("duplicate element: secret words");
        e.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("java.util.ImmutableCollections$SetN", "<init>", "ImmutableCollections.java", 940),
                new StackTraceElement(ReplyCheck.class.getName(), "changedWords", "ReplyCheck.java", 43)});
        assertThat(CoachService.where(e)).isEqualTo("IllegalArgumentException at ReplyCheck.changedWords:43")
                .doesNotContain("secret");
    }

    @Test
    void withoutABoloFrameItNamesTheException() {
        RuntimeException e = new IllegalStateException("x");
        e.setStackTrace(new StackTraceElement[0]);
        assertThat(CoachService.where(e)).isEqualTo("IllegalStateException");
    }
}
