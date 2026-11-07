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
        String existing = SessionCookie.read(req, secureCookie);
        StartGuestUseCase.Started started = startGuest.start(existing, SessionCookie.clientKey(req));
        // Always (re)issue: a valid token gets a fresh 90-day Max-Age, matching the server's sliding expiry.
        String token = started.newToken() != null ? started.newToken() : existing;
        res.addHeader(HttpHeaders.SET_COOKIE, SessionCookie.header(token, secureCookie));
        return started.learner();
    }

    /** Every app start reads this, so it is also where a returning learner's cookie gets its fresh 90 days. */
    @GetMapping("/api/me")
    LearnerView me(@RequestAttribute(name = SessionCookie.LEARNER_ATTRIBUTE, required = false) LearnerId learner,
                   HttpServletRequest req, HttpServletResponse res) {
        if (learner == null) throw new SignedOutException();
        res.addHeader(HttpHeaders.SET_COOKIE, SessionCookie.header(SessionCookie.read(req, secureCookie), secureCookie));
        return current.view(learner);
    }
}
