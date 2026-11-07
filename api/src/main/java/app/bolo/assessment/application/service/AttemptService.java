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
 * One read-aloud attempt: checked (consent, a real 16 kHz mono WAV of ≤ 10 s, a reference of ≤ 30 words, at most
 * N attempts a minute), assessed against the reference by the speech vendor, scored for clarity, stored (scores only).
 */
@Service
public class AttemptService implements AttemptUseCase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    static final int MAX_WORDS = 30;
    static final int MAX_DATA_BYTES = 10 * 16000 * 2;

    private final AssessPronunciationUseCase vendor;
    private final AttemptPort attempts;
    private final CurrentLearnerUseCase learners;
    private final BoloClock clock;
    private final int perMinute;
    private final Map<LearnerId, Deque<Instant>> recent = new ConcurrentHashMap<>();

    public AttemptService(AssessPronunciationUseCase vendor, AttemptPort attempts, CurrentLearnerUseCase learners, BoloClock clock,
                          @Value("${bolo.assessment.attempts-per-minute}") int perMinute) {
        this.vendor = vendor;
        this.attempts = attempts;
        this.learners = learners;
        this.clock = clock;
        this.perMinute = perMinute;
    }

    @Override
    public Outcome attempt(LearnerId learner, byte[] wav, String reference) {
        if (learners.view(learner).consent() == null) throw new RefusedException("no-consent");
        String ref = reference == null ? "" : reference.trim();
        if (ref.isEmpty() || ref.split("\\s+").length > MAX_WORDS) throw new RefusedException("bad-reference");
        if (!isShortWav16kMono(wav)) throw new RefusedException("bad-audio");
        throttle(learner);

        PronunciationResult r = vendor.assess(new TalkContext(learner, null), wav, ref);
        AttemptResult scored = AttemptScorer.score(r.words().stream()
                .map(w -> new SpokenWord(w.word(), w.startMs(), w.endMs(), w.accuracy(), w.errorType(),
                        w.phonemes().stream().map(p -> new SpokenSound(p.symbol(), p.accuracy())).toList()))
                .toList());
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

    /** RIFF/WAVE, PCM, mono, 16 kHz, 16-bit, with at most 10 s of audio. */
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
}
