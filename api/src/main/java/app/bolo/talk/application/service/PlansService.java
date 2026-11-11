package app.bolo.talk.application.service;

import app.bolo.common.error.RefusedException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.time.BoloClock;
import app.bolo.talk.application.port.in.PlansUseCase;
import app.bolo.talk.application.port.out.PlansPort;
import app.bolo.talk.domain.FreePlan;
import app.bolo.talk.domain.PlanAllowance;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PlansService implements PlansUseCase {

    static final String DAILY_CAP = "daily_talk_cap";
    static final int DEFAULT_CAP = 300;

    private final PlansPort port;
    private final BoloClock clock;
    private final Duration attachWithin;

    public PlansService(PlansPort port, BoloClock clock, @org.springframework.beans.factory.annotation.Value("${bolo.talk.attach-within-ms}") long attachWithinMs) {
        this.port = port;
        this.clock = clock;
        this.attachWithin = Duration.ofMillis(attachWithinMs);
    }

    @Override
    public java.time.Instant grant(LearnerId learner, String plan, int days, String source, String note) {
        if (days < 1 || days > 366 || monthly().get(plan) == null) throw new RefusedException("bad-grant");
        var now = clock.now();
        // P2d: a month earned (or given) while on the same plan is a month more, not the same days twice.
        var start = port.subscriptions(learner).stream().filter(s -> s.plan().equals(plan) && s.endsAt().isAfter(now))
                .map(PlanAllowance.Sub::endsAt).max(java.util.Comparator.naturalOrder()).orElse(now);
        var end = start.plus(Duration.ofDays(days));
        port.insert(learner, plan, start, end, source, note, now);
        return end;
    }

    @Override
    public Optional<Active> active(LearnerId learner) {
        return PlanAllowance.active(port.subscriptions(learner), monthly(), clock.now()).map(s -> new Active(s.plan(), s.endsAt()));
    }

    @Override
    public List<Plan> plans() {
        return port.plans();
    }

    /** All or nothing: every plan is checked before any is saved; unknown plans and missing prices are refused. */
    @Override
    @org.springframework.transaction.annotation.Transactional
    public void updatePlans(List<Plan> plans) {
        var known = monthly().keySet();
        for (Plan p : plans) {
            if (p == null || p.plan() == null || !known.contains(p.plan()) || p.monthlyTalks() < 0 || p.monthlyTalks() > 1000
                    || p.priceBdt() < 0 || p.priceBdt() > 1_000_000 || p.priceUsd() == null || p.priceUsd().signum() < 0
                    || p.priceUsd().compareTo(new java.math.BigDecimal("100000")) > 0) {
                throw new RefusedException("bad-plan");
            }
        }
        plans.forEach(port::savePlan);
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
        var now = clock.now();
        var today = now.atZone(FreePlan.ZONE).toLocalDate().atStartOfDay(FreePlan.ZONE).toInstant();
        return port.startsSince(today, now.minus(attachWithin));
    }

    Map<String, Integer> monthly() {
        return port.plans().stream().collect(Collectors.toMap(Plan::plan, Plan::monthlyTalks));
    }

    PlanAllowance.Allowance allowance(LearnerId learner, List<java.time.Instant> counted) {
        return PlanAllowance.of(counted, port.subscriptions(learner), monthly(), clock.now());
    }
}
