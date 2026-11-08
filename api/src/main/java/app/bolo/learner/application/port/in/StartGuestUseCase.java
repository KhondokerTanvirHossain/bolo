package app.bolo.learner.application.port.in;

/**
 * Resolves the session token, or starts a new guest. {@code newToken} is null when the token was valid.
 * New guests are capped per client ({@code clientKey}, e.g. its IP); over the cap it throws TooManyRequestsException.
 */
public interface StartGuestUseCase {

    /** A new guest needs {@code invite} while the closed beta is on (refused with "invite"); a valid session never does. */
    Started start(String existingTokenOrNull, String clientKey, String invite);

    record Started(LearnerView learner, String newToken) {
    }
}
