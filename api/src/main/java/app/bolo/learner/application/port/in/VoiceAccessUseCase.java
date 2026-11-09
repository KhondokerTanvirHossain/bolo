package app.bolo.learner.application.port.in;

/**
 * Who may use paid voice time (talks and practice) without a Google account (P1d). Accounts always may (their talks are
 * then limited by the free plan); unlimited learners always may; a guest only where bolo.guests.may-talk is on (tests,
 * local development) and sign-in is off. Otherwise a guest is refused with "sign-in".
 */
public interface VoiceAccessUseCase {

    boolean guestMayUseVoice(LearnerView learner);
}
