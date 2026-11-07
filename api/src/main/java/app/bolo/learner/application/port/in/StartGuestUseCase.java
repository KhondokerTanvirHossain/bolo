package app.bolo.learner.application.port.in;

/** Resolves the session token, or starts a new guest. {@code newToken} is null when the token was valid. */
public interface StartGuestUseCase {

    Started start(String existingTokenOrNull);

    record Started(LearnerView learner, String newToken) {
    }
}
