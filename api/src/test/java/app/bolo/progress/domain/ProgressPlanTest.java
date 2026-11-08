package app.bolo.progress.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressPlanTest {

    static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");

    static ProgressPlan.Session ready(int daysAgo, String... patternKeys) {
        List<ProgressPlan.PatternSeen> seen = new ArrayList<>();
        for (String k : patternKeys) seen.add(new ProgressPlan.PatternSeen(k, "Title " + k, 2));
        return new ProgressPlan.Session("t" + daysAgo, NOW.minus(Duration.ofDays(daysAgo)), 5, "ready", "Topic " + daysAgo, 90_000,
                Map.of("pronunciation", "GOOD", "fluency", "BUILDING", "sentenceBuilding", "GOOD", "vocabulary", "STRONG"), seen,
                List.of("Fix A", "Fix B"));
    }

    static List<ProgressPlan.Session> series(String[]... perTalk) {
        List<ProgressPlan.Session> out = new ArrayList<>();
        for (int i = 0; i < perTalk.length; i++) out.add(ready(perTalk.length - i, perTalk[i]));
        return out;
    }

    static ProgressPlan.PatternTag tag(ProgressPlan.Patterns p, String key) {
        return p.active().stream().filter(t -> t.key().equals(key)).findFirst().orElse(null);
    }

    @Test
    void effortCountsTalksAndMinutes() {
        var s = new ArrayList<>(List.of(ready(3), ready(2)));
        s.add(new ProgressPlan.Session("short", NOW, 5, "too_short", null, 0, Map.of(), List.of(), List.of()));
        var e = ProgressPlan.effort(s);
        assertThat(e.talks()).isEqualTo(3);
        assertThat(e.minutes()).isEqualTo(3);
    }

    @Test
    void trendsWaitForThreeTalks() {
        var t = ProgressPlan.trends(List.of(ready(2), ready(1)), NOW);
        assertThat(t.sessions()).isEqualTo(2);
        assertThat(t.bySkill()).isEmpty();
        var t3 = ProgressPlan.trends(List.of(ready(3), ready(2), ready(1)), NOW);
        assertThat(t3.bySkill().get("fluency")).hasSize(3).allMatch(p -> p.level().equals("BUILDING"));
    }

    @Test
    void trendsKeepTheLast30Days() {
        var t = ProgressPlan.trends(List.of(ready(40), ready(20), ready(10), ready(1)), NOW);
        assertThat(t.bySkill().get("vocabulary")).hasSize(3);
    }

    @Test
    void aPatternFirstSeenInTheLastTwoIsNew() {
        var p = ProgressPlan.patterns(series(new String[]{"V_BH"}, new String[]{"V_BH"}, new String[]{"V_BH", "TH_TD"}));
        assertThat(tag(p, "TH_TD").tag()).isEqualTo("NEW");
    }

    @Test
    void fewerRecentAppearancesIsImproving() {
        var p = ProgressPlan.patterns(series(new String[]{"Z_J"}, new String[]{"Z_J"}, new String[]{"Z_J"},
                new String[]{}, new String[]{"Z_J"}, new String[]{}));
        assertThat(tag(p, "Z_J").tag()).isEqualTo("IMPROVING");
    }

    @Test
    void presentEveryTimeIsSteady() {
        var p = ProgressPlan.patterns(series(new String[]{"W_V"}, new String[]{"W_V"}, new String[]{"W_V"}, new String[]{"W_V"}));
        assertThat(tag(p, "W_V").tag()).isEqualTo("STEADY");
    }

    @Test
    void fiveTalksWithoutItIsFixed() {
        var p = ProgressPlan.patterns(series(new String[]{"S_SH"}, new String[]{"S_SH"}, new String[]{}, new String[]{}, new String[]{},
                new String[]{}, new String[]{}));
        assertThat(p.fixed()).extracting(ProgressPlan.PatternTag::key).containsExactly("S_SH");
        assertThat(tag(p, "S_SH")).isNull();
    }

    @Test
    void aFixedPatternThatReturnsIsActiveAgain() {
        var p = ProgressPlan.patterns(series(new String[]{"S_SH"}, new String[]{}, new String[]{}, new String[]{}, new String[]{},
                new String[]{}, new String[]{"S_SH"}));
        assertThat(p.fixed()).isEmpty();
        assertThat(tag(p, "S_SH")).isNotNull();
    }

    @Test
    void atMostFiveActive() {
        var p = ProgressPlan.patterns(series(new String[]{"A", "B", "C", "D", "E", "F", "G"}));
        assertThat(p.active()).hasSize(5);
    }

    @Test
    void historyIsNewestFirst() {
        var h = ProgressPlan.history(List.of(ready(3), ready(1), ready(2)));
        assertThat(h).extracting(ProgressPlan.Row::talkId).containsExactly("t1", "t2", "t3");
        assertThat(h.get(0).fixes()).containsExactly("Fix A", "Fix B");
    }
}
