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
        long speech = 0;
        int words = 0;
        int pauses = 0;
        Map<String, Integer> fillers = new LinkedHashMap<>();
        for (TurnScore t : turns) {
            String[] tokens = t.text().toLowerCase(Locale.ROOT).replaceAll("[^a-z' ]", " ").trim().split("\\s+");
            int count = t.text().isBlank() ? 0 : tokens.length;
            words += count;
            if (!t.words().isEmpty()) {
                speech += t.words().getLast().endMs() - t.words().getFirst().startMs();
                for (int i = 1; i < t.words().size(); i++) {
                    if (t.words().get(i).startMs() - t.words().get(i - 1).endMs() > LONG_PAUSE_MS) pauses++;
                }
            } else {
                speech += count * ESTIMATED_MS_PER_WORD;
            }
            for (int i = 0; i < count; i++) {
                if (SINGLE_FILLERS.contains(tokens[i])) fillers.merge(tokens[i], 1, Integer::sum);
                if (tokens[i].equals("you") && i + 1 < count && tokens[i + 1].equals("know")) fillers.merge("you know", 1, Integer::sum);
            }
        }
        int wpm = speech <= 0 ? 0 : (int) Math.round(words / (speech / 60_000.0));
        double average = turns.isEmpty() ? 0 : (double) words / turns.size();
        return new FluencyStats(speech, words, wpm, pauses, Map.copyOf(fillers), average);
    }

    public double longPausesPerMinute() {
        return speechMs <= 0 ? 0 : longPauses / (speechMs / 60_000.0);
    }
}
