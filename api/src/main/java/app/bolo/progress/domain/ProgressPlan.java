package app.bolo.progress.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Progress and History from a learner's past debriefs (PRG-1, PRG-3, PRG-8). Pure: effort, the 4 skill trends in words,
 * "My patterns" (New / Improving / Steady, and Fixed), and the history rows. Never a score or a percentage.
 */
public final class ProgressPlan {

    public static final int TRENDS_AFTER = 3;
    public static final Duration TREND_WINDOW = Duration.ofDays(30);
    public static final int FIXED_AFTER = 5;
    public static final int MAX_ACTIVE = 5;
    public static final List<String> SKILLS = List.of("pronunciation", "fluency", "sentenceBuilding", "vocabulary");

    /** One past talk with a debrief; status is ready, too_short or failed. skills hold BUILDING / GOOD / STRONG. */
    public record Session(String talkId, Instant startedAt, int minutes, String status, String topic, long speechMs,
                          Map<String, String> skills, List<PatternSeen> patterns, List<String> fixTitles) {
        boolean ready() {
            return "ready".equals(status);
        }
    }

    public record PatternSeen(String key, String title, int count) {
    }

    public record Effort(int talks, int minutes) {
    }

    public record TrendPoint(Instant at, String level) {
    }

    /** {@code bySkill} is empty until {@link #TRENDS_AFTER} finished talks. */
    public record Trends(int sessions, Map<String, List<TrendPoint>> bySkill) {
    }

    /** tag: NEW, IMPROVING, STEADY (active) or FIXED. */
    public record PatternTag(String key, String title, String tag) {
    }

    public record Patterns(List<PatternTag> active, List<PatternTag> fixed) {
    }

    public record Row(String talkId, Instant startedAt, int minutes, String topic, String status, List<String> fixes) {
    }

    private ProgressPlan() {
    }

    public static Effort effort(List<Session> sessions) {
        long speech = sessions.stream().filter(Session::ready).mapToLong(Session::speechMs).sum();
        return new Effort(sessions.size(), (int) Math.round(speech / 60_000.0));
    }

    public static Trends trends(List<Session> sessions, Instant now) {
        List<Session> done = sessions.stream().filter(Session::ready).sorted(Comparator.comparing(Session::startedAt)).toList();
        if (done.size() < TRENDS_AFTER) return new Trends(done.size(), Map.of());
        Instant from = now.minus(TREND_WINDOW);
        Map<String, List<TrendPoint>> by = new LinkedHashMap<>();
        for (String skill : SKILLS) {
            List<TrendPoint> points = new ArrayList<>();
            for (Session s : done) {
                String level = s.skills().get(skill);
                if (level != null && !s.startedAt().isBefore(from)) points.add(new TrendPoint(s.startedAt(), level));
            }
            by.put(skill, points);
        }
        return new Trends(done.size(), by);
    }

    public static Patterns patterns(List<Session> sessions) {
        List<Session> done = sessions.stream().filter(Session::ready).sorted(Comparator.comparing(Session::startedAt)).toList();
        int n = done.size();
        Map<String, List<Integer>> where = new LinkedHashMap<>();
        Map<String, List<Integer>> counts = new LinkedHashMap<>();
        Map<String, String> titles = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            for (PatternSeen p : done.get(i).patterns()) {
                if (where.computeIfAbsent(p.key(), k -> new ArrayList<>()).contains(i)) continue;
                where.get(p.key()).add(i);
                counts.computeIfAbsent(p.key(), k -> new ArrayList<>()).add(p.count());
                titles.put(p.key(), p.title());
            }
        }
        record Ranked(PatternTag tag, int recent, int total, int last) {
        }
        List<Ranked> active = new ArrayList<>();
        List<PatternTag> fixed = new ArrayList<>();
        for (var e : where.entrySet()) {
            List<Integer> at = e.getValue();
            int first = at.getFirst(), last = at.getLast();
            String key = e.getKey(), title = titles.get(key);
            if (n - 1 - last >= FIXED_AFTER) {
                fixed.add(new PatternTag(key, title, "FIXED"));
                continue;
            }
            int recent = (int) at.stream().filter(i -> i >= n - 3).count();
            int before = (int) at.stream().filter(i -> i >= n - 6 && i < n - 3).count();
            List<Integer> c = counts.get(key);
            boolean fewerTimes = c.size() >= 2 && c.getLast() < c.get(c.size() - 2);
            String tag = first >= n - 2 ? "NEW" : (recent < before || fewerTimes) ? "IMPROVING" : "STEADY";
            active.add(new Ranked(new PatternTag(key, title, tag), recent, at.size(), last));
        }
        active.sort(Comparator.comparingInt(Ranked::recent).reversed().thenComparing(Comparator.comparingInt(Ranked::total).reversed())
                .thenComparing(Comparator.comparingInt(Ranked::last).reversed()));
        return new Patterns(active.stream().limit(MAX_ACTIVE).map(Ranked::tag).toList(), fixed);
    }

    public static List<Row> history(List<Session> sessions) {
        return sessions.stream().sorted(Comparator.comparing(Session::startedAt).reversed())
                .map(s -> new Row(s.talkId(), s.startedAt(), s.minutes(), s.topic(), s.status(), s.fixTitles().stream().limit(3).toList()))
                .toList();
    }
}
