package app.bolo.talk.application.service;

import app.bolo.common.id.TalkContext;
import app.bolo.llm.application.port.in.LlmClient;
import app.bolo.llm.application.port.in.LlmMessage;
import app.bolo.llm.application.port.in.LlmRequest;
import app.bolo.llm.application.port.in.LlmStream;
import app.bolo.llm.application.port.in.LlmUnavailableException;
import app.bolo.speech.application.port.in.StreamTranscriptionUseCase;
import app.bolo.speech.application.port.in.SynthesizeUseCase;
import app.bolo.speech.application.port.in.TranscriptListener;
import app.bolo.speech.application.port.in.TranscriptionStream;
import app.bolo.talk.application.port.in.ClientMessage;
import app.bolo.talk.application.port.in.StartTalkUseCase;
import app.bolo.talk.application.port.in.TalkConnection;
import app.bolo.talk.application.port.in.TalkOutput;
import app.bolo.talk.application.port.in.TalkView;
import app.bolo.talk.application.port.out.TurnPort;
import app.bolo.talk.domain.BuddyPrompt;
import app.bolo.talk.domain.EndReason;
import app.bolo.talk.domain.EnergyVad;
import app.bolo.talk.domain.Exchange;
import app.bolo.talk.domain.SentenceSplitter;
import app.bolo.talk.domain.TurnTaker;
import lombok.extern.slf4j.Slf4j;

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

    enum Phase { BUDDY_SPEAKING, YOUR_TURN, THINKING, ENDED }

    private static final Object END = new Object();
    private static final int FRAME_BYTES = 640;
    private static final int BYTES_PER_MS = 32;
    private static final long WATCHDOG_TICK_MS = 250;
    /** Audio may arrive this much faster than real time (plus the burst allowance) before frames are dropped. */
    private static final double MAX_AUDIO_RATE = 1.2;

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
    private final TalkSettings settings;
    private final LongSupplier nowMs;

    private final ReentrantLock lock = new ReentrantLock();
    private final EnergyVad vad = new EnergyVad();
    private final TurnTaker taker;
    private final List<Exchange> history = new ArrayList<>();
    private final Map<Integer, Integer> latencyByTurn = new LinkedHashMap<>();
    private final String system;

    private volatile boolean ended;
    private volatile long lastFrameMs;
    private volatile TranscriptionStream stt;

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

    TalkConversation(TalkView talk, TalkContext ctx, TalkOutput out, StreamTranscriptionUseCase recogniser,
                     SynthesizeUseCase voice, LlmClient llm, StartTalkUseCase talks, TurnPort turns,
                     TalkSettings settings, LongSupplier nowMs) {
        this.talk = talk;
        this.ctx = ctx;
        this.out = out;
        this.recogniser = recogniser;
        this.voice = voice;
        this.llm = llm;
        this.talks = talks;
        this.turns = turns;
        this.settings = settings;
        this.nowMs = nowMs;
        this.taker = new TurnTaker(settings.policy());
        this.system = BuddyPrompt.system(talk.goal(), talk.fearedMoment(), talk.minutes());
    }

    /** Opens the recogniser and starts Bolo's opening line, off the socket thread, and starts the watchdog. */
    void start() {
        lock.lock();
        try {
            startedMs = nowMs.getAsLong();
            lastFrameMs = startedMs;
            out.send(message("state", "value", "buddy_speaking"));
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
            judge = phase == Phase.YOUR_TURN;
            toRecogniser = judge ? pcm : new byte[pcm.length];
        } finally {
            lock.unlock();
        }
        s.write(toRecogniser);
        if (!judge) return;
        lock.lock();
        try {
            for (int off = 0; off < pcm.length && phase == Phase.YOUR_TURN; off += FRAME_BYTES) {
                byte[] frame = Arrays.copyOfRange(pcm, off, Math.min(pcm.length, off + FRAME_BYTES));
                audioMs += Math.max(1, frame.length / BYTES_PER_MS);
                handle(taker.onFrame(vad.isSpeech(samples(frame)), audioMs));
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
            if (ended || turn == null || turn != buddyTurn || !buddyDone || phase == Phase.YOUR_TURN) return;
            if (wrapping) {
                timeUp = true;
            } else {
                phase = Phase.YOUR_TURN;
                transcript = "";
                audioMs = 0;
                taker.reset();
                // Marked inside the lock but the utterance never calls back while holding its own lock (no cycle).
                TranscriptionStream s = stt;
                if (s != null) s.mark();
                out.send(message("state", "value", "your_turn"));
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
                List<Exchange> withLearner = new ArrayList<>(history);
                withLearner.add(new Exchange(Exchange.Speaker.LEARNER, transcript.trim()));
                speculation = ask(BuddyPrompt.messages(withLearner), system, transcript.trim());
            }
            case RESUMED -> {
                cancel(speculation);
                speculation = null;
            }
            case COMMIT -> commit();
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
        out.send(message("turn_end", "turn", learnerIdx));
        out.send(message("state", "value", "thinking"));
        turns.save(talk.id(), learnerIdx, "learner", said, null, null);
        history.add(new Exchange(Exchange.Speaker.LEARNER, said));

        boolean wrap = elapsedMs() >= limitMs();
        Reply reply;
        if (!wrap && speculation != null && speculation.forTranscript.equals(said)) {
            reply = speculation;
        } else {
            cancel(speculation);
            reply = ask(BuddyPrompt.messages(history), wrap ? BuddyPrompt.wrapUpSystem(system) : system, said);
        }
        speculation = null;
        wrapping = wrap;
        buddyTurn = nextIdx++;
        startSpeaker(buddyTurn, reply, commitAt);
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
        Thread.ofVirtual().start(() -> speak(idx, reply, commitAt));
    }

    private void speak(int idx, Reply reply, Long commitAt) {
        SentenceSplitter splitter = new SentenceSplitter();
        StringBuilder full = new StringBuilder();
        Integer[] serverMs = {null};
        try {
            while (!ended) {
                Object item = reply.queue.poll(settings.replyTimeoutMs(), TimeUnit.MILLISECONDS);
                if (item == null) throw new LlmUnavailableException("reply timed out", null);
                if (item == END) break;
                if (item instanceof Failed f) {
                    if (f.cause() instanceof CancellationException) return;
                    throw new LlmUnavailableException("reply failed", f.cause());
                }
                String delta = (String) item;
                full.append(delta);
                for (String sentence : splitter.push(delta)) say(idx, sentence, commitAt, serverMs);
            }
            if (ended) return;
            var tail = splitter.flush();
            if (tail.isPresent()) say(idx, tail.get(), commitAt, serverMs);
            finishBuddyTurn(idx, full.toString().trim(), serverMs[0]);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    private void say(int idx, String sentence, Long commitAt, Integer[] serverMs) {
        lock.lock();
        try {
            if (ended) return;
            if (phase == Phase.THINKING) {
                phase = Phase.BUDDY_SPEAKING;
                out.send(message("state", "value", "buddy_speaking"));
            }
            out.send(message("caption", "turn", idx, "text", sentence));
        } finally {
            lock.unlock();
        }
        // Runs on the TTS vendor's thread: never throw into it, and never block it (output only enqueues).
        voice.synthesize(ctx, sentence, chunk -> {
            if (ended) return;
            if (serverMs[0] == null && commitAt != null) serverMs[0] = (int) (nowMs.getAsLong() - commitAt);
            out.audio(chunk);
        });
    }

    private void finishBuddyTurn(int idx, String text, Integer serverMs) {
        lock.lock();
        try {
            if (ended) return;
            Integer heardMs = pendingHeard;
            pendingHeard = null;
            turns.save(talk.id(), idx, "buddy", text, serverMs, heardMs);
            history.add(new Exchange(Exchange.Speaker.BUDDY, text));
            Integer latency = heardMs != null ? heardMs : serverMs;
            if (latency != null) latencyByTurn.put(idx, latency);
            if (phase == Phase.THINKING) phase = Phase.BUDDY_SPEAKING;
            buddyDone = true;
            out.send(message("buddy_done", "turn", idx));
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
                if (now - lastFrameMs > settings.noAudioTimeoutMs()) end(EndReason.DISCONNECTED, false, true);
                else if (now - startedMs >= limitMs() + settings.wrapGraceMs()) end(EndReason.TIME_UP, false, true);
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
        lock.lock();
        try {
            if (ended) return;
            ended = true;
            phase = Phase.ENDED;
            endedMessage = message("ended", "reason", reason.dbValue(), "turns", learnerTurns);
            endedMessage.put("p50Ms", percentile(50));
            endedMessage.put("p90Ms", percentile(90));
            spec = speculation;
            spk = speaking;
        } finally {
            lock.unlock();
        }
        talks.end(talk.id(), reason);
        if (notify) out.send(endedMessage);
        cancel(spec);
        cancel(spk);
        TranscriptionStream s = stt;
        if (s != null) Thread.ofVirtual().start(s::close);
        out.close(error);
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

    private long elapsedMs() {
        return nowMs.getAsLong() - startedMs;
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
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void onFailure(Exception e) {
            fail(e);
        }
    }
}
