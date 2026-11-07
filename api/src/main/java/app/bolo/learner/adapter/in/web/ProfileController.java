package app.bolo.learner.adapter.in.web;

import app.bolo.common.error.SignedOutException;
import app.bolo.common.id.LearnerId;
import app.bolo.learner.application.port.in.UpdateProfileUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class ProfileController {

    private final UpdateProfileUseCase profile;

    record ProfileRequest(String goal, String fearedMoment, int talkMinutes) {
    }

    @PutMapping("/api/profile")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void update(@RequestAttribute(name = SessionCookie.LEARNER_ATTRIBUTE, required = false) LearnerId learner,
                @RequestBody ProfileRequest body) {
        if (learner == null) throw new SignedOutException();
        profile.update(learner, body.goal(), body.fearedMoment(), body.talkMinutes());
    }
}
