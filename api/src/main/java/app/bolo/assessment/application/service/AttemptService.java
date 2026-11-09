package app.bolo.assessment.application.service;

import app.bolo.assessment.application.port.in.AttemptUseCase;
import app.bolo.assessment.application.port.out.AttemptPort;
import app.bolo.assessment.domain.AttemptResult;
import app.bolo.assessment.domain.AttemptScorer;
import app.bolo.assessment.domain.PracticeDeck;
import app.bolo.assessment.domain.SoundPattern;
import app.bolo.assessment.domain.SpokenSound;
import app.bolo.assessment.domain.SpokenWord;
import app.bolo.common.error.RefusedException;
import app.bolo.common.error.TooManyRequestsException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkContext;
import app.bolo.common.time.BoloClock;
import app.bolo.learner.application.port.in.CurrentLearnerUseCase;
import app.bolo.speech.application.port.in.AssessPronunciationUseCase;
import app.bolo.speech.application.port.in.PronunciationResult;
import app.bolo.speech.application.port.in.SpeechBusyException;
import app.bolo.talk.application.port.in.StartTalkUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One read-aloud attempt: checked (consent, a real 16 kHz mono WAV of ≤ 15 s, a reference of ≤ 30 words, at most
 * N attempts a minute), assessed against the reference by the speech vendor, scored for clarity, stored (scores only).
 */
@Service
public class AttemptService implements AttemptUseCase, app.bolo.assessment.application.port.in.PracticeDaysUseCase {

    @Override
    public java.util.List<Instant> practiceDays(LearnerId learner) {
        return attempts.attemptTimes(learner);
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    static final int MAX_WORDS = 30;
    /** 15 s of reading, plus half a second of slack for the browser's stop timer. */
    static final int MAX_DATA_BYTES = 15_500 * 32;

    private final AssessPronunciationUseCase vendor;
    private final AttemptPort attempts;
    private final CurrentLearnerUseCase learners;
    private final BoloClock clock;
    private final StartTalkUseCase talks;
    private final int perMinute;
    private final app.bolo.learner.application.port.in.VoiceAccessUseCase voiceAccess;
    private final Map<LearnerId, Deque<Instant>> recent = new ConcurrentHashMap<>();

    public AttemptService(AssessPronunciationUseCase vendor, AttemptPort attempts, CurrentLearnerUseCase learners, BoloClock clock,
                          StartTalkUseCase talks, @Value("${bolo.assessment.attempts-per-minute}") int perMinute,
                          app.bolo.learner.application.port.in.VoiceAccessUseCase voiceAccess) {
        this.voiceAccess = voiceAccess;
        this.talks = talks;
        this.vendor = vendor;
        this.attempts = attempts;
        this.learners = learners;
        this.clock = clock;
        this.perMinute = perMinute;
    }

    @Override
    public Outcome attempt(LearnerId learner, byte[] wav, String reference, boolean upgrade) {
        var view = learners.view(learner);
        if (view.consent() == null) throw new RefusedException("no-consent");
        // P1d: practice is paid voice time too; a guest signs in first (sign-up is open to anyone).
        if (!voiceAccess.guestMayUseVoice(view)) throw new RefusedException("sign-in");
        String ref = reference == null ? "" : reference.trim();
        if (ref.isEmpty() || ref.split("\\s+").length > MAX_WORDS) throw new RefusedException("bad-reference");
        if (!isShortWav16kMono(wav)) throw new RefusedException("bad-audio");
        throttle(learner);
        // Azure's free tier allows one recognition at a time; a live talk needs it more than practice does.
        if (talks.anyLive()) throw new SpeechBusyException();

        PronunciationResult r = vendor.assess(new TalkContext(learner, null), wav, ref);
        AttemptResult scored = AttemptScorer.score(r.words().stream()
                .map(w -> new SpokenWord(w.word(), w.startMs(), w.endMs(), w.accuracy(), w.errorType(),
                        w.phonemes().stream().map(p -> new SpokenSound(p.symbol(), p.accuracy())).toList()))
                .toList(), upgrade);
        List<Focus> focus = scored.focus().stream().map(f -> new Focus(f.word(), f.kind().name(),
                f.pattern() == null ? "Say every word" : f.pattern().title(), f.tip())).toList();
        Outcome outcome = new Outcome(r.heard() ? scored.level().name() : AttemptResult.Level.ONCE_MORE.name(), r.heard(), focus);
        attempts.save(learner, ref, outcome.level(), r.heard(), r.accuracy(), JSON.writeValueAsString(focus), clock.now());
        return outcome;
    }

    @Override
    public List<Practice> practice() {
        return Arrays.stream(SoundPattern.values())
                .map(p -> new Practice(p.name(), p.title(), p.example(), p.tip(), PracticeDeck.sentences(p))).toList();
    }

    private void throttle(LearnerId learner) {
        Instant now = clock.now();
        Deque<Instant> times = recent.computeIfAbsent(learner, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(Duration.ofMinutes(1)))) times.pollFirst();
            if (times.size() >= perMinute) throw new TooManyRequestsException();
            times.addLast(now);
        }
    }

    /** RIFF/WAVE, PCM, mono, 16 kHz, 16-bit, with at most 15 s of audio (plus half a second of slack). */
    static boolean isShortWav16kMono(byte[] wav) {
        if (wav == null || wav.length < 44) return false;
        if (!"RIFF".equals(new String(wav, 0, 4, StandardCharsets.US_ASCII))
                || !"WAVE".equals(new String(wav, 8, 4, StandardCharsets.US_ASCII))) return false;
        ByteBuffer b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        int i = 12;
        boolean fmtOk = false;
        while (i + 8 <= wav.length) {
            String id = new String(wav, i, 4, StandardCharsets.US_ASCII);
            int size = b.getInt(i + 4);
            if (size < 0) return false;
            if (id.equals("fmt ") && i + 24 <= wav.length) {
                fmtOk = b.getShort(i + 8) == 1 && b.getShort(i + 10) == 1 && b.getInt(i + 12) == 16000 && b.getShort(i + 22) == 16;
            }
            if (id.equals("data")) return fmtOk && size <= MAX_DATA_BYTES && wav.length - (i + 8) <= MAX_DATA_BYTES + 1;
            i += 8 + size + (size & 1);
        }
        return false;
    }

    /** A guest joined an account (US-8.1, US-8.4): its practice attempts move with it, in the sign-in transaction. */
    @org.springframework.context.event.EventListener
    public void onLearnerMerged(app.bolo.common.event.LearnerMerged e) {
        attempts.moveLearner(e.from(), e.to());
    }
}
