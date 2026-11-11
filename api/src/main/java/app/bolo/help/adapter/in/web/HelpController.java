package app.bolo.help.adapter.in.web;

import app.bolo.common.error.SignedOutException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.web.CurrentLearner;
import app.bolo.help.application.port.in.HelpUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Report a problem (P2b): any learner with a session, guest or account. */
@RestController
@RequiredArgsConstructor
class HelpController {

    private final HelpUseCase help;

    record Report(String message, Map<String, String> context) {
    }

    @PostMapping("/api/help")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void report(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner, @RequestBody Report body) {
        if (learner == null) throw new SignedOutException();
        help.report(learner, body.message(), body.context());
    }
}
