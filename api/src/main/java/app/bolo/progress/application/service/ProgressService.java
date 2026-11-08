package app.bolo.progress.application.service;

import app.bolo.coaching.application.port.in.DebriefHistoryUseCase;
import app.bolo.common.id.LearnerId;
import app.bolo.common.time.BoloClock;
import app.bolo.progress.application.port.in.ProgressUseCase;
import app.bolo.progress.domain.ProgressPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProgressService implements ProgressUseCase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    static final String FREE_TALK = "Free talk";

    private final DebriefHistoryUseCase debriefs;
    private final BoloClock clock;

    @Override
    public Map<String, Object> progress(LearnerId learner) {
        List<ProgressPlan.Session> sessions = sessions(learner);
        var trends = ProgressPlan.trends(sessions, clock.now());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("effort", ProgressPlan.effort(sessions));
        out.put("trends", Map.of("sessions", trends.sessions(), "after", ProgressPlan.TRENDS_AFTER, "skills", trends.bySkill()));
        out.put("patterns", ProgressPlan.patterns(sessions));
        out.put("latest", ProgressPlan.history(sessions).stream().limit(3).toList());
        return out;
    }

    @Override
    public Map<String, Object> history(LearnerId learner) {
        return Map.of("sessions", ProgressPlan.history(sessions(learner)));
    }

    private List<ProgressPlan.Session> sessions(LearnerId learner) {
        List<ProgressPlan.Session> out = new ArrayList<>();
        for (var d : debriefs.list(learner)) {
            if ("scoring".equals(d.status())) continue;
            JsonNode body = d.body() == null ? JSON.createObjectNode() : JSON.readTree(d.body());
            Map<String, String> skills = new LinkedHashMap<>();
            for (String k : ProgressPlan.SKILLS) {
                String level = body.path("skills").path(k).asString(null);
                if (level != null) skills.put(k, level);
            }
            List<String> fixTitles = new ArrayList<>();
            for (JsonNode f : body.path("fixes")) if (f.hasNonNull("title")) fixTitles.add(f.path("title").asString());
            String topic = body.path("topic").asString(null);
            out.add(new ProgressPlan.Session(d.talkId(), d.startedAt(), d.minutes(), d.status(), topic == null ? FREE_TALK : topic,
                    body.path("speechMs").asLong(0), skills, patterns(body), fixTitles));
        }
        return out;
    }

    /** body.patterns (every pattern found); older debriefs fall back to their sound and habit fixes. */
    static List<ProgressPlan.PatternSeen> patterns(JsonNode body) {
        List<ProgressPlan.PatternSeen> out = new ArrayList<>();
        JsonNode list = body.path("patterns");
        if (list.isArray() && !list.isEmpty()) {
            for (JsonNode p : list) {
                if (p.hasNonNull("pattern")) {
                    out.add(new ProgressPlan.PatternSeen(p.path("pattern").asString(), p.path("title").asString(p.path("pattern").asString()),
                            p.path("count").asInt(1)));
                } else if (p.hasNonNull("habit")) {
                    out.add(habit(p.path("habit").asString(), p.path("word").asString(null), p.path("count").asInt(1)));
                }
            }
            return out;
        }
        for (JsonNode f : body.path("fixes")) {
            String kind = f.path("kind").asString("");
            if (kind.equals("sound")) {
                String key = f.path("pattern").asString(f.path("title").asString(""));
                out.add(new ProgressPlan.PatternSeen(key, f.path("title").asString(key), f.path("count").asInt(1)));
            } else if (kind.equals("habit")) {
                out.add(habit(f.hasNonNull("word") ? "FILLER" : "SHORT_ANSWERS", f.path("word").asString(null), f.path("count").asInt(1)));
            }
        }
        return out;
    }

    private static ProgressPlan.PatternSeen habit(String kind, String word, int count) {
        if ("FILLER".equals(kind) && word != null) return new ProgressPlan.PatternSeen("filler:" + word, "Says \"" + word + "\" often", count);
        return new ProgressPlan.PatternSeen("short-answers", "Short answers", count);
    }
}
