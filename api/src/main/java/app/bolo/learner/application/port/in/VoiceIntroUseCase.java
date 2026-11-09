package app.bolo.learner.application.port.in;

import app.bolo.common.id.LearnerId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Your voice, then and now (PRG-2, US-6.1): the intro schedule, re-records and their audio. */
public interface VoiceIntroUseCase {

    record Recording(int day, Instant recordedAt, boolean onServer) {
    }

    /** due: the day to record now (or null); nextDay/nextOn: the next unlock. */
    record Voice(LocalDate day1, List<Integer> unlocked, Integer due, int nextDay, LocalDate nextOn, List<Recording> recordings) {
    }

    record Clip(String contentType, byte[] bytes) {
    }

    Voice voice(LearnerId learner);

    /**
     * Records Day {@code day}'s intro. {@code audio} is null when the clip stays in the browser (not kept). Refuses
     * "not-yet" for a day that isn't an unlocked intro day, and "not-kept" for audio from a learner who didn't choose to
     * keep recordings.
     */
    void record(LearnerId learner, int day, byte[] audio);

    Optional<Clip> clip(LearnerId learner, int day);
}
