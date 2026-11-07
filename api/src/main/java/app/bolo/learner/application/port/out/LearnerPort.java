package app.bolo.learner.application.port.out;

import app.bolo.common.id.LearnerId;
import app.bolo.learner.domain.Learner;

import java.time.Instant;
import java.util.Optional;

public interface LearnerPort {

    void save(Learner learner);

    Optional<Learner> find(LearnerId id);

    /** Deletes guests created before {@code cutoff} who never recorded consent (cascades to their rows). */
    void deleteGuestsWithoutConsentCreatedBefore(Instant cutoff);
}
