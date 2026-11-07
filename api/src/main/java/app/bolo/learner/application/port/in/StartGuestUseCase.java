package app.bolo.learner.application.port.in;

/**
 * Resolves the session token, or starts a new guest. {@code newToken} is null when the token was valid.
 * New guests are capped per client ({@code clientKey}, e.g. its IP); over the cap it throws TooManyRequestsException.
 */
public interface StartGuestUseCase {

    Started start(String existingTokenOrNull, String clientKey);

    record Started(LearnerView learner, String newToken) {
    }
}
