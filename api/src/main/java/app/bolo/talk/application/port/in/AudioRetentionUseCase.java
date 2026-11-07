package app.bolo.talk.application.port.in;

/** NFR-14: talk audio is deleted 24 h after the talk when the learner said "No" to keeping recordings, else after 90 days. */
public interface AudioRetentionUseCase {

    void sweep();
}
