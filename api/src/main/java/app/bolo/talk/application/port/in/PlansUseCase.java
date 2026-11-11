package app.bolo.talk.application.port.in;

import app.bolo.common.id.LearnerId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Paid plans as monthly talk counts (ACC-5), grants, and the global daily talk cap (P2a). */
public interface PlansUseCase {

    record Plan(String plan, int monthlyTalks, int priceBdt, BigDecimal priceUsd) {
    }

    record Active(String plan, Instant endsAt) {
    }

    /** Gives {@code plan} from now for {@code days} days; source is admin, referral or bkash. */
    void grant(LearnerId learner, String plan, int days, String source, String note);

    Optional<Active> active(LearnerId learner);

    List<Plan> plans();

    void updatePlans(List<Plan> plans);

    int dailyCap();

    void setDailyCap(int cap);

    /** Talks started today (Asia/Dhaka) by learners who aren't unlimited. */
    int todayCount();
}
