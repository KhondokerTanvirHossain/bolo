package app.bolo.talk.application.service;

import app.bolo.common.id.TalkContext;
import app.bolo.llm.application.port.in.LlmClient;
import app.bolo.llm.application.port.in.LlmMessage;
import app.bolo.llm.application.port.in.LlmRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * The English teacher hat (2026-11-16): is this sentence worth teaching, and how would it sound one step better? A small
 * structured model call with a short timeout; anything slow, failed or doubtful means "don't teach" (Bolo just replies).
 */
@Service
public class TeacherCheck {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    static final String SYSTEM = """
            You help an English teacher. The learner is a Bangladeshi adult practising spoken English. Read the learner's \
            last turn. Decide whether one sentence in it can CLEARLY be said better (grammar, a missing word, a much more \
            natural phrase). If it is already fine or the change would be tiny, return teach false. If yes, return teach \
            true, "better": that one sentence said one step better, keeping their meaning and their words where possible, \
            natural when spoken, at most 20 words; and "why": a warm reason in at most 10 words. Judge clarity, not accent. \
            Never use the words mistake, wrong, error, bad, incorrect.""";
    static final String STAMMER = " This learner stammers: never teach a sentence because of repeated sounds or words, blocks or pauses.";
    static final Map<String, Object> SCHEMA = Map.of("type", "object", "additionalProperties", false,
            "required", List.of("teach", "better", "why"),
            "properties", Map.of("teach", Map.of("type", "boolean"), "better", Map.of("type", "string"), "why", Map.of("type", "string")));
    private static final List<String> BANNED = List.of("mistake", "wrong", "error", "bad", "incorrect");

    public record Teach(String better, String why) {
    }

    private final LlmClient llm;
    private final String model;
    private final long timeoutMs;

    public TeacherCheck(LlmClient llm, @Value("${bolo.talk.teacher.model:claude-haiku-5-5}") String model,
                        @Value("${bolo.talk.teacher.timeout-ms:2500}") long timeoutMs) {
        this.llm = llm;
        this.model = model;
        this.timeoutMs = timeoutMs;
    }

    public Optional<Teach> check(TalkContext ctx, String said, boolean stammer) {
        try {
            var request = new LlmRequest(model, SYSTEM + (stammer ? STAMMER : ""), List.of(LlmMessage.user("Learner's last turn: " + said)),
                    150, SCHEMA, true);
            JsonNode n = JSON.readTree(llm.stream(ctx, request, d -> { }).done().get(timeoutMs, TimeUnit.MILLISECONDS).text());
            if (!n.path("teach").asBoolean(false)) return Optional.empty();
            String better = n.path("better").asString("").strip();
            String why = n.path("why").asString("").strip();
            return ok(said, better) ? Optional.of(new Teach(better, why)) : Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** A real, short, kind change: not empty, at most 20 words, not the same words, no banned words. */
    static boolean ok(String said, String better) {
        if (better.isBlank() || better.split("\\s+").length > 20) return false;
        String lower = better.toLowerCase(Locale.ROOT);
        if (BANNED.stream().anyMatch(b -> lower.matches(".*\\b" + b + "\\b.*"))) return false;
        return !app.bolo.talk.domain.Teaching.changed(said, better).isEmpty();
    }
}
