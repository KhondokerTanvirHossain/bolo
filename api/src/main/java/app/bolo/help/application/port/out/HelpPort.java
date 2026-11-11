package app.bolo.help.application.port.out;

import app.bolo.common.id.LearnerId;
import app.bolo.help.application.port.in.HelpUseCase.Request;

import java.time.Instant;
import java.util.List;

public interface HelpPort {

    void insert(LearnerId learner, String message, String contextJson, Instant at);

    int countSince(LearnerId learner, Instant since);

    List<Request> open();

    void done(long id, Instant at);

    void moveLearner(LearnerId from, LearnerId to);
}
