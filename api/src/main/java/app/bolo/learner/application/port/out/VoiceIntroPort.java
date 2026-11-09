package app.bolo.learner.application.port.out;

import app.bolo.common.id.LearnerId;
import app.bolo.learner.application.port.in.VoiceIntroUseCase.Recording;

import java.time.Instant;
import java.util.List;

public interface VoiceIntroPort {

    void save(LearnerId learner, int day, Instant at, boolean onServer);

    List<Recording> list(LearnerId learner);

    record Due(LearnerId learner, int day) {
    }

    List<Due> serverAudioOlderThan(Instant before);

    void markOffServer(LearnerId learner, int day);
}
