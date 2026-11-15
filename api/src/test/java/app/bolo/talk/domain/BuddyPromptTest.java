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

    /** Review (P3c): Professional leans toward the learner's own goal, not "work" for everyone (study abroad too). */
    @Test
    void professionalLeansTowardTheirGoal() {
        assertThat(BuddyPrompt.moodStyle("professional")).contains("toward their goal").doesNotContain("their work");
    }

    // SCN-2 (2026-11-15): the Standup rehearsal.
    static String standup(String brief) {
        return BuddyPrompt.system("work_meetings", null, 5, brief, brief == null ? BuddyPrompt.Opening.FROM_GOAL : BuddyPrompt.Opening.FROM_NOTES,
                null, "standup");
    }

    @Test
    void standupMakesBoloAWarmManager() {
        String s = standup(null);
        assertThat(s).contains("manager").contains("yesterday").contains("today").contains("blockers").contains("Got it, carry on");
        assertThat(s).doesNotContain("what they did today").doesNotContain("loves a relaxed chat");
    }

    @Test
    void standupKeepsTheSafetyAndNoCorrectionRules() {
        assertThat(standup(null)).contains("Never correct their pronunciation").contains("If they bring up something harmful");
    }

    @Test
    void standupWithNoNotesAsksWhatTheyWorkOn() {
        assertThat(standup(null)).contains("what they work on");
        assertThat(standup("What you remember: they are a backend developer.")).doesNotContain("what they work on")
                .contains("backend developer");
    }

    @Test
    void freeTalkIsUnchanged() {
        assertThat(BuddyPrompt.system("job_interviews", "talking_to_my_boss", 5, "brief", BuddyPrompt.Opening.FROM_NOTES, "calm", "free"))
                .isEqualTo(BuddyPrompt.system("job_interviews", "talking_to_my_boss", 5, "brief", BuddyPrompt.Opening.FROM_NOTES, "calm"));
    }

    @Test
    void standupStartersAreForAStatusUpdate() {
        assertThat(app.bolo.talk.application.service.Starters.system(true)).contains("status update to a manager");
        assertThat(app.bolo.talk.application.service.Starters.system(false)).doesNotContain("manager");
    }

    /** Review I3: a brief with only fresh topic ideas (memory empty or off) means Bolo doesn't know their work. */
    @Test
    void standupWithOnlyTopicIdeasAsksWhatTheyWorkOnAndDropsTheIdeas() {
        String s = BuddyPrompt.system("work_meetings", null, 5, "Ideas if the talk needs a new direction: cricket; cooking\n",
                BuddyPrompt.Opening.FROM_GOAL, null, "standup");
        assertThat(s).contains("what they work on").doesNotContain("cricket").doesNotContain("Ideas if");
        String known = BuddyPrompt.system("work_meetings", null, 5, "What you remember about them from earlier talks:\n- A tester\n"
                + "Topics you've talked about recently (pick something else): x\nIdeas if the talk needs a new direction: cricket\n",
                BuddyPrompt.Opening.FROM_NOTES, null, "standup");
        assertThat(known).contains("A tester").doesNotContain("what they work on").doesNotContain("cricket").doesNotContain("Topics you've");
    }

    // Hats (2026-11-16).
    @Test
    void ieltsIsAnExaminerWithNoBandScore() {
        String ten = BuddyPrompt.system("study_abroad", null, 10, null, BuddyPrompt.Opening.FROM_GOAL, null, "ielts");
        assertThat(ten).contains("IELTS Speaking").contains("examiner").contains("Part 1").contains("Part 2").contains("Part 3")
                .contains("cue card").contains("Never give a band score");
        assertThat(ten).doesNotContain("Never correct their pronunciation, grammar or word choice, and never mention mistakes. If you didn't");
        String five = BuddyPrompt.system("study_abroad", null, 5, null, BuddyPrompt.Opening.FROM_GOAL, null, "ielts");
        assertThat(five).contains("Part 1").contains("Part 2").doesNotContain("Part 3");
    }

    @Test
    void ieltsIgnoresBolosNotesAndMood() {
        String s = BuddyPrompt.system("study_abroad", null, 10, "What you remember about them from earlier talks:\n- Loves cricket\n",
                BuddyPrompt.Opening.FROM_NOTES, "funny", "ielts");
        assertThat(s).doesNotContain("Loves cricket").doesNotContain("Your mood is funny");
    }

    @Test
    void teacherInvitesARealSituation() {
        String s = BuddyPrompt.system("work_meetings", null, 5, null, BuddyPrompt.Opening.FROM_GOAL, null, "teacher");
        assertThat(s).contains("English teacher").contains("real situation").contains("Never correct them yourself");
        assertThat(BuddyPrompt.judgedSystem(s, "Nailed it!")).contains("Start your reply with exactly \"Nailed it!\"");
    }
}
