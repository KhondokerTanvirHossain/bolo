package app.bolo.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * CSRF guard for every write (the medioo pattern, no CORS ever): the request must be JSON or multipart, carry
 * {@code X-Bolo: 1} and come from Bolo's own origin. A cross-site form or fetch can satisfy none of these.
 * It guards every unsafe method whatever the path: the API serves nothing else, and a path test on the raw URI is
 * bypassed by encoded or matrix-param paths ({@code /%61pi/session}, {@code /api;x=1/session}) that Spring still routes.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class WriteGuardFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS");

    private final String publicOrigin;

    WriteGuardFilter(@Value("${bolo.public-origin}") String publicOrigin) {
        this.publicOrigin = publicOrigin;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SAFE.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String type = req.getContentType() == null ? "" : req.getContentType().toLowerCase();
        boolean typeOk = type.startsWith("application/json") || type.startsWith("multipart/form-data");
        boolean headerOk = "1".equals(req.getHeader("X-Bolo"));
        boolean originOk = publicOrigin.equals(req.getHeader("Origin"));
        if (typeOk && headerOk && originOk) {
            chain.doFilter(req, res);
            return;
        }
        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
        res.setContentType("application/json");
        res.getWriter().write("{\"code\":\"forbidden\"}");
    }
}
