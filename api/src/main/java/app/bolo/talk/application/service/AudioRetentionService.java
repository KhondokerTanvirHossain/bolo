package app.bolo.talk.application.service;

import app.bolo.common.time.BoloClock;
import app.bolo.media.application.port.in.AudioStoreUseCase;
import app.bolo.talk.application.port.in.AudioRetentionUseCase;
import app.bolo.talk.application.port.out.TalkSessionPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioRetentionService implements AudioRetentionUseCase {

    static final Duration NO_CONSENT_KEEP = Duration.ofHours(24);
    static final Duration MAX_KEEP = Duration.ofDays(90);
    static final Duration FREE_TALK_USE_KEEP = Duration.ofDays(365);

    private final TalkSessionPort sessions;
    private final AudioStoreUseCase audio;
    private final BoloClock clock;
    private final app.bolo.learner.application.port.in.VoiceIntroUseCase intros;

    @Override
    public void sweep() {
        var now = clock.now();
        var due = sessions.audioDue(now.minus(NO_CONSENT_KEEP), now.minus(MAX_KEEP));
        for (var id : due) {
            audio.deletePrefix("talk/" + id + "/");
            sessions.markAudioPurged(id, now);
        }
        // Audio of a talk that no longer exists (deleted while its debrief or turn was still being written): remove it.
        var onDisk = new java.util.ArrayList<app.bolo.common.id.TalkSessionId>();
        for (String name : audio.children("talk/")) {
            try {
                onDisk.add(app.bolo.common.id.TalkSessionId.of(name));
            } catch (IllegalArgumentException notATalk) {
                // not a talk folder; leave it
            }
        }
        var alive = sessions.existing(onDisk);
        for (var id : onDisk) if (!alive.contains(id)) audio.deletePrefix("talk/" + id + "/");
        if (!due.isEmpty()) log.info("audio retention: purged {} talks", due.size());
        // P1d task 6: a deleted account's remembered free-talk use is kept 12 months.
        sessions.forgetFreeTalkUseBefore(now.minus(FREE_TALK_USE_KEEP));
        // Intro re-records (PRG-2) follow the same 90 days as kept talk clips.
        intros.expireAudio(now.minus(MAX_KEEP));
    }

    /** Delete everything (ACC-4): every talk's audio and clips go before the learner's rows do. */
    @org.springframework.context.event.EventListener
    public void onLearnerDeleting(app.bolo.common.event.LearnerDeleting e) {
        for (var id : sessions.idsFor(e.learner())) audio.deletePrefix("talk/" + id.value() + "/");
    }
}
