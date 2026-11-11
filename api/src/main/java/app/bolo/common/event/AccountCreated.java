package app.bolo.common.event;

import app.bolo.common.id.LearnerId;

/**
 * A sign-in made a new Google account (P2d): a guest became one, or a fresh one was made. {@code ref} is the invite-a-friend
 * code it carried, or null. Published inside the sign-in transaction; listeners must not fail it.
 */
public record AccountCreated(LearnerId learner, String ref) {
}
