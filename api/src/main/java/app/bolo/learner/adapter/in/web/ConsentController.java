package app.bolo.learner.adapter.in.web;

import app.bolo.common.error.SignedOutException;
import app.bolo.common.id.LearnerId;
import app.bolo.learner.application.port.in.RecordConsentUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class ConsentController {

    private final RecordConsentUseCase consent;

    record ConsentRequest(boolean keepRecordings, boolean over13, String policyVersion) {
    }

    @PostMapping("/api/consent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void record(@RequestAttribute(name = SessionCookie.LEARNER_ATTRIBUTE, required = false) LearnerId learner,
                @RequestBody ConsentRequest body) {
        if (learner == null) throw new SignedOutException();
        consent.record(learner, body.keepRecordings(), body.over13(), body.policyVersion());
    }
}
