package app.bolo.memory.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;

/**
 * How a free talk opens when Bolo knows some history (founder 2026-11-18): one offer of three things — pick up the last
 * one ({@code last}: the follow-up thread when one is due, else the newest topic), something else from their history
 * ({@code other}, may be null), or a curious question ({@code curious}). Never more than three: heard aloud, a longer list
 * is lost.
 */
public record OpeningChoice(String last, String other, String curious) {

    public static Optional<OpeningChoice> of(MemoryNote note, MemoryThread opener, Random rnd) {
        if (note == null) return Optional.empty();
        String last = opener != null ? opener.text() : note.recent().isEmpty() ? null : note.recent().getFirst();
        if (last == null || last.isBlank()) return Optional.empty();
        String lastKey = key(last);
        List<String> others = new ArrayList<>();
        note.recent().forEach(r -> add(others, r, lastKey));
        note.interests().forEach(l -> add(others, l.text(), lastKey));
        note.threads().stream().filter(t -> opener == null || !t.id().equals(opener.id())).forEach(t -> add(others, t.text(), lastKey));
        String other = others.isEmpty() ? null : others.get(rnd.nextInt(others.size()));
        List<String> curious = TopicBank.curious();
        return Optional.of(new OpeningChoice(last, other, curious.get(rnd.nextInt(curious.size()))));
    }

    private static void add(List<String> to, String s, String lastKey) {
        if (s == null || s.isBlank() || key(s).equals(lastKey) || to.stream().anyMatch(x -> key(x).equals(key(s)))) return;
        to.add(s.strip());
    }

    private static String key(String s) {
        return s.strip().toLowerCase(Locale.ROOT);
    }
}
