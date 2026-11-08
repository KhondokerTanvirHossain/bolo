package app.bolo.learner.application.port.out;

import java.util.Optional;

/** Checks a Google sign-in (ACC-1). */
public interface IdentityPort {

    /** Who signed in: a stable id from the identity provider, a verified email, and a display name (may be null). */
    record VerifiedIdentity(String uid, String email, String name) {
    }

    /** Empty for anything that isn't a fresh, verified sign-in for this project. Never throws. */
    Optional<VerifiedIdentity> verify(String idToken);
}
