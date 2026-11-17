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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * The English teacher hat (2026-11-16): is this sentence worth teaching, and how would it sound one step better? A small
 * structured model call with a short timeout; anything slow, failed or doubtful means "don't teach" (Bolo just replies).
 */
@Service
public class TeacherCheck {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    static final String SYSTEM = """
            You help an English teacher who teaches actively. The learner is a Bangladeshi adult practising spoken \
            English. Read the learner's last turn. Decide whether one sentence in it can be said better in one of these \
            ways, and pick the one that helps most: "grammar" (fix the grammar or a missing word), "word" (a stronger or \
            more exact word instead of a plain one, like "huge" for "very big"), "phrase" (a more natural everyday \
            phrase), or "idiom" (a common idiom people really say today at work or in daily life, like "on the same \
            page" or "running late"; never old textbook idioms like "raining cats and dogs"). Teach whenever there is \
            a real improvement they can learn from; return teach false only when the sentence is already natural and \
            strong. If yes, return teach true, "kind", "better": that one sentence said better, keeping their meaning \
            and their words where possible, natural when spoken, at most 20 words; and "why": a warm reason in at most \
            10 words. Judge clarity, not accent. Never use the words mistake, wrong, error, bad, incorrect.""";
    static final String WISH = " What they said they'd like to learn today: \"%s\". When this sentence allows it, teach that kind of thing.";
    /** Founder 2026-11-18: the learner said it in Bangla; the teacher gives them the English. */
    static final String ENGLISH = """
            You help an English teacher. The learner is a Bangladeshi adult practising spoken English, and just said this \
            in Bangla. Return teach true, kind "grammar", "better": what they meant, as one natural, simple English \
            sentence they could say themselves (at most 20 words), and "why": a warm reason in at most 10 words. If you \
            can't tell what they meant, return teach false. Never use the words mistake, wrong, error, bad, incorrect.""";
    static final String STAMMER = " This learner stammers: never teach a sentence because of repeated sounds or words, blocks or pauses.";
    static final Map<String, Object> SCHEMA = Map.of("type", "object", "additionalProperties", false,
            "required", List.of("teach", "kind", "better", "why"),
            "properties", Map.of("teach", Map.of("type", "boolean"),
                    "kind", Map.of("type", "string", "enum", List.of("grammar", "word", "phrase", "idiom")),
                    "better", Map.of("type", "string"), "why", Map.of("type", "string")));
    private static final List<String> BANNED = List.of("mistake", "wrong", "error", "bad", "incorrect");

    /** {@code kind}: grammar, a better word, a natural phrase or an idiom (founder 2026-11-18). */
    public record Teach(String better, String why, app.bolo.talk.domain.Teaching.Kind kind) {

        public Teach(String better, String why) {
            this(better, why, app.bolo.talk.domain.Teaching.Kind.GRAMMAR);
        }
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

    /** How long a finished turn waits for the check (review I4: the check usually starts earlier, at speculation). */
    public long timeoutMs() {
        return timeoutMs;
    }

    /** Starts the check; the future never fails (anything wrong is "don't teach"). */
    public CompletableFuture<Optional<Teach>> ask(TalkContext ctx, String said, boolean stammer) {
        return ask(ctx, said, stammer, null);
    }

    /** {@code wish}: the learner's answer to "anything you'd like to learn today?", or null. */
    public CompletableFuture<Optional<Teach>> ask(TalkContext ctx, String said, boolean stammer, String wish) {
        try {
            String system = SYSTEM + (stammer ? STAMMER : "") + (wish == null || wish.isBlank() ? "" : WISH.formatted(wish.strip()));
            var request = new LlmRequest(model, system, List.of(LlmMessage.user("Learner's last turn: " + said)),
                    150, SCHEMA, true);
            return llm.stream(ctx, request, d -> { }).done().handle((r, e) -> e != null ? Optional.<Teach>empty() : parse(said, r.text()));
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    /** Founder 2026-11-18: the English for a Bangla turn. */
    public CompletableFuture<Optional<Teach>> askEnglish(TalkContext ctx, String saidInBangla) {
        try {
            var request = new LlmRequest(model, ENGLISH, List.of(LlmMessage.user("What they said in Bangla: " + saidInBangla)),
                    150, SCHEMA, true);
            return llm.stream(ctx, request, d -> { }).done().handle((r, e) -> e != null ? Optional.<Teach>empty() : parse(saidInBangla, r.text()));
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    /** Waits at most {@link #timeoutMs()} for a started check. */
    public Optional<Teach> await(CompletableFuture<Optional<Teach>> started) {
        try {
            return started.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<Teach> check(TalkContext ctx, String said, boolean stammer) {
        return await(ask(ctx, said, stammer));
    }

    private static Optional<Teach> parse(String said, String text) {
        try {
            JsonNode n = JSON.readTree(text);
            if (!n.path("teach").asBoolean(false)) return Optional.empty();
            String better = n.path("better").asString("").strip();
            String why = n.path("why").asString("").strip();
            var kind = app.bolo.talk.domain.Teaching.Kind.of(n.path("kind").asString(null));
            return ok(said, better) ? Optional.of(new Teach(better, why, kind)) : Optional.empty();
        } catch (RuntimeException e) {
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
