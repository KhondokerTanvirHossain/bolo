package app.bolo.learner.adapter.in.web;

import app.bolo.common.error.SignedOutException;
import app.bolo.common.id.LearnerId;
import app.bolo.learner.application.port.in.UpdateSettingsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class SettingsController {

    private final UpdateSettingsUseCase settings;

    /** talkView may be left out: the current choice stays. */
    record SettingsRequest(String buddySpeed, boolean captions, String talkView) {
    }

    @PutMapping("/api/me/settings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void update(@RequestAttribute(name = SessionCookie.LEARNER_ATTRIBUTE, required = false) LearnerId learner,
                @RequestBody SettingsRequest body) {
        if (learner == null) throw new SignedOutException();
        settings.update(learner, body.buddySpeed(), body.captions(), body.talkView());
    }
}
