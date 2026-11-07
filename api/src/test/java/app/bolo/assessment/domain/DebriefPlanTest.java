package app.bolo.assessment.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DebriefPlanTest {

    static SoundIssue issue(SoundPattern p) {
        return new SoundIssue(p, List.of(new WordAt(1, "very", 0, 400, 30), new WordAt(3, "voice", 0, 400, 30)));
    }

    @Test
    void oneSoundFixThenUpgrades() {
        List<FixChoice> plan = DebriefPlan.choose(List.of(issue(SoundPattern.V_BH), issue(SoundPattern.TH_TD)), 2,
                Optional.of(new Habit(Habit.Kind.FILLER, "um", 7, 0)));
        assertThat(plan).containsExactly(new FixChoice.Sound(issue(SoundPattern.V_BH)), new FixChoice.Upgrade(0),
                new FixChoice.Upgrade(1));
    }

    @Test
    void withoutUpgradesTheHabitFills() {
        List<FixChoice> plan = DebriefPlan.choose(List.of(issue(SoundPattern.V_BH)), 0,
                Optional.of(new Habit(Habit.Kind.SHORT_ANSWERS, null, 0, 5.5)));
        assertThat(plan).hasSize(2);
        assertThat(plan.get(1)).isInstanceOf(FixChoice.HabitFix.class);
    }

    @Test
    void habitsFromStats() {
        assertThat(Habit.of(new FluencyStats(70_000, 200, 171, 0, Map.of("um", 6, "actually", 2), 12))).hasValueSatisfying(
                h -> assertThat(h.kind()).isEqualTo(Habit.Kind.FILLER));
        assertThat(Habit.of(new FluencyStats(70_000, 40, 34, 0, Map.of(), 5))).hasValueSatisfying(
                h -> assertThat(h.kind()).isEqualTo(Habit.Kind.SHORT_ANSWERS));
        assertThat(Habit.of(new FluencyStats(70_000, 200, 171, 0, Map.of("um", 2), 14))).isEmpty();
    }

    @Test
    void underAMinuteOfSpeechIsTooShort() {
        assertThat(DebriefPlan.tooShort(new FluencyStats(59_000, 100, 100, 0, Map.of(), 10), 60_000)).isTrue();
        assertThat(DebriefPlan.tooShort(new FluencyStats(61_000, 100, 100, 0, Map.of(), 10), 60_000)).isFalse();
    }
}
