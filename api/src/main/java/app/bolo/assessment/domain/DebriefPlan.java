package app.bolo.assessment.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ASM-4 / US-3.5 for Coach v1: at most 3 fixes; at most 1 sound fix until the accuracy gate has been measured (only
 * the most frequent confident pattern); then up to 2 sentence upgrades; then a habit if there's room.
 */
public final class DebriefPlan {

    static final int MAX_FIXES = 3;
    static final int MAX_SOUND_FIXES = 1;
    static final int MAX_UPGRADES = 2;

    private DebriefPlan() {
    }

    public static List<FixChoice> choose(List<SoundIssue> sounds, int upgrades, Optional<Habit> habit) {
        List<FixChoice> plan = new ArrayList<>();
        sounds.stream().limit(MAX_SOUND_FIXES).forEach(s -> plan.add(new FixChoice.Sound(s)));
        for (int i = 0; i < Math.min(upgrades, MAX_UPGRADES) && plan.size() < MAX_FIXES; i++) plan.add(new FixChoice.Upgrade(i));
        if (plan.size() < MAX_FIXES) habit.ifPresent(h -> plan.add(new FixChoice.HabitFix(h)));
        return List.copyOf(plan);
    }

    /**
     * SCN-2 / ASM-4 (2026-11-15): with {@code standupTip}, the tip takes the last card's place when there are already 3
     * (the order is fixed, so the last is the lowest-ranked; the sound fix comes first and is never pushed out), or is
     * added when there is room.
     */
    public static List<FixChoice> choose(List<SoundIssue> sounds, int upgrades, Optional<Habit> habit, boolean standupTip) {
        List<FixChoice> plan = new ArrayList<>(choose(sounds, upgrades, habit));
        if (!standupTip) return List.copyOf(plan);
        if (plan.size() >= MAX_FIXES) plan.set(plan.size() - 1, new FixChoice.StandupTip());
        else plan.add(new FixChoice.StandupTip());
        return List.copyOf(plan);
    }

    /** ASM-6: under a minute of speech there isn't enough to judge. */
    public static boolean tooShort(FluencyStats stats, long minSpeechMs) {
        return stats.speechMs() < minSpeechMs;
    }
}
