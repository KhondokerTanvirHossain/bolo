package app.bolo.reminder.adapter.in.web;

import app.bolo.common.error.SignedOutException;
import app.bolo.common.id.LearnerId;
import app.bolo.common.web.ApiError;
import app.bolo.common.web.CurrentLearner;
import app.bolo.reminder.application.port.in.ReminderUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;


/** PRG-5: Me → Daily reminder, the reminder step, and the browsers that get reminders. Accounts only (403 sign-in). */
@RestController
@RequiredArgsConstructor
class ReminderController {

    private final ReminderUseCase reminders;

    record Save(Boolean on, String time, String timeZone, Boolean keepPrivate) {
    }

    record Zone(String timeZone) {
    }

    record Keys(String p256dh, String auth) {
    }

    record Subscription(String endpoint, Keys keys) {
    }

    record Endpoint(String endpoint) {
    }

    record Opened(String timeZone, String reminder) {
    }

    @GetMapping("/api/reminder")
    ResponseEntity<?> view(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner) {
        return forAccount(learner, me -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reminders.view(me)));
    }

    @PutMapping("/api/reminder")
    ResponseEntity<?> save(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner, @RequestBody Save body) {
        return forAccount(learner, me -> ResponseEntity.ok(reminders.save(me, body.on(), body.time(), body.timeZone(), body.keepPrivate())));
    }

    @PostMapping("/api/reminder/not-now")
    ResponseEntity<?> notNow(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner,
                             @RequestBody(required = false) Zone body) {
        return forAccount(learner, me -> {
            reminders.notNow(me, body == null ? null : body.timeZone());
            return ResponseEntity.noContent().build();
        });
    }

    @PostMapping("/api/reminder/card/dismiss")
    ResponseEntity<?> dismiss(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner) {
        return forAccount(learner, me -> {
            reminders.dismissCard(me);
            return ResponseEntity.noContent().build();
        });
    }

    @PostMapping("/api/reminder/subscriptions")
    ResponseEntity<?> subscribe(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner,
                                @RequestBody Subscription body, @RequestHeader(name = "User-Agent", required = false) String ua) {
        return forAccount(learner, me -> {
            reminders.subscribe(me, body.endpoint(), body.keys() == null ? null : body.keys().p256dh(),
                    body.keys() == null ? null : body.keys().auth(), ua);
            return ResponseEntity.noContent().build();
        });
    }

    /** Also for guests and signed-out pages: a browser can always drop its own subscription. */
    @DeleteMapping("/api/reminder/subscriptions")
    ResponseEntity<Void> unsubscribe(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner,
                                     @RequestBody Endpoint body) {
        if (learner == null) throw new SignedOutException();
        reminders.unsubscribe(learner, body.endpoint());
        return ResponseEntity.noContent().build();
    }

    /** Called when Bolo opens (guests too: it does nothing without settings). */
    @PostMapping("/api/reminder/opened")
    ResponseEntity<Void> opened(@RequestAttribute(name = CurrentLearner.ATTRIBUTE, required = false) LearnerId learner,
                                @RequestBody(required = false) Opened body) {
        if (learner == null) throw new SignedOutException();
        reminders.opened(learner, body == null ? null : body.timeZone(), body == null ? null : body.reminder());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<?> forAccount(LearnerId learner, java.util.function.Function<LearnerId, ResponseEntity<?>> then) {
        if (learner == null) throw new SignedOutException();
        if (!reminders.account(learner)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("sign-in"));
        return then.apply(learner);
    }
}
