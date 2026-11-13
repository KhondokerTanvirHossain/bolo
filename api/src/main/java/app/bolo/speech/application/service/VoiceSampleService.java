package app.bolo.speech.application.service;

import app.bolo.common.audio.Wav;
import app.bolo.common.error.RefusedException;
import app.bolo.common.error.TooManyRequestsException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkContext;
import app.bolo.common.time.BoloClock;
import app.bolo.speech.application.port.in.SynthesizeUseCase;
import app.bolo.speech.application.port.in.Voice;
import app.bolo.speech.application.port.in.VoiceSampleUseCase;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P3c (SET-5): each voice's sample is synthesised once (usage recorded against the learner who first asked) and kept in
 * memory, so "Hear it" never waits on the voice service after the first time.
 */
@Service
public class VoiceSampleService implements VoiceSampleUseCase {

    public static final String SAMPLE = "Hi, I'm Bolo. What would you like to talk about today?";
    static final Set<String> KEYS = Set.of("british-female", "british-male", "american-female", "american-male",
            "indian-female", "indian-male");
    static final int PER_MINUTE = 20;

    private final SynthesizeUseCase voice;
    private final BoloClock clock;
    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();
    private final Map<LearnerId, Deque<Instant>> recent = new ConcurrentHashMap<>();

    public VoiceSampleService(SynthesizeUseCase voice, BoloClock clock) {
        this.voice = voice;
        this.clock = clock;
    }

    @Override
    public byte[] sample(LearnerId learner, String voiceKey) {
        if (voiceKey == null || !KEYS.contains(voiceKey)) throw new RefusedException("voice");
        allow(learner);
        return cache.computeIfAbsent(voiceKey, k -> {
            ByteArrayOutputStream pcm = new ByteArrayOutputStream();
            voice.synthesize(new TalkContext(learner, null), Voice.BUDDY, k, SAMPLE, pcm::writeBytes);
            return Wav.wrap(pcm.toByteArray(), 24_000);
        });
    }

    private void allow(LearnerId learner) {
        Instant now = clock.now();
        Deque<Instant> times = recent.computeIfAbsent(learner, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && Duration.between(times.peekFirst(), now).compareTo(Duration.ofMinutes(1)) >= 0) times.pollFirst();
            if (times.size() >= PER_MINUTE) throw new TooManyRequestsException();
            times.addLast(now);
        }
    }
}
