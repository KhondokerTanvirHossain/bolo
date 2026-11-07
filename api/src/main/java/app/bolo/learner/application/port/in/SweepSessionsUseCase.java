package app.bolo.learner.application.port.in;

public interface SweepSessionsUseCase {

    /** Deletes sessions idle past the expiry and guests who never consented within a day. */
    void sweep();
}
