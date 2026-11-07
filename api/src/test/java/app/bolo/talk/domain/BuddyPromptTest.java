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
}
