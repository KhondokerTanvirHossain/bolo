package app.bolo.admin.application.service;

import app.bolo.admin.application.port.out.AdminReportsPort;
import app.bolo.common.id.LearnerId;
import app.bolo.common.time.BoloClock;
import app.bolo.learner.application.port.in.CurrentLearnerUseCase;
import app.bolo.learner.application.port.in.LearnerAdminUseCase;
import app.bolo.talk.application.port.in.PlansUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** The founder's admin portal (P2a): only signed-in accounts whose email is in bolo.admin.emails. */
@Slf4j
@Service
public class AdminService {

    static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

    private final Set<String> admins;
    private final CurrentLearnerUseCase learners;
    private final LearnerAdminUseCase learnerAdmin;
    private final PlansUseCase plans;
    private final AdminReportsPort reports;
    private final app.bolo.help.application.port.in.HelpUseCase help;
    private final app.bolo.billing.application.port.in.WaitlistUseCase waitlist;
    private final app.bolo.billing.application.port.in.PaymentUseCase payments;
    private final BoloClock clock;

    public AdminService(@Value("${bolo.admin.emails:}") String emails, CurrentLearnerUseCase learners, LearnerAdminUseCase learnerAdmin,
                        PlansUseCase plans, AdminReportsPort reports, BoloClock clock, app.bolo.help.application.port.in.HelpUseCase help,
                        app.bolo.billing.application.port.in.WaitlistUseCase waitlist,
                        app.bolo.billing.application.port.in.PaymentUseCase payments) {
        this.help = help;
        this.payments = payments;
        this.waitlist = waitlist;
        this.admins = Arrays.stream(emails.split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        this.learners = learners;
        this.learnerAdmin = learnerAdmin;
        this.plans = plans;
        this.reports = reports;
        this.clock = clock;
    }

    /** An admin is a signed-in Google account whose email is listed (a guest never is). */
    public boolean isAdmin(LearnerId learner) {
        if (learner == null) return false;
        var view = learners.view(learner);
        return "account".equals(view.kind()) && view.account() != null && admins.contains(view.account().email().toLowerCase(Locale.ROOT));
    }

    public List<AdminReportsPort.User> users(String q) {
        var monthStart = clock.now().atZone(DHAKA).toLocalDate().withDayOfMonth(1).atStartOfDay(DHAKA).toInstant();
        return reports.users(q, monthStart);
    }

    public void setUnlimited(LearnerId admin, LearnerId target, boolean on) {
        learnerAdmin.setUnlimited(target, on);
        log.info("admin {} set unlimited={} for {}", admin, on, target);
    }

    public void grant(LearnerId admin, LearnerId target, String plan, int days, String note) {
        plans.grant(target, plan, days, "admin", note);
        log.info("admin {} gave {} for {} days to {}", admin, plan, days, target);
    }

    /** P2e: bKash payments to check: pending first, then the latest decided. */
    public List<app.bolo.billing.application.port.in.PaymentUseCase.Payment> payments() {
        return payments.forAdmin();
    }

    public void approvePayment(LearnerId admin, long id) {
        payments.approve(admin, id);
    }

    public void rejectPayment(LearnerId admin, long id, String reason) {
        payments.reject(admin, id, reason);
    }

    /** P2c: who is waiting for which plan, by region. */
    public java.util.Map<String, Object> waitlist() {
        return java.util.Map.of("counts", waitlist.counts(), "entries", waitlist.entries());
    }

    public List<app.bolo.help.application.port.in.HelpUseCase.Request> help() {
        return help.open();
    }

    public void helpDone(LearnerId admin, long id) {
        help.done(id);
        log.info("admin {} closed help request {}", admin, id);
    }

    public record Now(int live, int today, int cap) {
    }

    public record Usage(List<AdminReportsPort.Day> days, Now now) {
    }

    public Usage usage(int days) {
        int n = Math.max(1, Math.min(days, 90));
        var today = clock.now().atZone(DHAKA).toLocalDate();
        return new Usage(reports.days(today.minusDays(n - 1L), today),
                new Now(reports.liveTalks(clock.now().minus(Duration.ofHours(2))), plans.todayCount(), plans.dailyCap()));
    }

    /** DBF-8: the last 30 days of ratings and the 20 newest notes. */
    public AdminReportsPort.Ratings ratings() {
        return reports.ratings(clock.now().minus(Duration.ofDays(30)), 20);
    }

    /** bkashNumber: null leaves it as it is; "" turns bKash payments off (P2e). */
    public record Settings(Integer dailyTalkCap, List<PlansUseCase.Plan> plans, String bkashNumber) {
    }

    public Settings settings() {
        return new Settings(plans.dailyCap(), plans.plans(), payments.bkashNumber().orElse(""));
    }

    /** All or nothing (review): a missing cap or a bad plan changes nothing. */
    @org.springframework.transaction.annotation.Transactional
    public void saveSettings(LearnerId admin, Settings s) {
        if (s == null || s.dailyTalkCap() == null || s.dailyTalkCap() < 0 || s.dailyTalkCap() > 100_000) {
            throw new app.bolo.common.error.RefusedException("bad-cap");
        }
        if (s.plans() != null) plans.updatePlans(s.plans());
        if (s.bkashNumber() != null) payments.setBkashNumber(s.bkashNumber());
        plans.setDailyCap(s.dailyTalkCap());
        log.info("admin {} saved settings: cap {}", admin, s.dailyTalkCap());
    }
}
