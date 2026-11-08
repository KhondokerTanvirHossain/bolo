package app.bolo.learner.adapter.in.web;

import app.bolo.learner.application.port.in.LearnerView;
import app.bolo.learner.application.port.in.SignInUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Continue with Google (ACC-1) and sign-out. The session cookie is rotated on every sign-in. */
@RestController
class AuthController {

    private final SignInUseCase signIn;
    private final app.bolo.learner.application.port.in.DeleteLearnerUseCase deleter;
    private final boolean secureCookie;

    AuthController(SignInUseCase signIn, app.bolo.learner.application.port.in.DeleteLearnerUseCase deleter,
                   @Value("${bolo.secure-cookie}") boolean secureCookie) {
        this.signIn = signIn;
        this.deleter = deleter;
        this.secureCookie = secureCookie;
    }

    record GoogleRequest(String idToken, String invite) {
    }

    @PostMapping("/api/auth/google")
    LearnerView google(@RequestBody GoogleRequest body, HttpServletRequest req, HttpServletResponse res) {
        SignInUseCase.SignedIn done = signIn.signIn(SessionCookie.read(req, secureCookie), body.idToken(), body.invite());
        res.addHeader(HttpHeaders.SET_COOKIE, SessionCookie.header(done.token(), secureCookie));
        return done.learner();
    }

    record DeleteRequest(String confirm) {
    }

    /** Me → Privacy → Delete everything (ACC-4): JSON with confirm "DELETE", so the write guard and a typed confirmation both apply. */
    @PostMapping("/api/me/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteEverything(@org.springframework.web.bind.annotation.RequestAttribute(name = SessionCookie.LEARNER_ATTRIBUTE, required = false)
                          app.bolo.common.id.LearnerId learner, @RequestBody(required = false) DeleteRequest body, HttpServletResponse res) {
        if (learner == null) throw new app.bolo.common.error.SignedOutException();
        deleter.deleteEverything(learner, body == null ? null : body.confirm());
        res.addHeader(HttpHeaders.SET_COOKIE, SessionCookie.cleared(secureCookie));
    }

    @PostMapping("/api/auth/sign-out")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void signOut(HttpServletRequest req, HttpServletResponse res) {
        signIn.signOut(SessionCookie.read(req, secureCookie));
        res.addHeader(HttpHeaders.SET_COOKIE, SessionCookie.cleared(secureCookie));
    }
}
