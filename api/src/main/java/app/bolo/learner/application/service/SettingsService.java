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
    public void update(LearnerId learner, String buddySpeed, boolean captions, String talkView) {
        if (!Settings.validSpeed(buddySpeed)) throw new RefusedException("buddy-speed");
        if (talkView != null && !Settings.validTalkView(talkView)) throw new RefusedException("talk-view");
        String view = talkView != null ? talkView : settings.find(learner).map(Settings::talkView).orElse(Settings.DEFAULTS.talkView());
        settings.upsert(learner, new Settings(buddySpeed, captions, view), clock.now());
    }
}
