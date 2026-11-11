package app.bolo.learner.application.port.out;

import app.bolo.common.id.LearnerId;
import app.bolo.learner.domain.Learner;

import java.time.Instant;
import java.util.Optional;

public interface LearnerPort {

    void save(Learner learner);

    Optional<Learner> find(LearnerId id);

    void setKind(LearnerId id, app.bolo.learner.domain.LearnerKind kind);

    /** Outside the free plan (P1d). */
    void setUnlimited(LearnerId id);

    void setUnlimited(LearnerId id, boolean on);

    boolean unlimited(LearnerId id);

    /** Deletes the learner; every row that references it goes too (cascade). */
    void delete(LearnerId id);

    /** Deletes guests created before {@code cutoff} who never recorded consent (cascades to their rows). */
    void deleteGuestsWithoutConsentCreatedBefore(Instant cutoff);
}
