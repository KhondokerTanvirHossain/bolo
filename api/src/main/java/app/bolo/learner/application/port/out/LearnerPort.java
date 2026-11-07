package app.bolo.learner.application.port.out;

import app.bolo.common.id.LearnerId;
import app.bolo.learner.domain.Learner;

import java.util.Optional;

public interface LearnerPort {

    void save(Learner learner);

    Optional<Learner> find(LearnerId id);
}
