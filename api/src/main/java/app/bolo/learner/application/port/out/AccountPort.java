package app.bolo.learner.application.port.out;

import app.bolo.common.id.LearnerId;

import java.time.Instant;
import java.util.Optional;

public interface AccountPort {

    record Account(String email, String name) {
    }

    Optional<LearnerId> learnerFor(String firebaseUid);

    Optional<String> uidOf(LearnerId learner);

    /** Throws on a duplicate uid or learner (a racing sign-in made it first). */
    void insert(LearnerId learner, String firebaseUid, String email, String name, Instant at);

    void signedIn(LearnerId learner, String email, String name, Instant at);

    Optional<Account> find(LearnerId learner);
}
