package app.bolo.billing.application.port.in;

import app.bolo.common.id.LearnerId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** P2c: the plan waitlist. Accounts only; one choice per learner, the latest wins. */
public interface WaitlistUseCase {

    record Mine(String plan, String region) {
    }

    record Entry(String email, String plan, String region, String timeZone, Instant at) {
    }

    record Count(String plan, int bd, int intl) {
    }

    void join(LearnerId learner, String plan, String region, String timeZone);

    Optional<Mine> mine(LearnerId learner);

    List<Entry> entries();

    List<Count> counts();
}
