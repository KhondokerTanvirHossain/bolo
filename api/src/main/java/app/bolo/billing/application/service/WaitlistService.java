package app.bolo.billing.application.service;

import app.bolo.billing.application.port.in.WaitlistUseCase;
import app.bolo.billing.application.port.out.WaitlistPort;
import app.bolo.common.error.RefusedException;
import app.bolo.common.event.LearnerMerged;
import app.bolo.common.id.LearnerId;
import app.bolo.common.time.BoloClock;
import app.bolo.learner.application.port.in.CurrentLearnerUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WaitlistService implements WaitlistUseCase {

    static final Set<String> PLANS = Set.of("basic", "premium", "ultimate");
    static final Set<String> REGIONS = Set.of("bd", "intl");

    private final WaitlistPort port;
    private final CurrentLearnerUseCase learners;
    private final BoloClock clock;

    @Override
    public void join(LearnerId learner, String plan, String region, String timeZone) {
        if (!"account".equals(learners.view(learner).kind())) throw new RefusedException("sign-in");
        if (plan == null || !PLANS.contains(plan) || region == null || !REGIONS.contains(region)
                || (timeZone != null && timeZone.length() > 64)) {
            throw new RefusedException("bad-waitlist");
        }
        port.upsert(learner, plan, region, timeZone, clock.now());
    }

    @Override
    public Optional<Mine> mine(LearnerId learner) {
        return port.find(learner);
    }

    @Override
    public List<Entry> entries() {
        return port.entries();
    }

    @Override
    public List<Count> counts() {
        return port.counts();
    }

    @EventListener
    public void onLearnerMerged(LearnerMerged e) {
        port.moveLearner(e.from(), e.to());
    }
}
