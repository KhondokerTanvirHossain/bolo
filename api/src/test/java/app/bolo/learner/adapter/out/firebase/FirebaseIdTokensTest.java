package app.bolo.learner.adapter.out.firebase;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/** ACC-1: only a fresh, verified Google sign-in for Bolo's own Firebase project is accepted. */
class FirebaseIdTokensTest {

    static final String PROJECT = "bolo-test";
    static final RSAKey KEY = key("k1");
    static final RSAKey OTHER = key("k2");

    final FirebaseIdTokens tokens = new FirebaseIdTokens(PROJECT, new ImmutableJWKSet<>(new JWKSet(KEY.toPublicJWK())), Clock.systemUTC());

    static RSAKey key(String id) {
        try {
            return new RSAKeyGenerator(2048).keyID(id).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String token(RSAKey signer, Consumer<JWTClaimsSet.Builder> change) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet.Builder b = new JWTClaimsSet.Builder()
                .issuer("https://securetoken.google.com/" + PROJECT).audience(PROJECT).subject("uid-1")
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(3600)))
                .claim("auth_time", now.getEpochSecond()).claim("email", "rahim@example.com").claim("email_verified", true)
                .claim("name", "Rahim");
        change.accept(b);
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signer.getKeyID()).build(), b.build());
        jwt.sign(new RSASSASigner(signer));
        return jwt.serialize();
    }

    @Test
    void aGoodTokenIsAccepted() throws Exception {
        var id = tokens.verify(token(KEY, b -> { })).orElseThrow();
        assertThat(id.uid()).isEqualTo("uid-1");
        assertThat(id.email()).isEqualTo("rahim@example.com");
        assertThat(id.name()).isEqualTo("Rahim");
    }

    @Test
    void anotherProjectsTokenIsRefused() throws Exception {
        assertThat(tokens.verify(token(KEY, b -> b.audience("someone-else")))).isEmpty();
    }

    @Test
    void aWrongIssuerIsRefused() throws Exception {
        assertThat(tokens.verify(token(KEY, b -> b.issuer("https://securetoken.google.com/someone-else")))).isEmpty();
    }

    @Test
    void anExpiredTokenIsRefused() throws Exception {
        assertThat(tokens.verify(token(KEY, b -> b.expirationTime(Date.from(Instant.now().minusSeconds(120)))))).isEmpty();
    }

    @Test
    void anOldSignInIsRefused() throws Exception {
        assertThat(tokens.verify(token(KEY, b -> b.claim("auth_time", Instant.now().minusSeconds(360).getEpochSecond())))).isEmpty();
    }

    @Test
    void anUnverifiedEmailIsRefused() throws Exception {
        assertThat(tokens.verify(token(KEY, b -> b.claim("email_verified", false)))).isEmpty();
    }

    @Test
    void aTokenSignedByAnotherKeyIsRefused() throws Exception {
        assertThat(tokens.verify(token(OTHER, b -> { }))).isEmpty();
    }

    @Test
    void garbageIsRefused() {
        assertThat(tokens.verify("not.a.token")).isEmpty();
        assertThat(tokens.verify("")).isEmpty();
        assertThat(tokens.verify(null)).isEmpty();
    }
}
