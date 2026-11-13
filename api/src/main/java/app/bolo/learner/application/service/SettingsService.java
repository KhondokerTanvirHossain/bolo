package app.bolo.learner.application.service;

import app.bolo.common.error.RefusedException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.time.BoloClock;
import app.bolo.learner.application.port.in.AutoAnswerTimeUseCase;
import app.bolo.learner.application.port.in.UpdateSettingsUseCase;
import app.bolo.learner.application.port.out.SettingsPort;
import app.bolo.learner.domain.Settings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SettingsService implements UpdateSettingsUseCase, AutoAnswerTimeUseCase {

    private final SettingsPort settings;
    private final BoloClock clock;

    @Override
    public void update(LearnerId learner, String buddySpeed, boolean captions, String talkView, String voiceGender, String voiceAccent,
                       String mood, String answerTime, Boolean stammer) {
        if (!Settings.validSpeed(buddySpeed)) throw new RefusedException("buddy-speed");
        if (talkView != null && !Settings.validTalkView(talkView)) throw new RefusedException("talk-view");
        if (voiceGender != null && !Settings.validGender(voiceGender)) throw new RefusedException("voice-gender");
        if (voiceAccent != null && !Settings.validAccent(voiceAccent)) throw new RefusedException("voice-accent");
        if (mood != null && !Settings.validMood(mood)) throw new RefusedException("mood");
        if (answerTime != null && !Settings.validAnswerTime(answerTime)) throw new RefusedException("answer-time");
        Settings now = settings.find(learner).orElse(Settings.DEFAULTS);
        settings.upsert(learner, new Settings(buddySpeed, captions, talkView != null ? talkView : now.talkView(),
                voiceGender != null ? voiceGender : now.voiceGender(), voiceAccent != null ? voiceAccent : now.voiceAccent(),
                mood != null ? mood : now.mood(), answerTime != null ? answerTime : now.answerTime(), now.answerTimeAuto(),
                stammer != null ? stammer : now.stammer()), clock.now());
    }

    @Override
    public void record(LearnerId learner, String speed) {
        if (!Settings.validAutoSpeed(speed)) throw new RefusedException("answer-time");
        settings.recordAutoAnswerTime(learner, speed, clock.now());
    }
}
