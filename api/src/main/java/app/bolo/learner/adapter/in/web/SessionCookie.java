package app.bolo.learner.adapter.in.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

/** The bolo_session cookie: HttpOnly, SameSite=Lax, Path=/, 90 days. */
final class SessionCookie {

    static final String NAME = "bolo_session";
    static final String LEARNER_ATTRIBUTE = "bolo.learner";

    private SessionCookie() {
    }

    static String read(HttpServletRequest req) {
        if (req.getCookies() == null) return null;
        for (Cookie c : req.getCookies()) if (NAME.equals(c.getName())) return c.getValue();
        return null;
    }

    static String header(String token, boolean secure) {
        return ResponseCookie.from(NAME, token).httpOnly(true).secure(secure).sameSite("Lax").path("/")
                .maxAge(Duration.ofDays(90)).build().toString();
    }
}
