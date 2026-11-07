package app.bolo.assessment.domain;

/** One fix card the debrief will show. Upgrade points at the Coach's n-th validated sentence upgrade. */
public sealed interface FixChoice {

    record Sound(SoundIssue issue) implements FixChoice {
    }

    record Upgrade(int index) implements FixChoice {
    }

    record HabitFix(Habit habit) implements FixChoice {
    }
}
