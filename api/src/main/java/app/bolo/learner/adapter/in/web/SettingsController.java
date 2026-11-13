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

    /** talkView, voiceGender, voiceAccent, mood, answerTime and stammer may be left out: the current choice stays.
     * answerTimeAuto is Bolo's to set and is ignored here. */
    record SettingsRequest(String buddySpeed, boolean captions, String talkView, String voiceGender, String voiceAccent, String mood,
                           String answerTime, Boolean stammer) {
    }

    @PutMapping("/api/me/settings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void update(@RequestAttribute(name = SessionCookie.LEARNER_ATTRIBUTE, required = false) LearnerId learner,
                @RequestBody SettingsRequest body) {
        if (learner == null) throw new SignedOutException();
        settings.update(learner, body.buddySpeed(), body.captions(), body.talkView(), body.voiceGender(), body.voiceAccent(), body.mood(),
                body.answerTime(), body.stammer());
    }
}
