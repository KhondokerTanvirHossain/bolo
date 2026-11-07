package app.bolo.talk.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TalkIntentTest {

    @Test
    void shortRequestsToRepeatOrSlowDown() {
        assertThat(TalkIntent.of("Sorry?")).isEqualTo(TalkIntent.REPEAT);
        assertThat(TalkIntent.of("Pardon")).isEqualTo(TalkIntent.REPEAT);
        assertThat(TalkIntent.of("Can you say that again?")).isEqualTo(TalkIntent.REPEAT);
        assertThat(TalkIntent.of("Repeat please.")).isEqualTo(TalkIntent.REPEAT);
        assertThat(TalkIntent.of("Slower please")).isEqualTo(TalkIntent.SLOWER);
        assertThat(TalkIntent.of("Can you speak slowly?")).isEqualTo(TalkIntent.SLOWER);
    }

    @Test
    void ordinaryAnswersAreNot() {
        assertThat(TalkIntent.of("I am sorry for being late today")).isEqualTo(TalkIntent.NONE);
        assertThat(TalkIntent.of("I work slowly but carefully on my code every day")).isEqualTo(TalkIntent.NONE);
        assertThat(TalkIntent.of("")).isEqualTo(TalkIntent.NONE);
    }
}
