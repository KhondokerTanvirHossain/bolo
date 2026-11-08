package app.bolo.learner.adapter.out.firebase;

import app.bolo.learner.application.port.out.IdentityPort;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Verifies a Firebase ID token the way Firebase documents it, with the project ID alone (no service account): RS256
 * against Google's securetoken keys, iss = https://securetoken.google.com/&lt;project&gt;, aud = the project, not expired.
 * Bolo also asks for a verified email and a sign-in from the last few minutes (the token is exchanged at once).
 */
public class FirebaseIdTokens implements IdentityPort {

    static final String GOOGLE_KEYS = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
    static final Duration FRESH = Duration.ofMinutes(5);

    private final String projectId;
    private final DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    private final Clock clock;

    public FirebaseIdTokens(String projectId, JWKSource<SecurityContext> keys, Clock clock) {
        this.projectId = projectId;
        this.clock = clock;
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
        processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(projectId,
                new JWTClaimsSet.Builder().issuer("https://securetoken.google.com/" + projectId).build(),
                Set.of("sub", "exp", "iat", "auth_time", "email")));
    }

    /** Google's keys, cached and refreshed when a token names a key we haven't seen. */
    public static FirebaseIdTokens forGoogle(String projectId, Clock clock) {
        try {
            return new FirebaseIdTokens(projectId, JWKSourceBuilder.<SecurityContext>create(URI.create(GOOGLE_KEYS).toURL()).build(), clock);
        } catch (java.net.MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Optional<VerifiedIdentity> verify(String idToken) {
        if (idToken == null || idToken.isBlank()) return Optional.empty();
        try {
            JWTClaimsSet c = processor.process(idToken, null);
            String uid = c.getSubject();
            Object authTime = c.getClaim("auth_time");
            boolean fresh = authTime instanceof Number n && n.longValue() >= clock.instant().minus(FRESH).getEpochSecond();
            if (uid == null || uid.isBlank() || !fresh || !Boolean.TRUE.equals(c.getBooleanClaim("email_verified"))) return Optional.empty();
            return Optional.of(new VerifiedIdentity(uid, c.getStringClaim("email"), c.getStringClaim("name")));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    String projectId() {
        return projectId;
    }
}
