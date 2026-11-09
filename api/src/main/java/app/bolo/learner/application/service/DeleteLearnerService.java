package app.bolo.learner.application.service;

import app.bolo.common.error.RefusedException;
import app.bolo.common.event.LearnerDeleting;
import app.bolo.common.id.LearnerId;
import app.bolo.learner.application.port.in.DeleteLearnerUseCase;
import app.bolo.learner.application.port.out.LearnerPort;
import app.bolo.media.application.port.in.AudioStoreUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ACC-4: everything goes at once (well within the PRD's 7 days): audio files first, then every row by cascade. */
@Service
@RequiredArgsConstructor
public class DeleteLearnerService implements DeleteLearnerUseCase {

    private final LearnerPort learners;
    private final AudioStoreUseCase audio;
    private final ApplicationEventPublisher events;
    private final app.bolo.learner.application.port.in.FreePlanKeyUseCase freePlanKeys;

    @Override
    @Transactional
    public void deleteEverything(LearnerId learner, String confirm) {
        if (!"DELETE".equals(confirm)) throw new RefusedException("confirm");
        events.publishEvent(new LearnerDeleting(learner, freePlanKeys.freePlanKey(learner).orElse(null)));
        audio.deletePrefix("learner/" + learner.value() + "/");
        learners.delete(learner);
    }
}
