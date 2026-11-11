package app.bolo.talk.application.port.out;

import app.bolo.common.id.LearnerId;
import app.bolo.talk.application.port.in.PlansUseCase.Plan;
import app.bolo.talk.domain.PlanAllowance.Sub;

import java.time.Instant;
import java.util.List;

public interface PlansPort {

    void insert(LearnerId learner, String plan, Instant startsAt, Instant endsAt, String source, String note, Instant at);

    List<Sub> subscriptions(LearnerId learner);

    List<Plan> plans();

    void savePlan(Plan plan);

    String setting(String key, String fallback);

    void setSetting(String key, String value);

    /** Talks started since {@code since} by non-unlimited learners that connected, or may still connect (started after {@code connectSince}). */
    int startsSince(Instant since, Instant connectSince);
}
