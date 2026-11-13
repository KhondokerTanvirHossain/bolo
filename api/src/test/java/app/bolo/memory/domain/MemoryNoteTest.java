package app.bolo.memory.domain;

import app.bolo.memory.domain.MemoryThread.Kind;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** P3b (spec 2026-11-13 "Bolo knows you"): the note's caps, the 30-day drop and how the opener is picked. */
class MemoryNoteTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
    static final Instant NOW = Instant.parse("2026-11-13T06:00:00Z");

    static MemoryThread thread(String id, Kind kind, String said, String due) {
        return new MemoryThread(id, kind, "thread " + id, LocalDate.parse(said), due == null ? null : LocalDate.parse(due), null, null);
    }

    static MemoryNote withThreads(MemoryThread... threads) {
        return new MemoryNote(List.of(), List.of(threads), List.of(), List.of());
    }

    @Test
    void capsApplyWhateverTheModelSent() {
        List<Line> about = IntStream.range(0, 20).mapToObj(i -> new Line("a" + i, "fact " + i)).toList();
        List<MemoryThread> threads = IntStream.range(0, 12).mapToObj(i -> thread("t" + i, Kind.PREP, "2026-11-10", null)).toList();
        List<Line> interests = new ArrayList<>(IntStream.range(0, 9).mapToObj(i -> new Line("i" + i, "interest " + i)).toList());
        interests.add(new Line("long", "x".repeat(300)));
        List<String> recent = IntStream.range(0, 9).mapToObj(i -> "topic " + i).toList();
        MemoryNote capped = new MemoryNote(about, threads, interests, recent).capped(TODAY);
        assertThat(capped.about()).hasSize(12);
        assertThat(capped.threads()).hasSize(8);
        assertThat(capped.interests()).hasSize(8);
        assertThat(capped.recent()).hasSize(6);
        MemoryNote longLine = new MemoryNote(List.of(new Line("l", "y".repeat(300))), List.of(), List.of(), List.of()).capped(TODAY);
        assertThat(longLine.about().getFirst().text()).hasSizeLessThanOrEqualTo(120).endsWith("…");
    }

    @Test
    void threadsFallAway30DaysAfterLastMention() {
        LocalDate today = LocalDate.of(2026, 12, 13);
        MemoryNote n = withThreads(thread("old", Kind.WORRY, "2026-11-12", null), thread("ok", Kind.WORRY, "2026-11-13", null)).capped(today);
        assertThat(n.threads()).extracting(MemoryThread::id).containsExactly("ok");
    }

    @Test
    void overduePlanBeatsAWorry() {
        MemoryNote n = withThreads(thread("w", Kind.WORRY, "2026-11-12", null), thread("p", Kind.PLAN, "2026-11-10", "2026-11-12"));
        assertThat(n.opener(TODAY, NOW, 1)).map(MemoryThread::id).contains("p");
    }

    @Test
    void aPlanDueLaterRanksBelowAWorry() {
        MemoryNote later = withThreads(thread("p", Kind.PLAN, "2026-11-12", "2026-11-20"), thread("w", Kind.WORRY, "2026-11-01", null));
        assertThat(later.opener(TODAY, NOW, 1)).map(MemoryThread::id).contains("w");
        MemoryNote undated = withThreads(thread("p", Kind.PLAN, "2026-11-12", null), thread("r", Kind.PREP, "2026-11-01", null));
        assertThat(undated.opener(TODAY, NOW, 1)).map(MemoryThread::id).contains("r");
        MemoryNote twoWorries = withThreads(thread("w1", Kind.WORRY, "2026-11-01", null), thread("w2", Kind.WORRY, "2026-11-11", null));
        assertThat(twoWorries.opener(TODAY, NOW, 1)).map(MemoryThread::id).contains("w2");
        MemoryNote dueToday = withThreads(thread("p", Kind.PLAN, "2026-11-10", "2026-11-13"), thread("w", Kind.WORRY, "2026-11-01", null));
        assertThat(dueToday.opener(TODAY, NOW, 1)).map(MemoryThread::id).contains("w");
    }

    @Test
    void aThreadThatOpenedRestsTwoTalksAndThreeDays() {
        MemoryNote n = withThreads(thread("w", Kind.WORRY, "2026-11-12", null)).withOpened("w", NOW, 4);
        assertThat(n.opener(TODAY, NOW, 5)).isEmpty();
        assertThat(n.opener(TODAY, NOW, 6)).isEmpty();
        assertThat(n.opener(TODAY.plusDays(2), NOW.plus(Duration.ofDays(2)), 7)).isEmpty();
        assertThat(n.opener(TODAY.plusDays(3), NOW.plus(Duration.ofDays(3)), 7)).map(MemoryThread::id).contains("w");
        assertThat(n.opener(TODAY.plusDays(9), NOW.plus(Duration.ofDays(9)), 6)).isEmpty();
    }

    @Test
    void allRestingMeansNoOpener() {
        MemoryNote n = withThreads(thread("a", Kind.WORRY, "2026-11-12", null), thread("b", Kind.PREP, "2026-11-12", null))
                .withOpened("a", NOW, 1).withOpened("b", NOW, 2);
        assertThat(n.opener(TODAY, NOW, 3)).isEmpty();
    }

    @Test
    void withoutRemovesTheLineEverywhere() {
        MemoryNote n = new MemoryNote(List.of(new Line("x", "a")), List.of(thread("y", Kind.PLAN, "2026-11-12", null)),
                List.of(new Line("z", "b")), List.of("t"));
        assertThat(n.without("x").about()).isEmpty();
        assertThat(n.without("y").threads()).isEmpty();
        assertThat(n.without("z").interests()).isEmpty();
        assertThat(n.ids()).containsExactlyInAnyOrder("x", "y", "z");
    }

    @Test
    void keepOnlyDropsLinesRemovedMeanwhile() {
        MemoryNote fresh = new MemoryNote(List.of(new Line("a", "1"), new Line("b", "2"), new Line("c", "3")), List.of(), List.of(), List.of());
        MemoryNote kept = fresh.keepOnly(Set.of("a"), Set.of("a", "b"));
        assertThat(kept.about()).extracting(Line::id).containsExactly("a", "c");
    }

    @Test
    void emptyIsEmpty() {
        assertThat(MemoryNote.empty().isEmpty()).isTrue();
        assertThat(withThreads(thread("a", Kind.PLAN, "2026-11-12", null)).isEmpty()).isFalse();
    }
}
