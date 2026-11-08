package app.bolo.coaching.application.service;

import app.bolo.assessment.application.port.in.AssessTalkUseCase;
import app.bolo.assessment.application.port.in.AssessTalkUseCase.FixSlot;
import app.bolo.assessment.application.port.in.AssessTalkUseCase.HabitView;
import app.bolo.assessment.application.port.in.AssessTalkUseCase.SoundCandidate;
import app.bolo.assessment.application.port.in.AssessTalkUseCase.TalkAssessment;
import app.bolo.coaching.application.port.in.DebriefUseCase;
import app.bolo.coaching.application.port.out.DebriefPort;
import app.bolo.coaching.domain.CoachPrompt;
import app.bolo.coaching.domain.CoachReply;
import app.bolo.coaching.domain.ReplyCheck;
import app.bolo.common.audio.Wav;
import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkContext;
import app.bolo.common.id.TalkSessionId;
import app.bolo.common.time.BoloClock;
import app.bolo.llm.application.port.in.LlmClient;
import app.bolo.llm.application.port.in.LlmMessage;
import app.bolo.llm.application.port.in.LlmRequest;
import app.bolo.media.application.port.in.AudioStoreUseCase;
import app.bolo.speech.application.port.in.ScoredWord;
import app.bolo.speech.application.port.in.SynthesizeUseCase;
import app.bolo.speech.application.port.in.Voice;
import app.bolo.talk.application.port.in.TalkEnded;
import app.bolo.talk.application.port.in.TalkQueryUseCase;
import app.bolo.talk.application.port.in.TalkRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Coach v1 (DBF-1/2/3/4/5, ASM-4/5/6/7). When a talk ends, its debrief is composed off the request path:
 * the pure assessment domain finds sound patterns, fluency and habits from the scored words; one structured Claude
 * call writes the strength and sentence upgrades, which are validated against the transcript; clips are cut from the
 * learner's own audio and models are voiced by the Coach; the result is one JSON body the web renders as is.
 * Two automatic retries, then "failed" (US-3.7). Logs carry ids and counts, never transcript text.
 */
@Slf4j
@Service
public class CoachService implements DebriefUseCase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern MEDIA_FILE = Pattern.compile("(clip|model)-\\d{1,2}\\.wav");
    private static final int ATTEMPTS = 3;
    private static final long CLIP_PAD_MS = 500;
    private static final long MAX_CLIP_MS = 12_000;
    static final String SHORT_ANSWER_MODEL = "I enjoy my job, because I solve real problems, like a payment bug last week.";

    private static final java.util.Set<String> LEVELS = java.util.Set.of("BUILDING", "GOOD", "STRONG");

    private final TalkQueryUseCase talks;
    private final AssessTalkUseCase assessment;
    private final DebriefPort debriefs;
    private final LlmClient llm;
    private final SynthesizeUseCase voice;
    private final AudioStoreUseCase audio;
    private final BoloClock clock;
    private final String model;
    private final int maxTokens;
    private final long minSpeechMs;
    private final long backoffMs;

    public CoachService(TalkQueryUseCase talks, AssessTalkUseCase assessment, DebriefPort debriefs, LlmClient llm, SynthesizeUseCase voice,
                        AudioStoreUseCase audio, BoloClock clock,
                        @Value("${bolo.coaching.model}") String model,
                        @Value("${bolo.coaching.max-tokens}") int maxTokens,
                        @Value("${bolo.coaching.min-speech-ms}") long minSpeechMs,
                        @Value("${bolo.coaching.retry-backoff-ms}") long backoffMs) {
        this.talks = talks;
        this.assessment = assessment;
        this.debriefs = debriefs;
        this.llm = llm;
        this.voice = voice;
        this.audio = audio;
        this.clock = clock;
        this.model = model;
        this.maxTokens = maxTokens;
        this.minSpeechMs = minSpeechMs;
        this.backoffMs = backoffMs;
    }

    @EventListener
    public void onTalkEnded(TalkEnded e) {
        // No debrief after a crisis (TLK-14): the practice stops, and its audio isn't kept.
        if ("crisis".equals(e.reason())) {
            debriefs.delete(e.id());
            audio.deletePrefix("talk/" + e.id() + "/");
            return;
        }
        if (e.learnerTurns() < 1) return;
        debriefs.start(e.id(), clock.now());
        Thread.ofVirtual().name("coach-" + e.id()).start(() -> compose(e.id(), e.learner()));
    }

    /** The exception's type and the first Bolo frame (class.method:line) — enough to find it, never the learner's words. */
    static String where(Throwable e) {
        String type = e.getClass().getSimpleName();
        for (StackTraceElement f : e.getStackTrace()) {
            if (f.getClassName().startsWith("app.bolo.")) {
                String cls = f.getClassName().substring(f.getClassName().lastIndexOf('.') + 1);
                return type + " at " + cls + "." + f.getMethodName() + ":" + f.getLineNumber();
            }
        }
        return type;
    }

    public void failInterrupted() {
        debriefs.failAllScoring(clock.now());
    }

    void compose(TalkSessionId id, LearnerId learner) {
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                Built b = build(id, learner);
                debriefs.save(id, b.status(), b.body(), attempt, clock.now());
                log.info("debrief {} {} after {} attempt(s)", id, b.status(), attempt);
                deleteTurnAudio(id);
                return;
            } catch (RuntimeException e) {
                log.warn("debrief {} attempt {} failed: {}", id, attempt, where(e));
                if (attempt < ATTEMPTS) sleep(backoffMs);
            }
        }
        debriefs.save(id, "failed", "{\"status\":\"failed\"}", ATTEMPTS, clock.now());
        deleteTurnAudio(id);
    }

    /** Raw turn audio is only needed to cut clips; once the debrief is settled only the short clips stay (disk, NFR-14). */
    private void deleteTurnAudio(TalkSessionId id) {
        try {
            talks.record(id).ifPresent(r -> r.turns().stream().filter(t -> t.audioKey() != null)
                    .forEach(t -> audio.delete(t.audioKey())));
        } catch (RuntimeException e) {
            log.warn("debrief {} turn audio not deleted: {}", id, e.getClass().getSimpleName());
        }
    }

    private record Built(String status, String body) {
    }

    private Built build(TalkSessionId id, LearnerId learner) {
        TalkRecord record = talks.record(id).orElseThrow(() -> new IllegalStateException("no talk"));
        List<TalkRecord.Turn> learnerTurns = record.turns().stream().filter(TalkRecord.Turn::learner).toList();
        Map<Integer, TalkRecord.Turn> byIdx = new LinkedHashMap<>();
        learnerTurns.forEach(t -> byIdx.put(t.idx(), t));
        TalkAssessment scored = assessment.assess(learnerTurns.stream()
                .map(t -> new AssessTalkUseCase.TurnInput(t.idx(), t.text(), t.words())).toList());
        if (scored.speechMs() < minSpeechMs) return new Built("too_short", "{\"status\":\"too_short\"}");

        CoachReply reply = askCoach(id, learner, record);
        List<CoachReply.Upgrade> upgrades = (reply.upgrades() == null ? List.<CoachReply.Upgrade>of() : reply.upgrades()).stream()
                .filter(u -> byIdx.containsKey(u.turn()) && ReplyCheck.quoteIn(byIdx.get(u.turn()).text(), u.said())
                        && ReplyCheck.upgradeOk(u.said(), u.better()))
                .limit(2).toList();
        List<FixSlot> plan = assessment.plan(scored, upgrades.size());

        TalkContext ctx = new TalkContext(learner, id);
        ObjectNode body = JSON.createObjectNode();
        body.put("status", "ready");
        body.set("strength", strength(id, reply.strength(), byIdx, learnerTurns, scored.speechMs()));
        ArrayNode fixes = body.putArray("fixes");
        int n = 1;
        for (FixSlot slot : plan) {
            ObjectNode fix = switch (slot.kind()) {
                case "sound" -> soundFix(id, ctx, n, scored.sounds().get(slot.index()), byIdx);
                case "upgrade" -> upgradeFix(id, ctx, n, upgrades.get(slot.index()), byIdx);
                default -> habitFix(id, ctx, n, scored.habit().orElseThrow());
            };
            // A stable id: disputes name the card, not its position (positions shift as cards are removed).
            ObjectNode withId = JSON.createObjectNode();
            withId.put("id", "f" + n);
            withId.setAll(fix);
            fixes.add(withId);
            n++;
        }
        ObjectNode skills = body.putObject("skills");
        if (scored.pronunciationLevel() == null) skills.putNull("pronunciation");
        else skills.put("pronunciation", scored.pronunciationLevel());
        skills.put("fluency", scored.fluencyLevel());
        skills.put("sentenceBuilding", level(reply.sentenceLevel()));
        skills.put("vocabulary", level(reply.vocabularyLevel()));
        body.put("firstSession", debriefs.readyCountFor(learner, id) == 0);
        body.put("speechMs", scored.speechMs());
        ObjectNode latency = body.putObject("latency");
        List<Integer> waits = record.turns().stream().filter(t -> !t.learner() && t.idx() > 0 && t.latencyMs() != null)
                .map(TalkRecord.Turn::latencyMs).sorted().toList();
        putPercentile(latency, "p50Ms", waits, 50);
        putPercentile(latency, "p90Ms", waits, 90);
        return new Built("ready", JSON.writeValueAsString(body));
    }

    // ---- the Coach model ------------------------------------------------------------------------------------------

    private CoachReply askCoach(TalkSessionId id, LearnerId learner, TalkRecord record) {
        List<CoachPrompt.Line> lines = record.turns().stream()
                .map(t -> new CoachPrompt.Line(t.learner(), t.idx(), t.text())).toList();
        var request = new LlmRequest(model, CoachPrompt.system(),
                List.of(LlmMessage.user(CoachPrompt.transcript(lines))), maxTokens, CoachPrompt.schema(), false);
        try {
            String text = llm.stream(new TalkContext(learner, id), request, d -> { }).done().get(60, TimeUnit.SECONDS).text();
            return JSON.readValue(text, CoachReply.class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("coach model failed", e);
        }
    }

    // ---- cards ----------------------------------------------------------------------------------------------------

    private ObjectNode strength(TalkSessionId id, CoachReply.Strength s, Map<Integer, TalkRecord.Turn> byIdx,
                                List<TalkRecord.Turn> learnerTurns, long speechMs) {
        ObjectNode node = JSON.createObjectNode();
        boolean valid = s != null && byIdx.containsKey(s.turn()) && ReplyCheck.quoteIn(byIdx.get(s.turn()).text(), s.quote())
                && s.text() != null && !s.text().isBlank() && ReplyCheck.clean(s.text());
        if (valid) {
            node.put("text", s.text().trim());
            putClip(node, "clip", id, 0, cutQuote(byIdx.get(s.turn()), s.quote()));
        } else {
            long minutes = Math.max(1, Math.round(speechMs / 60_000.0));
            node.put("text", "You kept talking for about " + minutes + (minutes == 1 ? " minute" : " minutes") + " across "
                    + learnerTurns.size() + " turns.");
            learnerTurns.stream().max((a, b) -> Integer.compare(a.text().length(), b.text().length()))
                    .ifPresent(t -> putClip(node, "clip", id, 0, cutQuote(t, "")));
        }
        return node;
    }

    private ObjectNode soundFix(TalkSessionId id, TalkContext ctx, int n, SoundCandidate at, Map<Integer, TalkRecord.Turn> byIdx) {
        TalkRecord.Turn turn = byIdx.get(at.turnIdx());
        String sentence = sentenceWith(turn.text(), at.word());
        ObjectNode fix = JSON.createObjectNode();
        fix.put("kind", "sound");
        fix.put("title", at.title());
        fix.put("example", at.example());
        fix.put("said", sentence);
        fix.put("saidHighlight", at.word());
        fix.put("better", sentence);
        fix.put("betterHighlight", at.word());
        fix.put("tip", at.tip());
        fix.put("count", at.count());
        putClip(fix, "clip", id, n, cutWord(turn, at));
        putModel(fix, id, ctx, n, sentence);
        return fix;
    }

    private ObjectNode upgradeFix(TalkSessionId id, TalkContext ctx, int n, CoachReply.Upgrade u, Map<Integer, TalkRecord.Turn> byIdx) {
        ObjectNode fix = JSON.createObjectNode();
        fix.put("kind", "upgrade");
        fix.put("title", "Say it better");
        fix.put("said", u.said().trim());
        fix.put("better", u.better().trim());
        ArrayNode changed = fix.putArray("changed");
        ReplyCheck.changedWords(u.said(), u.better()).forEach(changed::add);
        putClip(fix, "clip", id, n, cutQuote(byIdx.get(u.turn()), u.said()));
        putModel(fix, id, ctx, n, u.better().trim());
        return fix;
    }

    private ObjectNode habitFix(TalkSessionId id, TalkContext ctx, int n, HabitView habit) {
        ObjectNode fix = JSON.createObjectNode();
        fix.put("kind", "habit");
        if ("FILLER".equals(habit.kind())) {
            fix.put("title", "Filler watch");
            fix.put("word", habit.word());
            fix.put("count", habit.count());
            fix.put("tip", "Try a short pause instead of \"" + habit.word() + "\".");
        } else {
            fix.put("title", "Longer answers");
            fix.put("averageWords", Math.round(habit.averageWords()));
            fix.put("tip", "Try point, reason, example.");
            fix.put("better", SHORT_ANSWER_MODEL);
            putModel(fix, id, ctx, n, SHORT_ANSWER_MODEL);
        }
        return fix;
    }

    // ---- audio ----------------------------------------------------------------------------------------------------

    private byte[] cutWord(TalkRecord.Turn turn, SoundCandidate at) {
        if (turn == null || turn.streamStartMs() == null) return null;
        long base = turn.streamStartMs();
        return turnPcm(turn).map(pcm -> Wav.slice(pcm, 16000, at.startMs() - base - CLIP_PAD_MS, at.endMs() - base + CLIP_PAD_MS))
                .filter(p -> p.length > 0).orElse(null);
    }

    /** The quote's span in the turn (see {@link #quoteWindow}); no clip rather than an unrelated one. */
    private byte[] cutQuote(TalkRecord.Turn turn, String quote) {
        if (turn == null) return null;
        Optional<byte[]> pcm = turnPcm(turn);
        if (pcm.isEmpty()) return null;
        long turnMs = pcm.get().length / 32L;
        long[] window = quoteWindow(turn.words(), turn.streamStartMs() == null ? 0 : turn.streamStartMs(), quote, turnMs);
        return window == null ? null : Wav.slice(pcm.get(), 16000, window[0], window[1]);
    }

    /**
     * Where {@code quote} was said in a turn, relative to the turn's audio (±300 ms, at most 12 s). Recognised words
     * can differ from the display text ("five" vs "5"), so a quote may not place: then a turn short enough to be the
     * moment itself is used whole, and a longer one gets no clip rather than an unrelated 12 s (review #5).
     */
    static long[] quoteWindow(List<ScoredWord> words, long base, String quote, long turnMs) {
        String[] q = ReplyCheck.normalize(quote).split(" ");
        if (!words.isEmpty() && !q[0].isEmpty()) {
            for (int i = 0; i + q.length <= words.size(); i++) {
                boolean match = true;
                for (int k = 0; k < q.length && match; k++) match = ReplyCheck.normalize(words.get(i + k).word()).equals(q[k]);
                if (match) {
                    long start = words.get(i).startMs() - base - 300;
                    long end = Math.min(words.get(i + q.length - 1).endMs() - base + 300, start + MAX_CLIP_MS);
                    return new long[]{start, end};
                }
            }
        }
        return turnMs <= MAX_CLIP_MS ? new long[]{0, turnMs} : null;
    }

    private Optional<byte[]> turnPcm(TalkRecord.Turn turn) {
        if (turn.audioKey() == null) return Optional.empty();
        return audio.get(turn.audioKey()).map(Wav::pcm).filter(p -> p.length > 0);
    }

    private void putClip(ObjectNode node, String field, TalkSessionId id, int n, byte[] pcm16k) {
        if (pcm16k == null || pcm16k.length == 0) {
            node.putNull(field);
            return;
        }
        String file = "clip-" + n + ".wav";
        audio.put("talk/" + id + "/" + file, Wav.wrap(pcm16k, 16000));
        node.put(field, url(id, file));
    }

    /** The model line in the Coach's voice; a voice failure leaves the card without a model rather than failing it. */
    private void putModel(ObjectNode node, TalkSessionId id, TalkContext ctx, int n, String text) {
        try {
            ByteArrayOutputStream pcm = new ByteArrayOutputStream();
            voice.synthesize(ctx, Voice.COACH, text, pcm::writeBytes);
            String file = "model-" + n + ".wav";
            audio.put("talk/" + id + "/" + file, Wav.wrap(pcm.toByteArray(), 24000));
            node.put("model", url(id, file));
        } catch (RuntimeException e) {
            log.warn("debrief {} model {} not voiced: {}", id, n, e.getClass().getSimpleName());
            node.putNull("model");
        }
    }

    private static String url(TalkSessionId id, String file) {
        return "/api/media/talk/" + id + "/" + file;
    }

    // ---- reading it back --------------------------------------------------------------------------------------------

    @Override
    public Optional<String> view(LearnerId learner, TalkSessionId talk) {
        return debriefs.find(talk).filter(d -> d.learner().equals(learner))
                .map(d -> d.body() != null ? d.body() : "{\"status\":\"" + d.status() + "\"}");
    }

    @Override
    public boolean dispute(LearnerId learner, TalkSessionId talk, String fixId) {
        boolean owner = debriefs.find(talk).map(d -> d.learner().equals(learner)).orElse(false);
        if (!owner || fixId == null) return false;
        String[] removed = {null};
        int[] at = {-1};
        debriefs.updateReadyBody(talk, current -> {
            ObjectNode body = (ObjectNode) JSON.readTree(current);
            ArrayNode fixes = (ArrayNode) body.get("fixes");
            if (fixes == null) return null;
            for (int i = 0; i < fixes.size(); i++) {
                if (fixId.equals(fixes.get(i).path("id").asString(""))) {
                    removed[0] = fixes.get(i).path("kind").asString("unknown");
                    at[0] = i;
                    fixes.remove(i);
                    return JSON.writeValueAsString(body);
                }
            }
            return null;
        }, clock.now());
        if (removed[0] == null) return false;
        debriefs.logDispute(talk, at[0], removed[0], clock.now());
        return true;
    }

    @Override
    public Optional<byte[]> media(LearnerId learner, TalkSessionId talk, String file) {
        if (file == null || !MEDIA_FILE.matcher(file).matches()) return Optional.empty();
        boolean owner = debriefs.find(talk).map(d -> d.learner().equals(learner)).orElse(false);
        return owner ? audio.get("talk/" + talk + "/" + file) : Optional.empty();
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private static String level(String value) {
        return value != null && LEVELS.contains(value) ? value : "GOOD";
    }

    /** The sentence of {@code text} containing {@code word}, or the whole text. */
    static String sentenceWith(String text, String word) {
        for (String s : text.split("(?<=[.!?])\\s+")) {
            if (ReplyCheck.quoteIn(s, word)) return s.trim();
        }
        return text.trim();
    }

    private static void putPercentile(ObjectNode node, String field, List<Integer> sorted, int p) {
        if (sorted.isEmpty()) {
            node.putNull(field);
            return;
        }
        int rank = (int) Math.ceil(p / 100.0 * sorted.size());
        node.put(field, sorted.get(Math.max(0, rank - 1)));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
