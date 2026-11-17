package app.bolo.assessment.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ASM-3 fluency: speech time, words per minute, pauses over 1.2 s inside a turn, and fillers. "like" isn't counted:
 * as a verb it's too common to call a filler from text alone.
 */
public record FluencyStats(long speechMs, int words, int wordsPerMinute, int longPauses, Map<String, Integer> fillers,
                           double averageWordsPerTurn) {

    static final long LONG_PAUSE_MS = 1200;
    static final long ESTIMATED_MS_PER_WORD = 350;
    static final List<String> SINGLE_FILLERS = List.of("um", "uh", "erm", "actually", "basically");

    public static FluencyStats of(List<TurnScore> turns) {
        return of(turns, false);
    }

    /**
     * AX-8: with {@code stammer}, gaps between words longer than the long-pause cutoff (blocks) are left out of the
     * words-per-minute time and no long pauses are counted; the speech time itself is unchanged.
     */
    public static FluencyStats of(List<TurnScore> turns, boolean stammer) {
        long speech = 0;
        long blocks = 0;
        int words = 0;
        int pauses = 0;
        Map<String, Integer> fillers = new LinkedHashMap<>();
        int counted = 0;
        for (TurnScore t : turns) {
            // Review 2026-11-18: a turn said in Bangla isn't English speech: left out, never a "short answer".
            if (app.bolo.common.text.Bangla.is(t.text())) continue;
            counted++;
            String[] tokens = t.text().toLowerCase(Locale.ROOT).replaceAll("[^a-z' ]", " ").trim().split("\\s+");
            int count = t.text().isBlank() ? 0 : tokens.length;
            words += count;
            if (!t.words().isEmpty()) {
                speech += t.words().getLast().endMs() - t.words().getFirst().startMs();
                for (int i = 1; i < t.words().size(); i++) {
                    long gap = t.words().get(i).startMs() - t.words().get(i - 1).endMs();
                    if (gap > LONG_PAUSE_MS) {
                        if (stammer) blocks += gap;
                        else pauses++;
                    }
                }
            } else {
                speech += count * ESTIMATED_MS_PER_WORD;
            }
            for (int i = 0; i < count; i++) {
                if (SINGLE_FILLERS.contains(tokens[i])) fillers.merge(tokens[i], 1, Integer::sum);
                if (tokens[i].equals("you") && i + 1 < count && tokens[i + 1].equals("know")) fillers.merge("you know", 1, Integer::sum);
            }
        }
        long timed = speech - blocks;
        int wpm = timed <= 0 ? 0 : (int) Math.round(words / (timed / 60_000.0));
        double average = counted == 0 ? 0 : (double) words / counted;
        return new FluencyStats(speech, words, wpm, pauses, Map.copyOf(fillers), average);
    }

    public double longPausesPerMinute() {
        return speechMs <= 0 ? 0 : longPauses / (speechMs / 60_000.0);
    }
}
