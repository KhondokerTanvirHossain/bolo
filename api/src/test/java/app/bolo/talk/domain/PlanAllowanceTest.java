package app.bolo.talk.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** ACC-5: a paid plan is a monthly talk count (Dhaka calendar month); without one, the free plan. */
class PlanAllowanceTest {

    static final Instant NOW = Instant.parse("2026-11-20T06:00:00Z");
    static final Map<String, Integer> MONTHLY = Map.of("basic", 8, "premium", 20, "ultimate", 45);

    static PlanAllowance.Sub sub(String plan, String from, String to) {
        return new PlanAllowance.Sub(plan, Instant.parse(from), Instant.parse(to));
    }

    @Test
    void anActivePlanGivesItsMonthlyTalks() {
        var a = PlanAllowance.of(List.of(Instant.parse("2026-11-15T06:00:00Z"), Instant.parse("2026-11-19T06:00:00Z")),
                List.of(sub("premium", "2026-11-10T00:00:00Z", "2026-12-10T00:00:00Z")), MONTHLY, NOW);
        assertThat(a).isEqualTo(new PlanAllowance.Allowance("premium", "month", 18, 20, LocalDate.parse("2026-12-01")));
    }

    /** Review (P2d): a second month joined end-to-start keeps counting this month's talks; it isn't a fresh 20. */
    @Test
    void aChainedMonthDoesntResetThisMonthsTalks() {
        var now = Instant.parse("2026-12-21T06:00:00Z");
        var used = List.of(Instant.parse("2026-12-05T06:00:00Z"), Instant.parse("2026-12-15T06:00:00Z"), Instant.parse("2026-12-21T05:00:00Z"));
        var a = PlanAllowance.of(used, List.of(sub("premium", "2026-11-20T00:00:00Z", "2026-12-20T00:00:00Z"),
                sub("premium", "2026-12-20T00:00:00Z", "2027-01-19T00:00:00Z")), MONTHLY, now);
        assertThat(a.left()).isEqualTo(17);
    }

    @Test
    void talksBeforeThePlanStartedDontCount() {
        var a = PlanAllowance.of(List.of(Instant.parse("2026-11-05T06:00:00Z"), Instant.parse("2026-11-19T06:00:00Z")),
                List.of(sub("basic", "2026-11-10T00:00:00Z", "2026-12-10T00:00:00Z")), MONTHLY, NOW);
        assertThat(a.left()).isEqualTo(7);
    }

    @Test
    void anEndedPlanFallsBackToFree() {
        var a = PlanAllowance.of(List.of(), List.of(sub("premium", "2026-10-01T00:00:00Z", "2026-11-01T00:00:00Z")), MONTHLY, NOW);
        assertThat(a.plan()).isEqualTo("free");
        assertThat(a.total()).isEqualTo(7);
    }

    @Test
    void theBiggerOfTwoActivePlansWins() {
        var a = PlanAllowance.of(List.of(), List.of(sub("basic", "2026-11-01T00:00:00Z", "2026-12-01T00:00:00Z"),
                sub("ultimate", "2026-11-15T00:00:00Z", "2026-12-15T00:00:00Z")), MONTHLY, NOW);
        assertThat(a.plan()).isEqualTo("ultimate");
        assertThat(a.total()).isEqualTo(45);
    }

    @Test
    void usedMoreThanTheNewLimitShowsZero() {
        List<Instant> many = java.util.stream.IntStream.range(0, 10).mapToObj(i -> Instant.parse("2026-11-12T06:00:00Z").plusSeconds(i * 60L)).toList();
        var a = PlanAllowance.of(many, List.of(sub("basic", "2026-11-10T00:00:00Z", "2026-12-10T00:00:00Z")), MONTHLY, NOW);
        assertThat(a.left()).isZero();
    }
}
