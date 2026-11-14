package app.bolo.reminder.application.service;

import app.bolo.common.error.RefusedException;
import app.bolo.common.event.LearnerMerged;
import app.bolo.common.id.LearnerId;
import app.bolo.common.time.BoloClock;
import app.bolo.learner.application.port.in.CurrentLearnerUseCase;
import app.bolo.reminder.application.port.in.ReminderUseCase;
import app.bolo.reminder.application.port.out.PushPort;
import app.bolo.reminder.application.port.out.ReminderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ReminderService implements ReminderUseCase {

    static final String DEFAULT_TIME = "18:30";
    static final String DEFAULT_ZONE = "Asia/Dhaka";
    private static final Pattern TIME = Pattern.compile("^([01][0-9]|2[0-3]):[0-5][0-9]$");
    private static final int CARD_DAYS = 3;

    private final ReminderPort reminders;
    private final PushPort push;
    private final CurrentLearnerUseCase learners;
    private final BoloClock clock;
    private final ReminderSendService sender;
    private final org.springframework.context.ApplicationEventPublisher events;

    @Override
    public boolean account(LearnerId learner) {
        return "account".equals(learners.view(learner).kind());
    }

    @Override
    public ReminderView view(LearnerId learner) {
        var s = reminders.find(learner).orElse(null);
        boolean keepPrivate = s != null && s.keepPrivate();
        return new ReminderView(s != null && s.askedAt() != null, s != null && s.enabled(),
                s == null ? DEFAULT_TIME : s.localTime(), s == null ? DEFAULT_ZONE : s.timeZone(), keepPrivate,
                preview(learner, keepPrivate), reminders.deviceCount(learner), push.publicKey());
    }

    /** The line the next reminder would show: the fresh story line, else the first plain line. */
    String preview(LearnerId learner, boolean keepPrivate) {
        var s = reminders.find(learner).orElse(null);
        String story = s == null ? null : sender.storyLine(learner, s);
        return app.bolo.reminder.domain.ReminderLines.text(app.bolo.reminder.domain.ReminderRules.Kind.NORMAL, keepPrivate, story, 0);
    }

    @Override
    public int sendTest(LearnerId learner) {
        return sender.sendTest(learner, clock.now());
    }

    @Override
    public ReminderView save(LearnerId learner, Boolean on, String time, String timeZone, Boolean keepPrivate) {
        if (time != null && !TIME.matcher(time).matches()) throw new RefusedException("reminder");
        if (timeZone != null && zone(timeZone) == null) throw new RefusedException("reminder");
        var s = reminders.find(learner).orElse(null);
        reminders.saveChoices(learner,
                on != null ? on : s != null && s.enabled(),
                time != null ? time : s == null ? DEFAULT_TIME : s.localTime(),
                timeZone != null ? timeZone : s == null ? DEFAULT_ZONE : s.timeZone(),
                keepPrivate != null ? keepPrivate : s != null && s.keepPrivate(),
                clock.now(), clock.now());
        return view(learner);
    }

    @Override
    public void notNow(LearnerId learner, String timeZone) {
        save(learner, false, null, zone(timeZone) == null ? null : timeZone, null);
    }

    @Override
    public void dismissCard(LearnerId learner) {
        reminders.ensure(learner, clock.now());
        reminders.dismissCard(learner);
    }

    @Override
    public void subscribe(LearnerId learner, String endpoint, String p256dh, String auth, String userAgent) {
        if (endpoint == null || !endpoint.startsWith("https://") || endpoint.length() > 2048
                || blankOrLong(p256dh) || blankOrLong(auth)) throw new RefusedException("subscription");
        String ua = userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 300));
        reminders.upsertDevice(learner, endpoint, p256dh, auth, ua, clock.now());
    }

    private static boolean blankOrLong(String s) {
        return s == null || s.isBlank() || s.length() > 200;
    }

    @Override
    public void unsubscribe(LearnerId learner, String endpoint) {
        if (endpoint != null) reminders.deleteDevice(learner, endpoint);
    }

    @Override
    public void opened(LearnerId learner, String timeZone, String reminderId) {
        if (reminders.find(learner).isEmpty()) return;
        reminders.clearBackOff(learner, zone(timeZone) == null ? null : timeZone, clock.now());
        if (reminderId != null) {
            try {
                if (reminders.markTapped(learner, UUID.fromString(reminderId))) {
                    events.publishEvent(new app.bolo.reminder.application.port.in.ReminderOpened(learner));
                }
            } catch (IllegalArgumentException ignored) {
                // not one of ours
            }
        }
    }

    @Override
    public boolean cardDue(LearnerId learner, LocalDate today) {
        var s = reminders.find(learner).orElse(null);
        if (s != null && (s.askedAt() != null || s.cardDismissed())) return false;
        if (s != null && s.cardDays() >= CARD_DAYS && !today.equals(s.cardLastOn())) return false;
        reminders.ensure(learner, clock.now());
        reminders.countCardDay(learner, today);
        return true;
    }

    @EventListener
    public void onLearnerMerged(LearnerMerged e) {
        reminders.move(e.from(), e.to());
    }

    static ZoneId zone(String id) {
        if (id == null || id.isBlank() || id.length() > 64) return null;
        try {
            return ZoneId.of(id);
        } catch (DateTimeException e) {
            return null;
        }
    }
}
