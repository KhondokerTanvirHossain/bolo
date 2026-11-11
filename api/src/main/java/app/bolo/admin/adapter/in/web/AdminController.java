package app.bolo.admin.adapter.in.web;

import app.bolo.admin.application.service.AdminService;
import app.bolo.common.id.LearnerId;
import app.bolo.common.web.CurrentLearner;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** The admin portal API (P2a). Anyone who isn't an admin gets 404: it doesn't say it exists. */
@RestController
@RequiredArgsConstructor
class AdminController {

    private final AdminService admin;

    @ResponseStatus(HttpStatus.NOT_FOUND)
    static class NotFound extends RuntimeException {
    }

    private LearnerId admin(LearnerId learner) {
        if (!admin.isAdmin(learner)) throw new NotFound();
        return learner;
    }

    private static LearnerId target(String id) {
        try {
            return new LearnerId(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            throw new NotFound();
        }
    }

    @GetMapping("/api/admin/users")
    Map<String, Object> users(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner,
                              @RequestParam(name = "q", defaultValue = "") String q) {
        admin(learner);
        return Map.of("users", admin.users(q));
    }

    record Unlimited(boolean on) {
    }

    @PostMapping("/api/admin/users/{id}/unlimited")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unlimited(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner, @PathVariable String id,
                   @RequestBody Unlimited body) {
        admin.setUnlimited(admin(learner), target(id), body.on());
    }

    record Grant(String plan, int days, String note) {
    }

    @PostMapping("/api/admin/users/{id}/grant")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void grant(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner, @PathVariable String id,
               @RequestBody Grant body) {
        admin.grant(admin(learner), target(id), body.plan(), body.days(), body.note());
    }

    @GetMapping("/api/admin/usage")
    AdminService.Usage usage(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner,
                             @RequestParam(name = "days", defaultValue = "14") int days) {
        admin(learner);
        return admin.usage(days);
    }

    record Reject(String reason) {
    }

    @GetMapping("/api/admin/payments")
    Map<String, Object> payments(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner) {
        admin(learner);
        return Map.of("payments", admin.payments());
    }

    @PostMapping("/api/admin/payments/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void approvePayment(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner, @PathVariable long id) {
        admin.approvePayment(admin(learner), id);
    }

    @PostMapping("/api/admin/payments/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void rejectPayment(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner, @PathVariable long id,
                       @org.springframework.web.bind.annotation.RequestBody Reject body) {
        admin.rejectPayment(admin(learner), id, body == null ? null : body.reason());
    }

    @GetMapping("/api/admin/waitlist")
    Map<String, Object> waitlist(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner) {
        admin(learner);
        return admin.waitlist();
    }

    @GetMapping("/api/admin/help")
    Map<String, Object> help(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner) {
        admin(learner);
        return Map.of("requests", admin.help());
    }

    @PostMapping("/api/admin/help/{id}/done")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void helpDone(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner, @PathVariable long id) {
        admin.helpDone(admin(learner), id);
    }

    @GetMapping("/api/admin/settings")
    AdminService.Settings settings(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner) {
        admin(learner);
        return admin.settings();
    }

    @PutMapping("/api/admin/settings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void saveSettings(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner,
                      @RequestBody AdminService.Settings body) {
        admin.saveSettings(admin(learner), body);
    }
}
