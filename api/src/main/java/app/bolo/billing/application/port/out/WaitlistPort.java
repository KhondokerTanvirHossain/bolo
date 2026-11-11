package app.bolo.billing.application.port.out;

import app.bolo.billing.application.port.in.WaitlistUseCase.Count;
import app.bolo.billing.application.port.in.WaitlistUseCase.Entry;
import app.bolo.billing.application.port.in.WaitlistUseCase.Mine;
import app.bolo.common.id.LearnerId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WaitlistPort {

    void upsert(LearnerId learner, String plan, String region, String timeZone, Instant at);

    Optional<Mine> find(LearnerId learner);

    List<Entry> entries();

    List<Count> counts();

    /** A guest joined an account: the account's own choice wins; otherwise the guest's moves over. */
    void moveLearner(LearnerId from, LearnerId to);
}
