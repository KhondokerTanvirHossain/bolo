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

    record Started(TalkSessionId id, int minutes) {
    }

    /** TLK-1. The browser then opens /ws/talk/{id}. */
    @PostMapping("/api/talk")
    @ResponseStatus(HttpStatus.CREATED)
    Started start(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner) {
        if (learner == null) throw new SignedOutException();
        var view = talks.start(learner);
        return new Started(view.id(), view.minutes());
    }
}
