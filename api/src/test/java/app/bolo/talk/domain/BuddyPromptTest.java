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
        String system = BuddyPrompt.system("job_interviews", "interviews", 5, "What you remember about them:\n- Lives in Mirpur", BuddyPrompt.Opening.FOLLOW_UP);
        assertThat(system).contains("Lives in Mirpur");
        assertThat(system).doesNotContain("a real question about their situation");
    }

    @Test
    void noBriefMeansOpeningFromTheGoal() {
        assertThat(BuddyPrompt.system("job_interviews", "interviews", 5, null, BuddyPrompt.Opening.FROM_GOAL)).contains("a real question about their situation")
                .contains("what kind of job they're going for");
        assertThat(BuddyPrompt.system("job_interviews", "interviews", 5, "Ideas if the talk needs a new direction: x", BuddyPrompt.Opening.FROM_GOAL))
                .contains("a real question about their situation");
    }

    /** Review (P3b, Important): Bolo knows them but every follow-up is resting: no first-talk question. */
    @Test
    void knownButNoFollowUpOpensFromWhatBoloKnows() {
        String system = BuddyPrompt.system("job_interviews", "interviews", 5, "What you remember about them:\n- Applying for bank jobs",
                BuddyPrompt.Opening.FROM_NOTES);
        assertThat(system).contains("built on what you remember").doesNotContain("what kind of job they're going for")
                .doesNotContain("a real question about their situation");
    }

    /** P3c (SET-6, 2026-11-13): a mood adds one style line and keeps every rule. */
    @Test
    void eachMoodAddsItsStyleAndKeepsTheRules() {
        for (String mood : new String[]{"funny", "professional", "calm"}) {
            String system = BuddyPrompt.system("work_meetings", "speaking_up", 5, null, BuddyPrompt.Opening.FROM_GOAL, mood);
            assertThat(system).contains("Your mood is " + mood).contains("Never correct").contains("one open question")
                    .contains("never invent a life of your own");
            assertThat(system.toLowerCase()).doesNotContain("wrong").doesNotContain("error").doesNotContain("fail");
        }
    }

    @Test
    void friendlyIsTodaysPrompt() {
        assertThat(BuddyPrompt.system("work_meetings", "speaking_up", 5, "brief", BuddyPrompt.Opening.FROM_NOTES, "friendly"))
                .isEqualTo(BuddyPrompt.system("work_meetings", "speaking_up", 5, "brief", BuddyPrompt.Opening.FROM_NOTES));
        assertThat(BuddyPrompt.system("work_meetings", "speaking_up", 5, null, BuddyPrompt.Opening.FROM_GOAL, "romantic"))
                .doesNotContain("Your mood is");
    }

    @Test
    void theMoodComesBeforeTheBrief() {
        String system = BuddyPrompt.system("work_meetings", "speaking_up", 5, "What you remember about them: x", BuddyPrompt.Opening.FROM_NOTES, "calm");
        assertThat(system.indexOf("Your mood is calm")).isLessThan(system.indexOf("What you remember about them"));
    }
}
