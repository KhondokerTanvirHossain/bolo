package app.bolo.learner.application.port.out;

import app.bolo.common.id.LearnerId;
import app.bolo.learner.domain.Profile;

import java.time.Instant;
import java.util.Optional;

public interface ProfilePort {

    void upsert(LearnerId learner, Profile profile, Instant now);

    Optional<Profile> find(LearnerId learner);
}
