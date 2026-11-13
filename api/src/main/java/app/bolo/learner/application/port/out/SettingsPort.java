package app.bolo.learner.application.port.out;

import app.bolo.common.id.LearnerId;
import app.bolo.learner.domain.Settings;

import java.time.Instant;
import java.util.Optional;

public interface SettingsPort {

    void upsert(LearnerId learner, Settings settings, Instant now);

    Optional<Settings> find(LearnerId learner);

    /** Writes only Bolo's automatic pick (a row with defaults if there is none), so it never undoes a learner's save. */
    void recordAutoAnswerTime(LearnerId learner, String speed, Instant now);
}
