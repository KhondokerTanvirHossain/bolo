package app.bolo.talk.application.service;

import app.bolo.common.id.TalkContext;
import app.bolo.common.audio.Wav;
import app.bolo.llm.application.port.in.LlmClient;
import app.bolo.llm.application.port.in.LlmMessage;
import app.bolo.llm.application.port.in.LlmRequest;
import app.bolo.llm.application.port.in.LlmStream;
import app.bolo.llm.application.port.in.LlmUnavailableException;
import app.bolo.media.application.port.in.AudioStoreUseCase;
import app.bolo.speech.application.port.in.ScoredSegment;
import app.bolo.speech.application.port.in.ScoredWord;
import app.bolo.speech.application.port.in.StreamTranscriptionUseCase;
import app.bolo.speech.application.port.in.SynthesizeUseCase;
import app.bolo.speech.application.port.in.TranscriptListener;
import app.bolo.speech.application.port.in.TranscriptionStream;
import app.bolo.speech.application.port.in.Voice;
import app.bolo.talk.application.port.in.ClientMessage;
import app.bolo.talk.application.port.in.StartTalkUseCase;
import app.bolo.talk.application.port.in.TalkEnded;
import app.bolo.talk.application.port.in.TalkConnection;
import app.bolo.talk.application.port.in.TalkOutput;
import app.bolo.talk.application.port.in.TalkView;
import app.bolo.talk.application.port.out.TurnPort;
import app.bolo.talk.domain.BuddyPrompt;
import app.bolo.talk.domain.CrisisScreen;
import app.bolo.talk.domain.CrisisScript;
import app.bolo.talk.domain.QuietClock;
import app.bolo.talk.domain.TalkIntent;
import app.bolo.talk.domain.EndReason;
import app.bolo.talk.domain.EnergyVad;
import app.bolo.talk.domain.Exchange;
import app.bolo.talk.domain.SentenceSplitter;
import app.bolo.talk.domain.TurnTaker;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * One live talk (architecture §4.1). Bolo speaks first; while Bolo speaks the learner's audio reaches the recogniser
 * as silence, so Bolo never hears itself through laptop speakers. On the learner's turn the {@link TurnTaker} decides,
 * frame by frame in audio time, when to start a speculative reply and when the turn is over. Each reply streams into
 * its own queue; the speaker thread drains the committed one sentence by sentence into TTS. Turn indexes run
 * Buddy 0, learner 1, Buddy 2, ...
 * <p>
 * Threading: state lives behind one {@link ReentrantLock} (not {@code synchronized}, which pins virtual threads to
 * their carrier on Java 21 - the box has one CPU). Calls into the speech SDK (write, mark, close) are made outside
 * the lock, because the SDK's own callback threads come back in through it. Output only enqueues. A watchdog ends a
 * talk whose browser has gone quiet or that has run past its time in any phase. Logs never carry transcript text.
 */
@Slf4j
final class TalkConversation implements TalkConnection {

    enum Phase { BUDDY_SPEAKING, YOUR_TURN, THINKING, PAUSED, ENDED }
    static final long LIVE_EVERY_MS = 200;

    private static final Object END = new Object();
    private static final int FRAME_BYTES = 640;
    private static final int BYTES_PER_MS = 32;
    private static final long WATCHDOG_TICK_MS = 250;
    /** Audio may arrive this much faster than real time (plus the burst allowance) before frames are dropped. */
    private static final double MAX_AUDIO_RATE = 1.2;
    /** A learner turn's audio is kept up to this long (90 s of 16 kHz PCM). */
    private static final int MAX_TURN_BYTES = 90 * 1000 * BYTES_PER_MS;

    private record Failed(Throwable cause) {
    }

    /** One reply from the LLM: its deltas in order, then END or Failed. */
    private static final class Reply {
        final String forTranscript;
        final BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
        LlmStream stream;

        Reply(String forTranscript) {
            this.forTranscript = forTranscript;
        }

        void cancel() {
            if (stream != null) stream.cancel();
        }
    }

    private final TalkView talk;
    private final TalkContext ctx;
    private final TalkOutput out;
    private final StreamTranscriptionUseCase recogniser;
    private final SynthesizeUseCase voice;
    private final LlmClient llm;
    private final StartTalkUseCase talks;
    private final TurnPort turns;
    private final AudioStoreUseCase audioStore;
    private final TalkSettings settings;
    private final LongSupplier nowMs;
    private final Consumer<TalkEnded> onEnded;
    private final SafetyGuard safety;
    private final Starters starters;

    private final ReentrantLock lock = new ReentrantLock();
    private final EnergyVad vad = new EnergyVad();
    private final TurnTaker taker;
    private final List<Exchange> history = new ArrayList<>();
    private final Map<Integer, Integer> latencyByTurn = new LinkedHashMap<>();
    private final String system;

    private volatile boolean ended;
    private volatile long lastFrameMs;
    private volatile TranscriptionStream stt;
    /** Stream time at which the current recogniser opened (it restarts at 0 after a pause). */
    private volatile long sttBaseMs;
    /** Bumped whenever a new Buddy line takes over; an older speaker stops sending (e.g. a crisis replaces a reply). */
    private volatile int speakGen;
    private volatile boolean slow;
    /** A keyword-matched turn is waiting for the classifier: ending now still counts as a crisis. */
    private volatile boolean crisisPending;
    /** Bumped by every pause and resume, so a slow recogniser reopen from an older resume is thrown away. */
    private int resumeToken;
    private boolean startersAfterReply;

    // Guarded by lock.
    private Phase phase = Phase.BUDDY_SPEAKING;
    private String transcript = "";
    private long audioMs;
    private long acceptedAudioMs;
    private int nextIdx;
    private int buddyTurn = -1;
    private boolean buddyDone;
    private Integer pendingHeard;
    private boolean wrapping;
    private int learnerTurns;
    private long startedMs;
    private Reply speculation;
    private Reply speaking;
    private QuietClock quiet;
    private int shortStreak;
    private String lastBuddyText = "";
    /** P3a: the learner's live words this turn (TLK-8 changed 2026-11-12): sent at most every LIVE_EVERY_MS. */
    private boolean youSent;
    private String lastYouText = "";
    private long lastYouMs = Long.MIN_VALUE / 2;
    /** P3a: the turn repeatOnRequest made, and the earlier Bolo turn it repeats (the screen replays that bubble). */
    private int repeatTurn = -1;
    private int repeatOf = -1;
    private volatile long pausedAtMs = -1;
    private volatile long pausedTotalMs;
    private EndReason endAfterSpeaking;
    /** Audio ms written to the recogniser so far: word times come back in this clock. */
    private long streamMs;
    private long turnStreamStartMs;
    private final ByteArrayOutputStream turnPcm = new ByteArrayOutputStream();
    private final List<ScoredWord> turnWords = new ArrayList<>();
    private int lastLearnerIdx = -1;
    private long lastTurnStreamStartMs;
    private final List<ScoredWord> lastTurnWords = new ArrayList<>();

    TalkConversation(TalkView talk, TalkContext ctx, TalkOutput out, StreamTranscriptionUseCase recogniser,
                     SynthesizeUseCase voice, LlmClient llm, StartTalkUseCase talks, TurnPort turns,
                     AudioStoreUseCase audioStore, TalkSettings settings, LongSupplier nowMs, Consumer<TalkEnded> onEnded,
                     SafetyGuard safety, Starters starters) {
        this(talk, ctx, out, recogniser, voice, llm, talks, turns, audioStore, settings, nowMs, onEnded, safety, starters, null,
                BuddyPrompt.Opening.FROM_GOAL);
    }

    /** P3b: {@code brief} is what Bolo remembers and fresh ideas (TLK-11 changed 2026-11-13); {@code opening}: how Bolo opens. */
    TalkConversation(TalkView talk, TalkContext ctx, TalkOutput out, StreamTranscriptionUseCase recogniser,
                     SynthesizeUseCase voice, LlmClient llm, StartTalkUseCase talks, TurnPort turns,
                     AudioStoreUseCase audioStore, TalkSettings settings, LongSupplier nowMs, Consumer<TalkEnded> onEnded,
                     SafetyGuard safety, Starters starters, String brief, BuddyPrompt.Opening opening) {
        this.talk = talk;
        this.ctx = ctx;
        this.out = out;
        this.recogniser = recogniser;
        this.voice = voice;
        this.llm = llm;
        this.talks = talks;
        this.turns = turns;
        this.audioStore = audioStore;
        this.settings = settings;
        this.nowMs = nowMs;
        this.onEnded = onEnded;
        this.safety = safety;
        this.starters = starters;
        this.slow = talk.slowVoice();
        this.quiet = new QuietClock(settings.quietEncourageMs(), settings.quietHintMs(), settings.quietPauseMs());
        this.taker = new TurnTaker(settings.policy());
        this.system = BuddyPrompt.system(talk.goal(), talk.fearedMoment(), talk.minutes(), brief, opening, talk.mood());
    }

    /** Opens the recogniser and starts Bolo's opening line, off the socket thread, and starts the watchdog. */
    void start() {
        lock.lock();
        try {
            startedMs = nowMs.getAsLong();
            lastFrameMs = startedMs;
            out.send(message("state", "value", "buddy_speaking", "leftMs", leftMs()));
        } finally {
            lock.unlock();
        }
        Thread.ofVirtual().start(() -> {
            try {
                TranscriptionStream opened = recogniser.open(ctx, new Listener());
                if (ended) {
                    opened.close();
                    return;
                }
                stt = opened;
                lock.lock();
                try {
                    if (ended) return;
                    Reply opening = ask(BuddyPrompt.messages(List.of()), system, "");
                    buddyTurn = nextIdx++;
                    startSpeaker(buddyTurn, opening, null);
                } finally {
                    lock.unlock();
                }
            } catch (RuntimeException e) {
                fail(e);
            }
        });
        Thread.ofVirtual().start(this::watch);
    }

    // ---- socket input -------------------------------------------------------------------------------------------

    /** Called by the socket for each audio message, one at a time and in order. */
    @Override
    public void onAudio(byte[] pcm) {
        long now = nowMs.getAsLong();
        lastFrameMs = now;
        TranscriptionStream s = stt;
        if (ended || s == null) return;
        byte[] toRecogniser;
        boolean judge;
        lock.lock();
        try {
            long ms = pcm.length / BYTES_PER_MS;
            long allowed = (long) ((now - startedMs) * MAX_AUDIO_RATE) + settings.burstAllowanceMs();
            if (acceptedAudioMs + ms > allowed) return;
            acceptedAudioMs += ms;
            streamMs += ms;
            judge = phase == Phase.YOUR_TURN;
            toRecogniser = judge ? pcm : new byte[pcm.length];
            if (judge && turnPcm.size() + pcm.length <= MAX_TURN_BYTES) turnPcm.writeBytes(pcm);
        } finally {
            lock.unlock();
        }
        s.write(toRecogniser);
        if (!judge) return;
        lock.lock();
        try {
            for (int off = 0; off < pcm.length && phase == Phase.YOUR_TURN; off += FRAME_BYTES) {
                byte[] frame = Arrays.copyOfRange(pcm, off, Math.min(pcm.length, off + FRAME_BYTES));
                long frameMs = Math.max(1, frame.length / BYTES_PER_MS);
                audioMs += frameMs;
                boolean speech = vad.isSpeech(samples(frame));
                if (speech) quiet.onSpeech();
                else onQuiet(quiet.onQuiet(frameMs));
                if (phase == Phase.YOUR_TURN) handle(taker.onFrame(speech, audioMs));
            }
            if (phase == Phase.YOUR_TURN && elapsedMs() >= limitMs() + settings.wrapGraceMs()) wrapUpNow();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onClient(ClientMessage m) {
        switch (m.type() == null ? "" : m.type()) {
            case "played" -> played(m.turn());
            case "done" -> done();
            case "heard" -> heard(m.turn(), m.ms());
            case "end" -> end(EndReason.LEARNER_ENDED, false, true);
            case "pause" -> pause("learner");
            case "resume" -> resume();
            case "repeat" -> repeatOnRequest();
            default -> { }
        }
    }

    @Override
    public void onClosed() {
        end(EndReason.DISCONNECTED, false, false);
    }

    private void played(Integer turn) {
        boolean timeUp = false;
        lock.lock();
        try {
            if (ended || turn == null || turn != buddyTurn || !buddyDone || phase == Phase.YOUR_TURN || phase == Phase.PAUSED) return;
            if (endAfterSpeaking != null) return;
            if (wrapping) {
                timeUp = true;
            } else {
                phase = Phase.YOUR_TURN;
                transcript = "";
                youSent = false;
                lastYouText = "";
                lastYouMs = Long.MIN_VALUE / 2;
                audioMs = 0;
                taker.reset();
                turnStreamStartMs = streamMs;
                turnPcm.reset();
                turnWords.clear();
                // Marked inside the lock but the utterance never calls back while holding its own lock (no cycle).
                TranscriptionStream s = stt;
                if (s != null) s.mark();
                out.send(message("state", "value", "your_turn", "leftMs", leftMs()));
            }
        } finally {
            lock.unlock();
        }
        if (timeUp) end(EndReason.TIME_UP, false, true);
    }

    private void done() {
        lock.lock();
        try {
            if (phase == Phase.YOUR_TURN) handle(taker.onDone(audioMs));
        } finally {
            lock.unlock();
        }
    }

    /** The browser's measure of turn end → Bolo heard. It usually arrives while Bolo is still speaking that turn. */
    private void heard(Integer turn, Integer ms) {
        if (turn == null || ms == null || ms < 0 || ms > 60_000) return;
        lock.lock();
        try {
            if (ended) return;
            if (turn == buddyTurn && !buddyDone) {
                pendingHeard = ms;
                return;
            }
            if (!latencyByTurn.containsKey(turn)) return;
            latencyByTurn.put(turn, ms);
            turns.heard(talk.id(), turn, ms);
        } finally {
            lock.unlock();
        }
    }

    // ---- turn taking (lock held) ------------------------------------------------------------------------------------

    private void handle(TurnTaker.Decision d) {
        switch (d) {
            case SPECULATE -> {
                cancel(speculation);
                // "Sorry?" / "Slower please" are answered without the model: nothing to speculate.
                if (TalkIntent.of(transcript) != TalkIntent.NONE) {
                    speculation = null;
                    return;
                }
                List<Exchange> withLearner = new ArrayList<>(history);
                withLearner.add(new Exchange(Exchange.Speaker.LEARNER, transcript.trim()));
                speculation = ask(BuddyPrompt.messages(withLearner), systemFor(transcript.trim()), transcript.trim());
            }
            case RESUMED -> {
                cancel(speculation);
                speculation = null;
            }
            case COMMIT -> commit();
            case UNCLEAR -> {
                dropLive();
                cue(BuddyPrompt.UNCLEAR_CUE);
            }
            case NONE -> { }
        }
    }

    private void commit() {
        String said = transcript.trim();
        if (said.isEmpty()) return;
        long commitAt = nowMs.getAsLong();
        phase = Phase.THINKING;
        learnerTurns++;
        int learnerIdx = nextIdx++;
        out.send(message("turn_end", "turn", learnerIdx, "text", said));
        youSent = false;
        out.send(message("state", "value", "thinking", "leftMs", leftMs()));
        turns.saveLearner(talk.id(), learnerIdx, said, keepTurnAudio(learnerIdx), turnStreamStartMs, List.copyOf(turnWords));
        lastLearnerIdx = learnerIdx;
        lastTurnStreamStartMs = turnStreamStartMs;
        lastTurnWords.clear();
        lastTurnWords.addAll(turnWords);
        history.add(new Exchange(Exchange.Speaker.LEARNER, said));

        // TLK-14 first: a turn that trips the keyword screen waits for the classifier before Bolo says anything, and a
        // classifier that fails or is slow counts as a crisis (fail closed).
        if (CrisisScreen.mayBeCrisis(said)) {
            cancel(speculation);
            speculation = null;
            crisisPending = true;
            Thread.ofVirtual().start(() -> {
                boolean crisis;
                try {
                    crisis = safety.isCrisis(ctx, said).get(3, TimeUnit.SECONDS);
                } catch (Exception e) {
                    crisis = true;
                }
                if (crisis) {
                    crisis();
                } else {
                    crisisPending = false;
                    replyTo(said, commitAt, false);
                }
            });
            return;
        }

        // TLK-13: "Sorry?" and "Slower please" are answered in Java, with no model call.
        TalkIntent intent = TalkIntent.of(said);
        if (intent != TalkIntent.NONE && !lastBuddyText.isBlank()) {
            cancel(speculation);
            speculation = null;
            if (intent == TalkIntent.SLOWER && !slow) {
                slow = true;
                // US-2.7: ask whether to keep the slower Bolo; the web asks, Settings saves it.
                out.send(message("slower"));
            }
            history.add(new Exchange(Exchange.Speaker.BUDDY, lastBuddyText));
            buddyTurn = nextIdx++;
            startSpeaker(buddyTurn, fixed(lastBuddyText), commitAt);
        } else {
            replyTo(said, commitAt, true);
        }
        // Every other turn is checked in parallel; a late "crisis" stops Bolo mid-reply (or, after the end, withdraws
        // the debrief). A failed check here means no crisis: the keyword screen already passed.
        var check = safety.isCrisis(ctx, said);
        if (check != null) check.handle((c, e) -> e == null && Boolean.TRUE.equals(c)).thenAccept(c -> {
            if (!c) return;
            if (ended) onEnded.accept(new TalkEnded(talk.id(), ctx.learnerId(), learnerTurns, EndReason.CRISIS.dbValue()));
            else crisis();
        });
    }

    /** Bolo's reply to a committed learner turn (lock may or may not be held by the caller; it is taken here). */
    private void replyTo(String said, long commitAt, boolean mayReuseSpeculation) {
        lock.lock();
        try {
            // Paused while the safety check ran: say nothing now; resume asks the last question again.
            if (ended || endAfterSpeaking != null || phase == Phase.PAUSED) return;
            boolean wrap = elapsedMs() >= limitMs();
            String sys = wrap ? BuddyPrompt.wrapUpSystem(system) : systemFor(said);
            shortStreak = words(said).split(" ").length < 5 ? shortStreak + 1 : 0;
            if (shortStreak >= 3) {
                // One starter card, for the question Bolo is about to ask (sent when that line is done).
                startersAfterReply = true;
                shortStreak = 0;
            }
            Reply reply;
            if (mayReuseSpeculation && !wrap && speculation != null && sameWords(speculation.forTranscript, said)) {
                reply = speculation;
            } else {
                cancel(speculation);
                reply = ask(BuddyPrompt.messages(history), sys, said);
            }
            speculation = null;
            wrapping = wrap;
            buddyTurn = nextIdx++;
            startSpeaker(buddyTurn, reply, commitAt);
        } finally {
            lock.unlock();
        }
    }

    /** TLK-5: a very short answer gets a specific follow-up; the third in a row also gets a starter. */
    private String systemFor(String said) {
        if (words(said).split(" ").length >= 5) return system;
        return BuddyPrompt.shortAnswerSystem(system, shortStreak + 1 >= 3);
    }

    /** A Bolo line driven by a cue instead of a learner turn: silence (TLK-6), Bangla or unclear speech (TLK-7), resume. */
    private void cue(String cue) {
        phase = Phase.THINKING;
        out.send(message("state", "value", "thinking", "leftMs", leftMs()));
        cancel(speculation);
        speculation = null;
        history.add(new Exchange(Exchange.Speaker.LEARNER, cue));
        buddyTurn = nextIdx++;
        startSpeaker(buddyTurn, ask(BuddyPrompt.messages(history), system, ""), null);
    }

    private void onQuiet(QuietClock.Stage stage) {
        switch (stage) {
            case ENCOURAGE -> cue(BuddyPrompt.ENCOURAGE_CUE);
            case HINT -> sendStartersAsync();
            case PAUSE -> {
                lock.unlock();
                try {
                    pause("quiet");
                } finally {
                    lock.lock();
                }
            }
            case NONE -> { }
        }
    }

    private void sendStartersAsync() {
        String line = lastBuddyText;
        Thread.ofVirtual().start(() -> {
            List<String> s = starters.forLine(ctx, line);
            if (!ended) out.send(message("hint", "starters", s));
        });
    }

    /** A Bolo line already known (repeat, crisis): no model call. */
    private static Reply fixed(String text) {
        Reply r = new Reply(text);
        r.queue.add(text);
        r.queue.add(END);
        return r;
    }

    /** The talk ran past its time plus grace while the learner held the floor: say goodbye now. */
    private void wrapUpNow() {
        if (!transcript.isBlank()) {
            commit();
            return;
        }
        phase = Phase.THINKING;
        cancel(speculation);
        speculation = null;
        wrapping = true;
        buddyTurn = nextIdx++;
        startSpeaker(buddyTurn, ask(BuddyPrompt.messages(history), BuddyPrompt.wrapUpSystem(system), ""), null);
    }

    // ---- Bolo speaking --------------------------------------------------------------------------------------------

    private Reply ask(List<Exchange> messages, String systemPrompt, String forTranscript) {
        Reply reply = new Reply(forTranscript);
        List<LlmMessage> llmMessages = messages.stream()
                .map(e -> e.speaker() == Exchange.Speaker.BUDDY ? LlmMessage.assistant(e.text()) : LlmMessage.user(e.text()))
                .toList();
        reply.stream = llm.stream(ctx, new LlmRequest(settings.model(), systemPrompt, llmMessages, settings.maxTokens()),
                reply.queue::add);
        reply.stream.done().whenComplete((r, e) -> reply.queue.add(e == null ? END : new Failed(e)));
        return reply;
    }

    private void startSpeaker(int idx, Reply reply, Long commitAt) {
        buddyDone = false;
        pendingHeard = null;
        speaking = reply;
        int gen = ++speakGen;
        Thread.ofVirtual().start(() -> speak(idx, reply, commitAt, gen));
    }

    private void speak(int idx, Reply reply, Long commitAt, int gen) {
        SentenceSplitter splitter = new SentenceSplitter();
        StringBuilder full = new StringBuilder();
        Integer[] serverMs = {null};
        try {
            while (!ended && gen == speakGen) {
                Object item = reply.queue.poll(settings.replyTimeoutMs(), TimeUnit.MILLISECONDS);
                if (item == null) throw new LlmUnavailableException("reply timed out", null);
                if (item == END) break;
                if (item instanceof Failed f) {
                    if (f.cause() instanceof CancellationException) return;
                    throw new LlmUnavailableException("reply failed", f.cause());
                }
                String delta = (String) item;
                full.append(delta);
                for (String sentence : splitter.push(delta)) say(idx, sentence, commitAt, serverMs, gen);
            }
            if (ended || gen != speakGen) return;
            var tail = splitter.flush();
            if (tail.isPresent()) say(idx, tail.get(), commitAt, serverMs, gen);
            finishBuddyTurn(idx, full.toString().trim(), serverMs[0], gen);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    private void say(int idx, String sentence, Long commitAt, Integer[] serverMs, int gen) {
        lock.lock();
        try {
            if (ended || gen != speakGen) return;
            if (phase == Phase.THINKING) {
                phase = Phase.BUDDY_SPEAKING;
                out.send(message("state", "value", "buddy_speaking", "leftMs", leftMs()));
            }
            out.send(idx == repeatTurn
                    ? message("caption", "turn", idx, "text", sentence, "repeatOf", repeatOf)
                    : message("caption", "turn", idx, "text", sentence));
        } finally {
            lock.unlock();
        }
        // Runs on the TTS vendor's thread: never throw into it, and never block it (output only enqueues).
        voice.synthesize(ctx, slow ? Voice.BUDDY_SLOW : Voice.BUDDY, talk.voice(), sentence, chunk -> {
            if (ended || gen != speakGen) return;
            if (serverMs[0] == null && commitAt != null) serverMs[0] = (int) (nowMs.getAsLong() - commitAt);
            out.audio(chunk);
        });
    }

    private void finishBuddyTurn(int idx, String text, Integer serverMs, int gen) {
        EndReason thenEnd;
        lock.lock();
        try {
            if (ended || gen != speakGen) return;
            Integer heardMs = pendingHeard;
            pendingHeard = null;
            turns.save(talk.id(), idx, "buddy", text, serverMs, heardMs);
            history.add(new Exchange(Exchange.Speaker.BUDDY, text));
            Integer latency = heardMs != null ? heardMs : serverMs;
            if (latency != null) latencyByTurn.put(idx, latency);
            if (phase == Phase.THINKING) phase = Phase.BUDDY_SPEAKING;
            buddyDone = true;
            if (!text.isBlank()) lastBuddyText = text;
            out.send(message("buddy_done", "turn", idx));
            thenEnd = endAfterSpeaking;
            if (startersAfterReply && thenEnd == null) {
                startersAfterReply = false;
                sendStartersAsync();
            }
        } finally {
            lock.unlock();
        }
        if (thenEnd != null) end(thenEnd, false, true);
    }

    // ---- pause, resume, repeat, crisis ------------------------------------------------------------------------------

    /** TLK-10: the clock and the no-audio watchdog stop; the recogniser closes (freeing the vendor's one live slot). */
    private void pause(String reason) {
        TranscriptionStream s;
        lock.lock();
        try {
            if (ended || phase == Phase.PAUSED || endAfterSpeaking != null) return;
            if (phase == Phase.YOUR_TURN) dropLive();
            phase = Phase.PAUSED;
            resumeToken++;
            pausedAtMs = nowMs.getAsLong();
            cancel(speculation);
            speculation = null;
            ++speakGen;
            s = stt;
            stt = null;
            out.send(message("paused", "reason", reason, "leftMs", leftMs()));
        } finally {
            lock.unlock();
        }
        if (s != null) Thread.ofVirtual().start(s::close);
    }

    /** Back from a pause: a fresh recogniser, then Bolo welcomes the learner back and asks its last question again. */
    private void resume() {
        int token;
        lock.lock();
        try {
            if (ended || phase != Phase.PAUSED) return;
            token = ++resumeToken;
            long now = nowMs.getAsLong();
            pausedTotalMs += now - pausedAtMs;
            pausedAtMs = -1;
            lastFrameMs = now;
            phase = Phase.THINKING;
            out.send(message("resumed", "leftMs", leftMs()));
        } finally {
            lock.unlock();
        }
        Thread.ofVirtual().start(() -> {
            try {
                TranscriptionStream opened = recogniser.open(ctx, new Listener());
                lock.lock();
                try {
                    // Paused (or ended) again while this recogniser was opening: it belongs to nobody.
                    if (ended || token != resumeToken || phase != Phase.THINKING) {
                        opened.close();
                        return;
                    }
                    sttBaseMs = streamMs;
                    stt = opened;
                    quiet.onSpeech();
                    cue(BuddyPrompt.RESUME_CUE);
                } finally {
                    lock.unlock();
                }
            } catch (RuntimeException e) {
                fail(e);
            }
        });
    }

    /** The replay icon (TLK-13): say Bolo's last line again. */
    private void repeatOnRequest() {
        lock.lock();
        try {
            if (ended || phase != Phase.YOUR_TURN || lastBuddyText.isBlank()) return;
            phase = Phase.THINKING;
            quiet.onSpeech();
            history.add(new Exchange(Exchange.Speaker.LEARNER, "(asks you to repeat that)"));
            history.add(new Exchange(Exchange.Speaker.BUDDY, lastBuddyText));
            dropLive();
            repeatOf = buddyTurn;
            buddyTurn = nextIdx++;
            repeatTurn = buddyTurn;
            startSpeaker(buddyTurn, fixed(lastBuddyText), null);
        } finally {
            lock.unlock();
        }
    }

    /**
     * TLK-14: stop whatever Bolo was about to say, show local help, speak the approved script (never generated), and
     * end the practice (no debrief follows).
     */
    private void crisis() {
        lock.lock();
        try {
            if (ended || endAfterSpeaking != null) return;
            log.warn("talk {} crisis response", talk.id());
            cancel(speculation);
            speculation = null;
            cancel(speaking);
            endAfterSpeaking = EndReason.CRISIS;
            phase = Phase.THINKING;
            out.send(crisisMessage());
            buddyTurn = nextIdx++;
            startSpeaker(buddyTurn, fixed(String.join(" ", CrisisScript.LINES)), null);
        } finally {
            lock.unlock();
        }
    }

    // ---- watchdog and endings ---------------------------------------------------------------------------------------

    /** Ends a talk whose browser has gone quiet (half-open phone connection) or that ran past time + grace in any phase. */
    private void watch() {
        try {
            while (!ended) {
                Thread.sleep(WATCHDOG_TICK_MS);
                long now = nowMs.getAsLong();
                long pausedAt = pausedAtMs;
                if (pausedAt >= 0) {
                    if (now - pausedAt > settings.pausedMaxMs()) end(EndReason.DISCONNECTED, false, true);
                } else if (now - lastFrameMs > settings.noAudioTimeoutMs()) {
                    end(EndReason.DISCONNECTED, false, true);
                } else if (elapsedMs() >= limitMs() + settings.wrapGraceMs()) {
                    end(EndReason.TIME_UP, false, true);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void fail(Throwable e) {
        if (ended) return;
        log.warn("talk {} lost its voice: {}", talk.id(), e.getClass().getSimpleName());
        end(EndReason.VOICE_UNAVAILABLE, true, true);
    }

    /**
     * Ends the talk once: the session row is closed first (whoever hears "ended" can rely on the free slot), then the
     * browser is told (unless it already left), replies are cancelled, the recogniser and the output are closed.
     */
    private void end(EndReason reason, boolean error, boolean notify) {
        Map<String, Object> endedMessage;
        Reply spec;
        Reply spk;
        int turnsTaken;
        Map<String, Object> crisisCard = null;
        lock.lock();
        try {
            if (ended) return;
            // Ending while a keyword turn's safety check is still out is treated as a crisis: help is shown and no
            // debrief is made of that turn.
            if (crisisPending && reason != EndReason.CRISIS && reason != EndReason.VOICE_UNAVAILABLE) {
                reason = EndReason.CRISIS;
                crisisCard = crisisMessage();
            }
            ended = true;
            phase = Phase.ENDED;
            endedMessage = message("ended", "reason", reason.dbValue(), "turns", learnerTurns);
            endedMessage.put("p50Ms", percentile(50));
            endedMessage.put("p90Ms", percentile(90));
            spec = speculation;
            spk = speaking;
            turnsTaken = learnerTurns;
        } finally {
            lock.unlock();
        }
        talks.end(talk.id(), reason);
        if (crisisCard != null && notify) out.send(crisisCard);
        try {
            onEnded.accept(new TalkEnded(talk.id(), ctx.learnerId(), turnsTaken, reason.dbValue()));
        } catch (RuntimeException e) {
            log.warn("talk {} end listener failed: {}", talk.id(), e.getClass().getSimpleName());
        }
        if (notify) out.send(endedMessage);
        cancel(spec);
        cancel(spk);
        TranscriptionStream s = stt;
        if (s != null) Thread.ofVirtual().start(s::close);
        out.close(error);
    }

    /** Stores the learner turn's audio encrypted; a storage failure loses the clip, never the talk. */
    private String keepTurnAudio(int idx) {
        String key = "talk/" + talk.id() + "/turn-" + idx + ".wav";
        try {
            audioStore.put(key, Wav.wrap(turnPcm.toByteArray(), 16000));
            return key;
        } catch (RuntimeException e) {
            log.warn("talk {} turn {} audio not kept: {}", talk.id(), idx, e.getClass().getSimpleName());
            return null;
        }
    }

    /** The recogniser's final text adds capitals and punctuation to the partial a speculation started from. */
    static boolean sameWords(String a, String b) {
        return words(a).equals(words(b));
    }

    private static String words(String s) {
        return s == null ? "" : s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9' ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static Map<String, Object> crisisMessage() {
        List<Map<String, Object>> help = CrisisScript.HELP.stream()
                .map(h -> Map.<String, Object>of("name", h.name(), "number", h.number(), "hours", h.hours())).toList();
        return message("crisis", "title", CrisisScript.TITLE, "lines", CrisisScript.LINES, "help", help);
    }

    private static void cancel(Reply r) {
        if (r != null) r.cancel();
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private Integer percentile(int p) {
        List<Integer> values = latencyByTurn.values().stream().sorted().toList();
        if (values.isEmpty()) return null;
        int rank = (int) Math.ceil(p / 100.0 * values.size());
        return values.get(Math.max(0, rank - 1));
    }

    /** P3a: time left for the screen's countdown (TLK-16 changed 2026-11-12); negative once time is up. */
    private long leftMs() {
        return limitMs() - elapsedMs();
    }

    /** P3a: the learner's live words for a turn that isn't kept (unclear, paused, repeat asked): the screen drops them. */
    private void dropLive() {
        if (!youSent) return;
        out.send(message("you_drop", "turn", nextIdx));
        youSent = false;
    }

    /** Talk time, not counting pauses. */
    private long elapsedMs() {
        long pausedNow = pausedAtMs >= 0 ? nowMs.getAsLong() - pausedAtMs : 0;
        return nowMs.getAsLong() - startedMs - pausedTotalMs - pausedNow;
    }

    private long limitMs() {
        return talk.minutes() * settings.minuteMs();
    }

    private static short[] samples(byte[] pcm) {
        short[] s = new short[pcm.length / 2];
        ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(s);
        return s;
    }

    private static Map<String, Object> message(String type, Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        for (int i = 0; i + 1 < keyValues.length; i += 2) m.put((String) keyValues[i], keyValues[i + 1]);
        return m;
    }

    /** Called on the recogniser's thread; takes the lock briefly and never calls back into the recogniser. */
    private final class Listener implements TranscriptListener {

        @Override
        public void onPartial(String textSoFar) {
            lock.lock();
            try {
                if (phase != Phase.YOUR_TURN) return;
                transcript = textSoFar == null ? "" : textSoFar;
                taker.onTranscript(transcript);
                // P3a: the learner's own screen shows their words live; at most every LIVE_EVERY_MS, never stored.
                long now = nowMs.getAsLong();
                if (!transcript.isBlank() && !transcript.equals(lastYouText) && now - lastYouMs >= LIVE_EVERY_MS) {
                    out.send(message("you", "turn", nextIdx, "text", transcript));
                    lastYouText = transcript;
                    lastYouMs = now;
                    youSent = true;
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onFailure(Exception e) {
            fail(e);
        }

        /** Scores belong to the turn being spoken, or to the last saved turn when they lag its end. */
        @Override
        public void onScored(ScoredSegment segment) {
            lock.lock();
            try {
                if (ended || segment.words().isEmpty()) return;
                long base = sttBaseMs;
                if (base != 0) {
                    segment = new ScoredSegment(segment.text(), segment.words().stream().map(w -> new ScoredWord(w.word(),
                            w.startMs() + base, w.endMs() + base, w.accuracy(), w.errorType(), w.phonemes())).toList());
                }
                if (phase == Phase.YOUR_TURN) {
                    turnWords.addAll(segment.words());
                } else if (lastLearnerIdx >= 0 && segment.words().getFirst().startMs() >= lastTurnStreamStartMs) {
                    lastTurnWords.addAll(segment.words());
                    turns.updateScored(talk.id(), lastLearnerIdx, List.copyOf(lastTurnWords));
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
