package app.bolo.learner.application.service;

import app.bolo.common.error.RefusedException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.time.BoloClock;
import app.bolo.learner.application.port.in.VoiceIntroUseCase;
import app.bolo.learner.application.port.out.ConsentLedgerPort;
import app.bolo.learner.application.port.out.LearnerPort;
import app.bolo.learner.application.port.out.VoiceIntroPort;
import app.bolo.learner.domain.Consent;
import app.bolo.learner.domain.IntroSchedule;
import app.bolo.media.application.port.in.AudioStoreUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Collectors;

/** Day 1 is the learner's sign-up date in Dhaka (they record their first intro during onboarding). */
@Service
@RequiredArgsConstructor
public class VoiceIntroService implements VoiceIntroUseCase {

    private final LearnerPort learners;
    private final ConsentLedgerPort consents;
    private final VoiceIntroPort intros;
    private final AudioStoreUseCase audio;
    private final BoloClock clock;

    static String key(LearnerId learner, int day, String ext) {
        return "learner/" + learner.value() + "/intro-" + day + "." + ext;
    }

    private LocalDate day1(LearnerId learner) {
        return IntroSchedule.dayOne(learners.find(learner).orElseThrow().createdAt());
    }

    @Override
    public Voice voice(LearnerId learner) {
        var recordings = intros.list(learner);
        var s = IntroSchedule.of(day1(learner), recordings.stream().map(Recording::day).collect(Collectors.toSet()),
                clock.now().atZone(IntroSchedule.ZONE).toLocalDate());
        return new Voice(s.day1(), s.unlocked(), s.due(), s.nextDay(), s.nextOn(), recordings);
    }

    @Override
    public void record(LearnerId learner, int day, byte[] clip) {
        LocalDate today = clock.now().atZone(IntroSchedule.ZONE).toLocalDate();
        if (!IntroSchedule.isUnlockDay(day) || IntroSchedule.on(day1(learner), day).isAfter(today)) throw new RefusedException("not-yet");
        audio.delete(key(learner, day, "webm"));
        audio.delete(key(learner, day, "mp4"));
        if (clip != null) {
            // A guest's intro stays on the device (ONB-4): only an account that keeps recordings sends audio.
            boolean account = learners.find(learner).map(l -> l.kind() == app.bolo.learner.domain.LearnerKind.ACCOUNT).orElse(false);
            boolean kept = account && consents.latest(learner).map(Consent::keepRecordings).orElse(false);
            if (!kept) throw new RefusedException("not-kept");
            String ext = Day1Service.kind(clip).orElseThrow(() -> new RefusedException("bad-audio"));
            audio.put(key(learner, day, ext), clip);
        }
        intros.save(learner, day, clock.now(), clip != null);
    }

    /** NFR-14: kept intro audio goes after 90 days, like every kept clip; the date stays for the schedule. */
    @Override
    public void expireAudio(java.time.Instant olderThan) {
        for (var due : intros.serverAudioOlderThan(olderThan)) {
            audio.delete(key(due.learner(), due.day(), "webm"));
            audio.delete(key(due.learner(), due.day(), "mp4"));
            intros.markOffServer(due.learner(), due.day());
        }
    }

    @Override
    public Optional<Clip> clip(LearnerId learner, int day) {
        return audio.get(key(learner, day, "webm")).map(b -> new Clip("audio/webm", b))
                .or(() -> audio.get(key(learner, day, "mp4")).map(b -> new Clip("audio/mp4", b)));
    }
}
