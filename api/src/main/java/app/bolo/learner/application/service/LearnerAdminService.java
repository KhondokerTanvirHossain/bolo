package app.bolo.learner.application.service;

import app.bolo.common.id.LearnerId;
import app.bolo.learner.application.port.in.LearnerAdminUseCase;
import app.bolo.learner.application.port.out.LearnerPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class LearnerAdminService implements LearnerAdminUseCase {

    private final LearnerPort learners;

    @Override
    public void setUnlimited(LearnerId learner, boolean on) {
        learners.setUnlimited(learner, on);
    }
}
