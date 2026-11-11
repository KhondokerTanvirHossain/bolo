package app.bolo.talk.application.service;

import app.bolo.common.error.RefusedException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.time.BoloClock;
import app.bolo.talk.application.port.in.PlansUseCase;
import app.bolo.talk.application.port.out.PlansPort;
import app.bolo.talk.domain.FreePlan;
import app.bolo.talk.domain.PlanAllowance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlansService implements PlansUseCase {

    static final String DAILY_CAP = "daily_talk_cap";
    static final int DEFAULT_CAP = 300;

    private final PlansPort port;
    private final BoloClock clock;

    @Override
    public void grant(LearnerId learner, String plan, int days, String source, String note) {
        if (days < 1 || days > 366 || monthly().get(plan) == null) throw new RefusedException("bad-grant");
        var now = clock.now();
        port.insert(learner, plan, now, now.plus(Duration.ofDays(days)), source, note, now);
    }

    @Override
    public Optional<Active> active(LearnerId learner) {
        return PlanAllowance.active(port.subscriptions(learner), monthly(), clock.now()).map(s -> new Active(s.plan(), s.endsAt()));
    }

    @Override
    public List<Plan> plans() {
        return port.plans();
    }

    @Override
    public void updatePlans(List<Plan> plans) {
        for (Plan p : plans) {
            if (p.monthlyTalks() < 0 || p.monthlyTalks() > 1000 || p.priceBdt() < 0 || p.priceUsd().signum() < 0) throw new RefusedException("bad-plan");
            port.savePlan(p);
        }
    }

    @Override
    public int dailyCap() {
        try {
            return Integer.parseInt(port.setting(DAILY_CAP, String.valueOf(DEFAULT_CAP)));
        } catch (NumberFormatException e) {
            return DEFAULT_CAP;
        }
    }

    @Override
    public void setDailyCap(int cap) {
        if (cap < 0 || cap > 100_000) throw new RefusedException("bad-cap");
        port.setSetting(DAILY_CAP, String.valueOf(cap));
    }

    @Override
    public int todayCount() {
        var today = clock.now().atZone(FreePlan.ZONE).toLocalDate().atStartOfDay(FreePlan.ZONE).toInstant();
        return port.startsSince(today);
    }

    Map<String, Integer> monthly() {
        return port.plans().stream().collect(Collectors.toMap(Plan::plan, Plan::monthlyTalks));
    }

    PlanAllowance.Allowance allowance(LearnerId learner, List<java.time.Instant> counted) {
        return PlanAllowance.of(counted, port.subscriptions(learner), monthly(), clock.now());
    }
}
