package app.bolo.learner.application.port.out;

import app.bolo.common.id.LearnerId;
import app.bolo.learner.domain.Settings;

import java.time.Instant;
import java.util.Optional;

public interface SettingsPort {

    void upsert(LearnerId learner, Settings settings, Instant now);

    Optional<Settings> find(LearnerId learner);
}
