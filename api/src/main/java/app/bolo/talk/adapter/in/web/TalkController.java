package app.bolo.talk.adapter.in.web;

import app.bolo.common.error.SignedOutException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.id.TalkSessionId;
import app.bolo.common.web.CurrentLearner;
import app.bolo.talk.application.port.in.StartTalkUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class TalkController {

    private final StartTalkUseCase talks;

    /** SCN-1: the scenario and difficulty the talk started with (the web's talk label). */
    record Started(TalkSessionId id, int minutes, String scenario, String difficulty) {
    }

    /** TLK-1. The browser then opens /ws/talk/{id}. */
    @PostMapping("/api/talk")
    @ResponseStatus(HttpStatus.CREATED)
    Started start(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner,
                  @org.springframework.web.bind.annotation.RequestBody(required = false) app.bolo.talk.application.port.in.StartTalkUseCase.StartChoice choice) {
        if (learner == null) throw new SignedOutException();
        var view = talks.start(learner, choice);
        return new Started(view.id(), view.minutes(), view.scenario(), view.difficulty());
    }
}
