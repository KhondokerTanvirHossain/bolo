package app.bolo.learner.application.port.in;

import app.bolo.common.id.LearnerId;

import java.util.Optional;

/** ONB-4 / ACC-7: the Day 1 clip, kept with the learner only if they chose to keep recordings. */
public interface Day1UseCase {

    record Clip(String contentType, byte[] bytes) {
    }

    /** Refuses with "no-consent" or "bad-audio" (not WebM/MP4 audio, or over 2 MB). Replaces any earlier clip. */
    void save(LearnerId learner, byte[] clip);

    Optional<Clip> find(LearnerId learner);
}
