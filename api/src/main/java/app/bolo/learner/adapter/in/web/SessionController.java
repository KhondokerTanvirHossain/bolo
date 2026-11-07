package app.bolo.learner.adapter.in.web;

import app.bolo.common.error.SignedOutException;
import app.bolo.common.id.LearnerId;
import app.bolo.learner.application.port.in.CurrentLearnerUseCase;
import app.bolo.learner.application.port.in.LearnerView;
import app.bolo.learner.application.port.in.StartGuestUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SessionController {

    private final StartGuestUseCase startGuest;
    private final CurrentLearnerUseCase current;
    private final boolean secureCookie;

    SessionController(StartGuestUseCase startGuest, CurrentLearnerUseCase current,
                      @Value("${bolo.secure-cookie}") boolean secureCookie) {
        this.startGuest = startGuest;
        this.current = current;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/api/session")
    LearnerView start(HttpServletRequest req, HttpServletResponse res) {
        StartGuestUseCase.Started started = startGuest.start(SessionCookie.read(req));
        if (started.newToken() != null) {
            res.addHeader(HttpHeaders.SET_COOKIE, SessionCookie.header(started.newToken(), secureCookie));
        }
        return started.learner();
    }

    @GetMapping("/api/me")
    LearnerView me(@RequestAttribute(name = SessionCookie.LEARNER_ATTRIBUTE, required = false) LearnerId learner) {
        if (learner == null) throw new SignedOutException();
        return current.view(learner);
    }
}
