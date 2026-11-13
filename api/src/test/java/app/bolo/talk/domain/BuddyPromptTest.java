package app.bolo.talk.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BuddyPromptTest {

    @Test
    void theSystemPromptCarriesTheRulesAndTheLearnersGoal() {
        String system = BuddyPrompt.system("job_interviews", "talking_to_my_boss", 5);
        assertThat(system).contains("Never correct").contains("one open question").contains("job interviews")
                .contains("talking to my boss").contains("5 minutes");
        assertThat(system.toLowerCase().split("mistake", -1)).hasSize(2);
        assertThat(system.toLowerCase()).doesNotContain("wrong").doesNotContain("error").doesNotContain("fail");
    }

    @Test
    void theToneIsNotASetting() {
        assertThat(BuddyPrompt.system(null, null, 5)).doesNotContain("bus").contains("don't invent a setting");
    }

    @Test
    void worksWithoutAProfile() {
        assertThat(BuddyPrompt.system(null, null, 5)).doesNotContain("null").contains("everyday");
    }

    @Test
    void theConversationAlwaysStartsAndEndsWithTheLearner() {
        List<Exchange> history = List.of(new Exchange(Exchange.Speaker.BUDDY, "Hi! What did you do today?"),
                new Exchange(Exchange.Speaker.LEARNER, "I went to work."));
        List<Exchange> messages = BuddyPrompt.messages(history);
        assertThat(messages.getFirst().speaker()).isEqualTo(Exchange.Speaker.LEARNER);
        assertThat(messages.getLast().speaker()).isEqualTo(Exchange.Speaker.LEARNER);
        assertThat(BuddyPrompt.messages(List.of())).hasSize(1);
    }

    @Test
    void wrapUpClosesWithoutANewQuestion() {
        String system = BuddyPrompt.wrapUpSystem(BuddyPrompt.system(null, null, 5));
        assertThat(system).contains("time is up").contains("no new question");
    }

    /** P3b (spec 2026-11-13): interesting talks, always; no routine chatter; Bolo has opinions but no invented life. */
    @Test
    void theRulesForInterestingTalksAreAlwaysThere() {
        String system = BuddyPrompt.system("work_meetings", "speaking_up", 5);
        assertThat(system).contains("stories, opinions, small what-ifs and choices").contains("Never ask what they ate")
                .contains("never invent a life of your own").contains("follow them fully");
        assertThat(system.toLowerCase()).doesNotContain("wrong").doesNotContain("error").doesNotContain("fail");
    }

    @Test
    void theBriefIsAppended() {
        String system = BuddyPrompt.system("job_interviews", "interviews", 5, "What you remember about them:\n- Lives in Mirpur", true);
        assertThat(system).contains("Lives in Mirpur");
        assertThat(system).doesNotContain("a real question about their situation");
    }

    @Test
    void noBriefMeansOpeningFromTheGoal() {
        assertThat(BuddyPrompt.system("job_interviews", "interviews", 5, null, false)).contains("a real question about their situation")
                .contains("what kind of job they're going for");
        assertThat(BuddyPrompt.system("job_interviews", "interviews", 5, "Ideas if the talk needs a new direction: x", false))
                .contains("a real question about their situation");
    }
}
