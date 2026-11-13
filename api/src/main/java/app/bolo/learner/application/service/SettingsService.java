package app.bolo.learner.application.service;

import app.bolo.common.error.RefusedException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.time.BoloClock;
import app.bolo.learner.application.port.in.UpdateSettingsUseCase;
import app.bolo.learner.application.port.out.SettingsPort;
import app.bolo.learner.domain.Settings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SettingsService implements UpdateSettingsUseCase {

    private final SettingsPort settings;
    private final BoloClock clock;

    @Override
    public void update(LearnerId learner, String buddySpeed, boolean captions, String talkView, String voiceGender, String voiceAccent,
                       String mood) {
        if (!Settings.validSpeed(buddySpeed)) throw new RefusedException("buddy-speed");
        if (talkView != null && !Settings.validTalkView(talkView)) throw new RefusedException("talk-view");
        if (voiceGender != null && !Settings.validGender(voiceGender)) throw new RefusedException("voice-gender");
        if (voiceAccent != null && !Settings.validAccent(voiceAccent)) throw new RefusedException("voice-accent");
        if (mood != null && !Settings.validMood(mood)) throw new RefusedException("mood");
        Settings now = settings.find(learner).orElse(Settings.DEFAULTS);
        settings.upsert(learner, new Settings(buddySpeed, captions, talkView != null ? talkView : now.talkView(),
                voiceGender != null ? voiceGender : now.voiceGender(), voiceAccent != null ? voiceAccent : now.voiceAccent(),
                mood != null ? mood : now.mood()), clock.now());
    }
}
