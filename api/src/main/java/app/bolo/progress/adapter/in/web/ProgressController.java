package app.bolo.progress.adapter.in.web;

import app.bolo.common.error.SignedOutException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.web.CurrentLearner;
import app.bolo.progress.application.port.in.ProgressUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Progress (ScreenProgress) and History (ScreenHistory): only ever the signed-in learner's own talks. */
@RestController
@RequiredArgsConstructor
class ProgressController {

    private final ProgressUseCase progress;

    @GetMapping("/api/progress")
    Map<String, Object> progress(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner) {
        if (learner == null) throw new SignedOutException();
        return progress.progress(learner);
    }

    @GetMapping("/api/history")
    Map<String, Object> history(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner) {
        if (learner == null) throw new SignedOutException();
        return progress.history(learner);
    }
}
