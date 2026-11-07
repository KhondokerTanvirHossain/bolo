package app.bolo.learner.adapter.in.web;

import app.bolo.learner.application.port.in.CurrentLearnerUseCase;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Resolves the session cookie once per request and exposes the learner id as a request attribute. */
@Component
class SessionCookieFilter extends OncePerRequestFilter {

    private final CurrentLearnerUseCase current;
    private final boolean secure;

    SessionCookieFilter(CurrentLearnerUseCase current, @Value("${bolo.secure-cookie}") boolean secure) {
        this.current = current;
        this.secure = secure;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") && !request.getRequestURI().startsWith("/ws/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String token = SessionCookie.read(req, secure);
        if (token != null) current.byToken(token).ifPresent(id -> req.setAttribute(SessionCookie.LEARNER_ATTRIBUTE, id));
        chain.doFilter(req, res);
    }
}
