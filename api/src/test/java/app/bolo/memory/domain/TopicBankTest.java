package app.bolo.memory.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** P3b (spec 2026-11-13, C): fresh ideas from a topic bank, never one of the recent topics. */
class TopicBankTest {

    @Test
    void everyGoalHasTwentySeedsAndSoDoesTheGeneralList() {
        for (String goal : List.of("work_meetings", "job_interviews", "study_abroad", "daily_confidence")) {
            assertThat(TopicBank.forGoal(goal)).hasSize(20).doesNotHaveDuplicates();
        }
        assertThat(TopicBank.forGoal(null)).hasSize(20);
        assertThat(TopicBank.forGoal("something_new")).isEqualTo(TopicBank.forGoal(null));
    }

    @Test
    void threeSeedsAndNeverARecentTopic() {
        String recent = TopicBank.forGoal("job_interviews").getFirst();
        Random rnd = new Random(7);
        for (int i = 0; i < 200; i++) {
            List<String> seeds = TopicBank.seeds("job_interviews", Set.of(recent), rnd, 3);
            assertThat(seeds).hasSize(3).doesNotHaveDuplicates().doesNotContain(recent);
        }
    }

    @Test
    void noRoutineChatter() {
        for (String goal : new String[]{"work_meetings", "job_interviews", "study_abroad", "daily_confidence", null}) {
            assertThat(TopicBank.forGoal(goal)).noneMatch(s -> s.toLowerCase().matches(".*(breakfast|what did you eat|weather|how was your day).*"));
        }
    }
}
