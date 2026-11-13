package app.bolo.memory.application.service;

import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkContext;
import app.bolo.common.id.TalkSessionId;
import app.bolo.common.time.BoloClock;
import app.bolo.llm.application.port.in.LlmClient;
import app.bolo.llm.application.port.in.LlmMessage;
import app.bolo.llm.application.port.in.LlmRequest;
import app.bolo.memory.application.port.in.MemoryUseCase;
import app.bolo.memory.application.port.in.TalkBrief;
import app.bolo.memory.application.port.out.MemoryPort;
import app.bolo.memory.domain.TopicBank;
import app.bolo.memory.domain.Line;
import app.bolo.memory.domain.MemoryNote;
import app.bolo.memory.domain.MemoryPrompt;
import app.bolo.memory.domain.MemoryThread;
import app.bolo.talk.application.port.in.TalkEnded;
import app.bolo.talk.application.port.in.TalkQueryUseCase;
import app.bolo.talk.application.port.in.TalkRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * What Bolo remembers (spec 2026-11-13 "Bolo knows you"). After a talk, one structured call rewrites the learner's short
 * note, off the talk's path. Updates for one learner run one at a time; a line removed, or remembering switched off,
 * while an update runs is respected when it saves.
 */
@Service
public class MemoryService implements MemoryUseCase {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** A talk needs this many learner turns before it's worth a note. */
    static final int MIN_TURNS = 2;

    private final MemoryPort memory;
    private final TalkQueryUseCase talks;
    private final LlmClient llm;
    private final BoloClock clock;
    private final TransactionTemplate tx;
    private final String model;
    private final int maxTokens;
    private final Map<LearnerId, ReentrantLock> locks = new ConcurrentHashMap<>();

    public MemoryService(MemoryPort memory, TalkQueryUseCase talks, LlmClient llm, BoloClock clock,
                         PlatformTransactionManager transactions,
                         @Value("${bolo.memory.model}") String model, @Value("${bolo.memory.max-tokens}") int maxTokens) {
        this.memory = memory;
        this.talks = talks;
        this.llm = llm;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
        this.model = model;
        this.maxTokens = maxTokens;
    }

    @EventListener
    public void onTalkEnded(TalkEnded e) {
        // Never from a talk that ended in crisis care, never from a talk too short to say anything (spec: Constraints).
        if ("crisis".equals(e.reason()) || e.learnerTurns() < MIN_TURNS) return;
        Thread.ofVirtual().name("memory-" + e.id()).start(() -> update(e.id(), e.learner()));
    }

    /** Rewrites the learner's note from this talk. Never throws: on any failure the old note stays. */
    public void update(TalkSessionId talk, LearnerId learner) {
        ReentrantLock lock = locks.computeIfAbsent(learner, k -> new ReentrantLock());
        lock.lock();
        try {
            MemoryPort.Row before = memory.find(learner).orElse(null);
            if (before != null && !before.enabled()) return;
            MemoryNote old = before == null ? MemoryNote.empty() : before.note();
            TalkRecord record = talks.record(talk).orElse(null);
            if (record == null || !record.learner().equals(learner)) return;
            LocalDate today = clock.today();
            MemoryPrompt.Reply reply = ask(talk, learner, today, old, record);
            if (reply == null) return;
            MemoryNote fresh = apply(reply, old, today);
            Set<String> sent = old.ids();
            tx.executeWithoutResult(s -> {
                MemoryPort.Row now = memory.lockOrCreate(learner);
                if (!now.enabled()) return;
                MemoryNote saved = fresh.keepOnly(now.note().ids(), sent).withMarksFrom(now.note()).capped(today);
                memory.save(learner, true, saved, now.talks(), now.lastTalkId());
            });
        } catch (RuntimeException e) {
            log.warn("memory update failed for talk {}: {}", talk, where(e));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public TalkBrief brief(LearnerId learner, TalkSessionId talk, String goal) {
        return tx.execute(s -> {
            MemoryPort.Row row = memory.lockOrCreate(learner);
            if (!row.enabled()) {
                return new TalkBrief(MemoryPrompt.brief(null, null, TopicBank.seeds(goal, Set.of(), ThreadLocalRandom.current(), 3)), false);
            }
            MemoryNote note = row.note();
            MemoryThread opener;
            if (talk.value().equals(row.lastTalkId())) {
                int number = row.talks();
                opener = note.threads().stream().filter(t -> t.openedTalk() != null && t.openedTalk() == number).findFirst().orElse(null);
            } else {
                int number = row.talks() + 1;
                opener = note.opener(clock.today(), clock.now(), number).orElse(null);
                if (opener != null) note = note.withOpened(opener.id(), clock.now(), number);
                memory.save(learner, true, note, number, talk.value());
            }
            List<String> seeds = TopicBank.seeds(goal, Set.copyOf(note.recent()), ThreadLocalRandom.current(), 3);
            return new TalkBrief(MemoryPrompt.brief(note, opener, seeds), opener != null);
        });
    }

    public void remove(LearnerId learner, String lineId) {
        tx.executeWithoutResult(s -> {
            MemoryPort.Row row = memory.lockOrCreate(learner);
            memory.save(learner, row.enabled(), row.note().without(lineId), row.talks(), row.lastTalkId());
        });
    }

    /** Off deletes the note and stops new notes; on starts from an empty note. */
    public void setEnabled(LearnerId learner, boolean on) {
        tx.executeWithoutResult(s -> {
            MemoryPort.Row row = memory.lockOrCreate(learner);
            MemoryNote note = on && row.enabled() ? row.note() : MemoryNote.empty();
            memory.save(learner, on, note, row.talks(), row.lastTalkId());
        });
    }

    /** "Forget everything Bolo knows": empties the note; remembering stays as it was. */
    public void forget(LearnerId learner) {
        tx.executeWithoutResult(s -> {
            MemoryPort.Row row = memory.lockOrCreate(learner);
            memory.save(learner, row.enabled(), MemoryNote.empty(), row.talks(), row.lastTalkId());
        });
    }

    // ---- the model ------------------------------------------------------------------------------------------------

    private MemoryPrompt.Reply ask(TalkSessionId talk, LearnerId learner, LocalDate today, MemoryNote old, TalkRecord record) {
        List<MemoryPrompt.Said> said = record.turns().stream()
                .filter(t -> t.text() != null && !t.text().isBlank())
                .map(t -> new MemoryPrompt.Said(t.learner(), t.text())).toList();
        var request = new LlmRequest(model, MemoryPrompt.system(),
                List.of(LlmMessage.user(MemoryPrompt.input(today, notesForModel(old), said))), maxTokens, MemoryPrompt.schema(), false);
        String text;
        try {
            text = llm.stream(new TalkContext(learner, talk), request, d -> { }).done().get(60, TimeUnit.SECONDS).text();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("memory model failed for talk {}: {}", talk, where(e));
            return null;
        }
        try {
            MemoryPrompt.Reply r = JSON.readValue(text, MemoryPrompt.Reply.class);
            if (r.about() == null || r.threads() == null || r.interests() == null) throw new IllegalArgumentException("missing lists");
            return r;
        } catch (RuntimeException e) {
            log.warn("memory reply unusable for talk {}: {}", talk, e.getClass().getSimpleName());
            return null;
        }
    }

    /** The current note as the model sees it: ids and words, no opener marks. */
    static String notesForModel(MemoryNote n) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("about", n.about());
        out.put("threads", n.threads().stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.id());
            m.put("kind", t.kind().json());
            m.put("text", t.text());
            m.put("said", t.said() == null ? "" : t.said().toString());
            m.put("due", t.due() == null ? "" : t.due().toString());
            return m;
        }).toList());
        out.put("interests", n.interests());
        return JSON.writeValueAsString(out);
    }

    /**
     * The model's reply over the old note. Ids are ours: a kept id keeps its line's opener marks; an id the model made
     * up counts as a new line. A new thread was said today; a date in the future is today.
     */
    static MemoryNote apply(MemoryPrompt.Reply r, MemoryNote old, LocalDate today) {
        Map<String, MemoryThread> oldThreads = new LinkedHashMap<>();
        old.threads().forEach(t -> oldThreads.put(t.id(), t));
        Set<String> oldIds = old.ids();
        Function<String, String> id = given -> given != null && oldIds.contains(given) ? given : newId();
        List<MemoryThread> threads = new ArrayList<>();
        for (MemoryPrompt.ReplyThread t : r.threads()) {
            if (t == null || t.text() == null) continue;
            MemoryThread.Kind kind = kind(t.kind());
            if (kind == null) continue;
            MemoryThread prev = t.id() == null ? null : oldThreads.get(t.id());
            LocalDate said = prev == null ? today : date(t.said(), prev.said());
            if (said == null || said.isAfter(today)) said = today;
            threads.add(new MemoryThread(prev == null ? newId() : prev.id(), kind, t.text(), said, date(t.due(), null),
                    prev == null ? null : prev.openedAt(), prev == null ? null : prev.openedTalk()));
        }
        List<String> recent = new ArrayList<>();
        if (r.topic() != null && !r.topic().isBlank()) recent.add(r.topic().strip());
        recent.addAll(old.recent());
        return new MemoryNote(lines(r.about(), id), threads, lines(r.interests(), id), recent);
    }

    private static List<Line> lines(List<MemoryPrompt.ReplyLine> in, Function<String, String> id) {
        return in.stream().filter(l -> l != null && l.text() != null).map(l -> new Line(id.apply(l.id()), l.text())).toList();
    }

    private static MemoryThread.Kind kind(String k) {
        if (k == null) return null;
        return switch (k) {
            case "plan" -> MemoryThread.Kind.PLAN;
            case "worry" -> MemoryThread.Kind.WORRY;
            case "prep" -> MemoryThread.Kind.PREP;
            default -> null;
        };
    }

    private static LocalDate date(String s, LocalDate fallback) {
        if (s == null || s.isBlank()) return fallback;
        try {
            return LocalDate.parse(s.strip());
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }

    static String newId() {
        byte[] b = new byte[4];
        ThreadLocalRandom.current().nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    /** The exception's type and the first Bolo frame: enough to find it, never the learner's words. */
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
}
