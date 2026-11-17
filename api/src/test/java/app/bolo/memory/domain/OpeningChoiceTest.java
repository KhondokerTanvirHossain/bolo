package app.bolo.memory.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/** Founder 2026-11-18: the free talk opens with a choice: the last thing, another from history, or a curious one. */
class OpeningChoiceTest {

    static final MemoryThread PLAN = new MemoryThread("p1", MemoryThread.Kind.PLAN, "The client demo on Thursday",
            LocalDate.of(2026, 11, 11), null, null, null);

    static MemoryNote note(List<MemoryThread> threads, List<String> recent) {
        return new MemoryNote(List.of(new Line("a1", "Works as a tester")), threads, List.of(new Line("i1", "Cricket")), recent);
    }

    @Test
    void theFollowUpComesFirst() {
        var c = OpeningChoice.of(note(List.of(PLAN), List.of("The payment bug", "Weekend in Sylhet")), PLAN, new Random(1)).orElseThrow();
        assertThat(c.last()).isEqualTo("The client demo on Thursday");
        assertThat(c.other()).isIn("The payment bug", "Weekend in Sylhet", "Cricket");
        assertThat(TopicBank.curious()).contains(c.curious());
    }

    @Test
    void withoutAFollowUpTheNewestTopic() {
        var c = OpeningChoice.of(note(List.of(), List.of("The payment bug", "Weekend in Sylhet")), null, new Random(2)).orElseThrow();
        assertThat(c.last()).isEqualTo("The payment bug");
        assertThat(c.other()).isIn("Weekend in Sylhet", "Cricket").isNotEqualTo(c.last());
    }

    @Test
    void theOtherIsNeverTheLast() {
        for (int seed = 0; seed < 50; seed++) {
            var c = OpeningChoice.of(note(List.of(PLAN), List.of("The client demo on Thursday", "Cricket")), PLAN, new Random(seed)).orElseThrow();
            assertThat(c.other()).isNotEqualToIgnoringCase(c.last());
        }
    }

    @Test
    void noHistoryNoChoice() {
        assertThat(OpeningChoice.of(new MemoryNote(List.of(new Line("a1", "Works as a tester")), List.of(), List.of(), List.of()), null,
                new Random(1))).isEmpty();
        assertThat(OpeningChoice.of(null, null, new Random(1))).isEmpty();
    }

    @Test
    void onlyOneThingKnownStillOffersTheCuriousOne() {
        var c = OpeningChoice.of(new MemoryNote(List.of(), List.of(), List.of(), List.of("The payment bug")), null, new Random(1)).orElseThrow();
        assertThat(c.last()).isEqualTo("The payment bug");
        assertThat(c.other()).isNull();
        assertThat(c.curious()).isNotBlank();
    }

    @Test
    void theSameSeedTheSameChoice() {
        MemoryNote n = note(List.of(), List.of("A", "B", "C", "D"));
        assertThat(OpeningChoice.of(n, null, new Random(42))).isEqualTo(OpeningChoice.of(n, null, new Random(42)));
    }

    @Test
    void theCuriousBankIsBigAndQuestionsOnly() {
        assertThat(TopicBank.curious()).hasSizeGreaterThanOrEqualTo(20).allMatch(q -> q.endsWith("?"));
    }

    @Test
    void theBriefOffersTheThree() {
        var c = new OpeningChoice("The client demo on Thursday", "Cricket", "Would you rather never feel cold or never feel sleepy?");
        String b = MemoryPrompt.brief(note(List.of(PLAN), List.of()), null, c, List.of());
        assertThat(b).contains("offer a choice").contains("pick up: The client demo on Thursday").contains("Cricket")
                .contains("Would you rather never feel cold or never feel sleepy?").contains("follow whatever they pick")
                .doesNotContain("one follow-up about");
    }
}
