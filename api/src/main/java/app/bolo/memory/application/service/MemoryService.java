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
    /** Review (P3b, Critical): talks the safety check called a crisis after they ended; they're never noted. */
    private final Map<TalkSessionId, java.time.Instant> withdrawn = new ConcurrentHashMap<>();
    /** The note before each recent update, so a crisis found after the save can put it back. */
    private final Map<TalkSessionId, Restore> restorable = new ConcurrentHashMap<>();
    /** How long a talk stays withdrawable / restorable: far longer than any safety check takes. */
    private static final java.time.Duration KEEP = java.time.Duration.ofMinutes(30);

    private record Restore(LearnerId learner, MemoryNote before, Set<String> savedIds, int epoch, java.time.Instant at) {
    }

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
        // A crisis can also be found after the talk ended (a second TalkEnded): the talk is withdrawn, and if its update
        // already saved, the note goes back to how it was.
        if ("crisis".equals(e.reason())) {
            withdraw(e.id(), e.learner());
            return;
        }
        if (e.learnerTurns() < MIN_TURNS) return;
        Thread.ofVirtual().name("memory-" + e.id()).start(() -> update(e.id(), e.learner()));
    }

    /** Rewrites the learner's note from this talk. Never throws: on any failure the old note stays. */
    public void update(TalkSessionId talk, LearnerId learner) {
        ReentrantLock lock = locks.computeIfAbsent(learner, k -> new ReentrantLock());
        lock.lock();
        try {
            if (withdrawn.containsKey(talk)) return;
            MemoryPort.Row before = memory.find(learner).orElse(null);
            if (before != null && !before.enabled()) return;
            int epoch = before == null ? 0 : before.epoch();
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
                // Withdrawn for a crisis, switched off, or Forget/off/on since this update read the note: don't save.
                if (withdrawn.containsKey(talk) || !now.enabled() || now.epoch() != epoch) return;
                MemoryNote saved = fresh.keepOnly(now.note().ids(), sent).withMarksFrom(now.note()).capped(today);
                memory.save(learner, true, saved, now.talks(), now.lastTalkId());
                prune();
                restorable.put(talk, new Restore(learner, now.note(), saved.ids(), now.epoch(), clock.now()));
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
                return new TalkBrief(MemoryPrompt.brief(null, null, TopicBank.seeds(goal, Set.of(), ThreadLocalRandom.current(), 3)), false, false);
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
            return new TalkBrief(MemoryPrompt.brief(note, opener, seeds), opener != null, !note.isEmpty());
        });
    }

    /** A late crisis: never note this talk; if it was noted, put the note back (keeping what the learner did since). */
    private void withdraw(TalkSessionId talk, LearnerId learner) {
        withdrawn.put(talk, clock.now());
        ReentrantLock lock = locks.computeIfAbsent(learner, k -> new ReentrantLock());
        lock.lock();
        try {
            Restore r = restorable.remove(talk);
            if (r == null) return;
            tx.executeWithoutResult(s -> {
                MemoryPort.Row now = memory.lockOrCreate(learner);
                if (!now.enabled() || now.epoch() != r.epoch()) return;
                // Lines the update removed come back; lines the learner removed since stay removed.
                Set<String> existing = new java.util.HashSet<>(now.note().ids());
                r.before().ids().stream().filter(id -> !r.savedIds().contains(id)).forEach(existing::add);
                MemoryNote back = new MemoryNote(r.before().about(), r.before().threads(), r.before().interests(), r.before().recent())
                        .keepOnly(existing, r.before().ids()).withMarksFrom(now.note());
                memory.save(learner, true, back, now.talks(), now.lastTalkId());
            });
        } catch (RuntimeException e) {
            log.warn("memory withdraw failed for talk {}: {}", talk, where(e));
        } finally {
            lock.unlock();
        }
    }

    private void prune() {
        java.time.Instant oldest = clock.now().minus(KEEP);
        withdrawn.values().removeIf(at -> at.isBefore(oldest));
        restorable.values().removeIf(r -> r.at().isBefore(oldest));
    }

    @Override
    public MemoryView view(LearnerId learner) {
        MemoryPort.Row row = memory.find(learner).orElse(null);
        if (row == null) return new MemoryView(true, List.of(), List.of(), List.of());
        MemoryNote n = row.note();
        return new MemoryView(row.enabled(),
                n.threads().stream().map(t -> new FollowUp(t.id(), t.kind().json(), t.text(), t.said())).toList(),
                n.about().stream().map(l -> new Item(l.id(), l.text())).toList(),
                n.interests().stream().map(l -> new Item(l.id(), l.text())).toList());
    }

    /** A guest signed into an existing account: the guest's note joins the account's. */
    @EventListener
    public void onLearnerMerged(app.bolo.common.event.LearnerMerged e) {
        memory.move(e.from(), e.to());
    }

    @Override
    public void remove(LearnerId learner, String lineId) {
        tx.executeWithoutResult(s -> {
            MemoryPort.Row row = memory.lockOrCreate(learner);
            memory.save(learner, row.enabled(), row.note().without(lineId), row.talks(), row.lastTalkId());
        });
    }

    /** Off deletes the note and stops new notes; on starts from an empty note. */
    @Override
    public void setEnabled(LearnerId learner, boolean on) {
        tx.executeWithoutResult(s -> {
            MemoryPort.Row row = memory.lockOrCreate(learner);
            if (on && row.enabled()) return;
            memory.reset(learner, on);
        });
    }

    /** "Forget everything Bolo knows": empties the note; remembering stays as it was. */
    @Override
    public void forget(LearnerId learner) {
        tx.executeWithoutResult(s -> {
            MemoryPort.Row row = memory.lockOrCreate(learner);
            memory.reset(learner, row.enabled());
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
        List<MemoryThread> threads = new ArrayList<>();
        for (MemoryPrompt.ReplyThread t : r.threads()) {
            if (t == null || t.text() == null || t.text().isBlank()) continue;
            MemoryThread.Kind kind = kind(t.kind());
            if (kind == null) continue;
            MemoryThread prev = t.id() == null ? null : oldThreads.remove(t.id());
            LocalDate said = prev == null ? today : date(t.said(), prev.said());
            if (said == null || said.isAfter(today)) said = today;
            threads.add(new MemoryThread(prev == null ? newId() : prev.id(), kind, t.text(), said, date(t.due(), null),
                    prev == null ? null : prev.openedAt(), prev == null ? null : prev.openedTalk()));
        }
        List<String> recent = new ArrayList<>();
        if (r.topic() != null && !r.topic().isBlank()) recent.add(r.topic().strip());
        recent.addAll(old.recent());
        Set<String> drop = r.drop() == null ? Set.of() : Set.copyOf(r.drop());
        return new MemoryNote(merge(old.about(), r.about(), drop), threads, merge(old.interests(), r.interests(), drop), recent);
    }

    /**
     * Review (P3b, Important): about and interest lines the model left out are kept; only {@code drop} removes one. A
     * returned line with a known id rewords it; any other returned line is new. Each id appears once.
     */
    static List<Line> merge(List<Line> old, List<MemoryPrompt.ReplyLine> reply, Set<String> drop) {
        Map<String, String> reworded = new LinkedHashMap<>();
        Set<String> oldIds = new java.util.HashSet<>();
        old.forEach(l -> oldIds.add(l.id()));
        List<Line> added = new ArrayList<>();
        for (MemoryPrompt.ReplyLine l : reply) {
            if (l == null || l.text() == null || l.text().isBlank()) continue;
            if (l.id() != null && oldIds.contains(l.id())) reworded.putIfAbsent(l.id(), l.text());
            else added.add(new Line(newId(), l.text()));
        }
        List<Line> out = new ArrayList<>();
        for (Line l : old) {
            if (drop.contains(l.id())) continue;
            out.add(new Line(l.id(), reworded.getOrDefault(l.id(), l.text())));
        }
        out.addAll(added);
        return out;
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
