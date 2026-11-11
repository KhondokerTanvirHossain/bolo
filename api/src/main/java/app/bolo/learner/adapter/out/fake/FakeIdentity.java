package app.bolo.learner.adapter.out.fake;

import app.bolo.learner.application.port.out.IdentityPort;

import java.util.Optional;

/** Tests and keyless dev: accepts "fake:&lt;uid&gt;:&lt;email&gt;". */
public class FakeIdentity implements IdentityPort {

    @Override
    public Optional<VerifiedIdentity> verify(String idToken) {
        if (idToken == null || !idToken.startsWith("fake:")) return Optional.empty();
        // fake:uid:email[:name] (the name part for P2d's first names; without it, "Test <uid>").
        String[] p = idToken.split(":", 4);
        if (p.length < 3 || p[1].isBlank() || p[2].isBlank()) return Optional.empty();
        return Optional.of(new VerifiedIdentity(p[1], p[2], p.length > 3 && !p[3].isBlank() ? p[3] : "Test " + p[1]));
    }
}
