package app.bolo.billing.application.port.in;

import app.bolo.common.id.LearnerId;

import java.time.Instant;
import java.util.List;

/** P2d: invite 3 friends, get a month of Premium (ACC-8 / US-8.10; Premium per the founder). Accounts only. */
public interface ReferralUseCase {

    int FRIENDS_PER_REWARD = 3;
    int MAX_REWARDS = 3;
    int REWARD_DAYS = 30;

    /**
     * offer: the invite offer still runs; friends: first names of friends who finished a talk; counted: how many;
     * rewards: months earned; premiumUntil: when the latest earned month ends; newReward: a month not yet announced.
     */
    record Status(boolean offer, String code, List<String> friends, int counted, int rewards, int maxRewards,
                  Instant premiumUntil, boolean newReward) {
    }

    /** Also grants any month the counted friends have earned (once). */
    Status status(LearnerId learner);

    void seen(LearnerId learner);
}
