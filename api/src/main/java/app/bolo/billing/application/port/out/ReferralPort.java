package app.bolo.billing.application.port.out;

import app.bolo.common.id.LearnerId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReferralPort {

    record Reward(int n, Instant endsAt, Instant seenAt) {
    }

    Optional<String> code(LearnerId learner);

    /** False when the code is taken (try another). */
    boolean insertCode(LearnerId learner, String code, Instant at);

    Optional<LearnerId> learnerForCode(String code);

    void insertReferral(LearnerId friend, LearnerId referrer, Instant at);

    /** Friends who joined through this learner's code, oldest first. */
    List<LearnerId> friends(LearnerId referrer);

    List<Reward> rewards(LearnerId learner);

    /** False when reward {@code n} already exists (another request granted it). */
    boolean insertReward(LearnerId learner, int n, Instant at);

    void setRewardEnd(LearnerId learner, int n, Instant endsAt);

    void seen(LearnerId learner, Instant at);
}
