package app.bolo.learner.application.port.out;

public interface InvitePort {

    /** Uses the code once if it exists and has uses left; false otherwise. Atomic. */
    boolean consume(String code);
}
