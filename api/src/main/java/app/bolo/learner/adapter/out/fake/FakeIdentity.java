package app.bolo.learner.adapter.out.fake;

import app.bolo.learner.application.port.out.IdentityPort;

import java.util.Optional;

/** Tests and keyless dev: accepts "fake:&lt;uid&gt;:&lt;email&gt;". */
public class FakeIdentity implements IdentityPort {

    @Override
    public Optional<VerifiedIdentity> verify(String idToken) {
        if (idToken == null || !idToken.startsWith("fake:")) return Optional.empty();
        String[] p = idToken.split(":", 3);
        if (p.length < 3 || p[1].isBlank() || p[2].isBlank()) return Optional.empty();
        return Optional.of(new VerifiedIdentity(p[1], p[2], "Test " + p[1]));
    }
}
