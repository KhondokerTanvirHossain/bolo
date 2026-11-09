package app.bolo.learner.application.port.out;

public interface InvitePort {

    /** Uses the code once if it exists and has uses left, returning whether it makes unlimited learners; empty otherwise. Atomic. */
    java.util.Optional<Boolean> consume(String code);
}
