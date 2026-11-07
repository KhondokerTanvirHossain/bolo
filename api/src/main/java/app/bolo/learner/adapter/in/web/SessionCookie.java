package app.bolo.learner.adapter.in.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

/**
 * The session cookie: HttpOnly, SameSite=Lax, Path=/, 90 days, re-issued on every visit so an active learner never
 * expires (NFR-17). Over HTTPS it is {@code __Host-bolo_session}: the prefix forbids a Domain attribute, so a sibling
 * site under the same parent domain (any other *.sslip.io host) can neither set nor shadow it. Plain-http local
 * development uses {@code bolo_session}, because browsers refuse __Host- cookies without Secure.
 */
final class SessionCookie {

    static final String LEARNER_ATTRIBUTE = "bolo.learner";
    static final Duration MAX_AGE = Duration.ofDays(90);

    private SessionCookie() {
    }

    static String name(boolean secure) {
        return secure ? "__Host-bolo_session" : "bolo_session";
    }

    static String read(HttpServletRequest req, boolean secure) {
        if (req.getCookies() == null) return null;
        String name = name(secure);
        for (Cookie c : req.getCookies()) if (name.equals(c.getName())) return c.getValue();
        return null;
    }

    static String header(String token, boolean secure) {
        return ResponseCookie.from(name(secure), token).httpOnly(true).secure(secure).sameSite("Lax").path("/")
                .maxAge(MAX_AGE).build().toString();
    }

    /** The client address for rate limits: Caddy sets X-Forwarded-For to the real client; the API port isn't public. */
    static String clientKey(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
