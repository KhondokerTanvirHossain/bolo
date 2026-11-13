package app.bolo.memory.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What Bolo remembers about one learner (spec 2026-11-13 "Bolo knows you"). Only what the learner said about themselves.
 * {@code recent}: the last talk topics, newest first; never shown, they only keep Bolo from repeating itself.
 */
public record MemoryNote(List<Line> about, List<MemoryThread> threads, List<Line> interests, List<String> recent) {

    public static final int MAX_ABOUT = 12;
    public static final int MAX_THREADS = 8;
    public static final int MAX_INTERESTS = 8;
    public static final int MAX_RECENT = 6;
    public static final int MAX_CHARS = 120;
    /** A thread falls away this long after it was last mentioned. */
    public static final int DROP_AFTER_DAYS = 30;
    /** A thread that opened a talk rests for the next REST_TALKS talks and REST_DAYS days, whichever is later. */
    public static final int REST_TALKS = 2;
    public static final Duration REST_DAYS = Duration.ofDays(3);

    public MemoryNote {
        about = about == null ? List.of() : List.copyOf(about);
        threads = threads == null ? List.of() : List.copyOf(threads);
        interests = interests == null ? List.of() : List.copyOf(interests);
        recent = recent == null ? List.of() : List.copyOf(recent);
    }

    public static MemoryNote empty() {
        return new MemoryNote(List.of(), List.of(), List.of(), List.of());
    }

    /** Nothing the learner would see (recent topics alone don't count). */
    public boolean isEmpty() {
        return about.isEmpty() && threads.isEmpty() && interests.isEmpty();
    }

    /** The caps and the 120-character trim, whatever the model sent; threads not mentioned for 30 days drop. */
    public MemoryNote capped(LocalDate today) {
        LocalDate oldest = today.minusDays(DROP_AFTER_DAYS);
        return new MemoryNote(
                lines(about, MAX_ABOUT),
                threads.stream().filter(t -> t.text() != null && !t.text().isBlank())
                        .filter(t -> t.said() == null || !t.said().isBefore(oldest))
                        .map(t -> t.withText(trim(t.text()))).limit(MAX_THREADS).toList(),
                lines(interests, MAX_INTERESTS),
                recent.stream().filter(r -> r != null && !r.isBlank()).map(MemoryNote::trim).limit(MAX_RECENT).toList());
    }

    /**
     * The one follow-up to open a talk with, or empty. Order: a plan whose due date has passed, a worry, practice, any
     * other plan; within each, the most recently mentioned. Threads that opened a talk recently are resting.
     */
    public Optional<MemoryThread> opener(LocalDate today, Instant now, int talkNumber) {
        return threads.stream()
                .filter(t -> !resting(t, now, talkNumber))
                .min(Comparator.comparingInt((MemoryThread t) -> rank(t, today))
                        .thenComparing(MemoryThread::said, Comparator.nullsLast(Comparator.reverseOrder())));
    }

    public MemoryNote withOpened(String threadId, Instant now, int talkNumber) {
        return new MemoryNote(about, threads.stream()
                .map(t -> t.id().equals(threadId) ? t.opened(now, talkNumber) : t).toList(), interests, recent);
    }

    public MemoryNote without(String lineId) {
        return new MemoryNote(about.stream().filter(l -> !l.id().equals(lineId)).toList(),
                threads.stream().filter(t -> !t.id().equals(lineId)).toList(),
                interests.stream().filter(l -> !l.id().equals(lineId)).toList(), recent);
    }

    public Set<String> ids() {
        Set<String> ids = new HashSet<>();
        about.forEach(l -> ids.add(l.id()));
        threads.forEach(t -> ids.add(t.id()));
        interests.forEach(l -> ids.add(l.id()));
        return ids;
    }

    /** Drops lines that were in {@code oldIds} but are gone from {@code existingIds}: removed while this note was made. */
    public MemoryNote keepOnly(Set<String> existingIds, Set<String> oldIds) {
        Predicate<String> keep = id -> !oldIds.contains(id) || existingIds.contains(id);
        return new MemoryNote(about.stream().filter(l -> keep.test(l.id())).toList(),
                threads.stream().filter(t -> keep.test(t.id())).toList(),
                interests.stream().filter(l -> keep.test(l.id())).toList(), recent);
    }

    /** Opener marks as they are in {@code current} (a talk may have opened with a thread while this note was made). */
    public MemoryNote withMarksFrom(MemoryNote current) {
        java.util.Map<String, MemoryThread> byId = new java.util.HashMap<>();
        current.threads().forEach(t -> byId.put(t.id(), t));
        return new MemoryNote(about, threads.stream().map(t -> {
            MemoryThread c = byId.get(t.id());
            return c == null || c.openedTalk() == null ? t : t.opened(c.openedAt(), c.openedTalk());
        }).toList(), interests, recent);
    }

    static boolean resting(MemoryThread t, Instant now, int talkNumber) {
        if (t.openedTalk() == null) return false;
        if (talkNumber - t.openedTalk() <= REST_TALKS) return true;
        return t.openedAt() != null && Duration.between(t.openedAt(), now).compareTo(REST_DAYS) < 0;
    }

    static int rank(MemoryThread t, LocalDate today) {
        return switch (t.kind()) {
            case PLAN -> t.due() != null && t.due().isBefore(today) ? 0 : 3;
            case WORRY -> 1;
            case PREP -> 2;
        };
    }

    private static List<Line> lines(List<Line> in, int max) {
        return in.stream().filter(l -> l.text() != null && !l.text().isBlank())
                .map(l -> new Line(l.id(), trim(l.text()))).limit(max).toList();
    }

    static String trim(String s) {
        String t = s.strip().replaceAll("\\s+", " ");
        return t.length() <= MAX_CHARS ? t : t.substring(0, MAX_CHARS - 1).stripTrailing() + "…";
    }
}
