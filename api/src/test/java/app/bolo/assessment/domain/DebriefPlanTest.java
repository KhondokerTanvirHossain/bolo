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

    // SCN-2 / ASM-4 (2026-11-15): one standup tip, in place of the last card, never more than 3.
    @Test
    void aStandupTipReplacesTheLastOfThree() {
        assertThat(DebriefPlan.choose(List.of(issue(SoundPattern.V_BH)), 2, Optional.empty(), true))
                .containsExactly(new FixChoice.Sound(issue(SoundPattern.V_BH)), new FixChoice.Upgrade(0), new FixChoice.StandupTip());
    }

    @Test
    void aStandupTipIsAddedWhenThereIsRoom() {
        assertThat(DebriefPlan.choose(List.of(issue(SoundPattern.V_BH)), 0, Optional.empty(), true))
                .containsExactly(new FixChoice.Sound(issue(SoundPattern.V_BH)), new FixChoice.StandupTip());
    }

    @Test
    void theSoundFixIsNeverPushedOut() {
        List<FixChoice> plan = DebriefPlan.choose(List.of(issue(SoundPattern.V_BH)), 2,
                Optional.of(new Habit(Habit.Kind.FILLER, "um", 7, 0)), true);
        assertThat(plan).hasSize(3).first().isEqualTo(new FixChoice.Sound(issue(SoundPattern.V_BH)));
        assertThat(plan.getLast()).isEqualTo(new FixChoice.StandupTip());
    }

    @Test
    void noTipNoChange() {
        assertThat(DebriefPlan.choose(List.of(issue(SoundPattern.V_BH)), 2, Optional.empty(), false))
                .isEqualTo(DebriefPlan.choose(List.of(issue(SoundPattern.V_BH)), 2, Optional.empty()));
    }

    // Founder 2026-11-18: Grammar and Vocabulary cards after the sound fix; Say it better drops to one; at most 5.
    @Test
    void grammarAndVocabularyFollowTheSoundFixAndUpgradesDropToOne() {
        assertThat(DebriefPlan.choose(List.of(issue(SoundPattern.V_BH)), 2, Optional.of(new Habit(Habit.Kind.FILLER, "um", 7, 0)), true, true, false))
                .containsExactly(new FixChoice.Sound(issue(SoundPattern.V_BH)), new FixChoice.Grammar(), new FixChoice.Vocabulary(),
                        new FixChoice.Upgrade(0), new FixChoice.HabitFix(new Habit(Habit.Kind.FILLER, "um", 7, 0)));
    }

    @Test
    void onlyGrammar() {
        assertThat(DebriefPlan.choose(List.of(), 2, Optional.empty(), true, false, false))
                .containsExactly(new FixChoice.Grammar(), new FixChoice.Upgrade(0));
    }

    @Test
    void withATipStillAtMostFive() {
        List<FixChoice> plan = DebriefPlan.choose(List.of(issue(SoundPattern.V_BH)), 2, Optional.of(new Habit(Habit.Kind.FILLER, "um", 7, 0)),
                true, true, true);
        assertThat(plan).hasSize(5).containsExactly(new FixChoice.Sound(issue(SoundPattern.V_BH)), new FixChoice.Grammar(),
                new FixChoice.Vocabulary(), new FixChoice.Upgrade(0), new FixChoice.StandupTip());
    }

    @Test
    void neitherIsTodaysPlan() {
        assertThat(DebriefPlan.choose(List.of(issue(SoundPattern.V_BH)), 2, Optional.empty(), false, false, true))
                .isEqualTo(DebriefPlan.choose(List.of(issue(SoundPattern.V_BH)), 2, Optional.empty(), true));
    }
}
