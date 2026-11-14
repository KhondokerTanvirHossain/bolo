package app.bolo.reminder.application.service;

import app.bolo.common.id.LearnerId;
import app.bolo.memory.application.port.in.MemoryUseCase;
import app.bolo.reminder.application.port.in.ReminderSent;
import app.bolo.reminder.application.port.out.PushPort;
import app.bolo.reminder.application.port.out.ReminderPort;
import app.bolo.reminder.domain.ReminderLines;
import app.bolo.reminder.domain.ReminderRules;
import app.bolo.reminder.domain.ReminderRules.Decision;
import app.bolo.reminder.domain.ReminderRules.Facts;
import app.bolo.reminder.domain.ReminderRules.Kind;
import app.bolo.reminder.domain.ReminderRules.State;
import app.bolo.talk.application.port.in.FreeTalksUseCase;
import app.bolo.talk.application.port.in.TalkQueryUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Sends the day's reminders (PRG-5/6): one per learner per local day, to every browser they turned it on in. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderSendService {

    private static final ZoneId BANGLADESH = ZoneId.of("Asia/Dhaka");

    private final ReminderPort reminders;
    private final PushPort push;
    private final TalkQueryUseCase talks;
    private final FreeTalksUseCase freeTalks;
    private final MemoryUseCase memory;
    private final ApplicationEventPublisher events;
    private final JsonMapper json;

    /** One pass: returns how many learners got a reminder. Never throws. */
    public int sendDue(Instant now) {
        if (!push.available()) return 0;
        int sent = 0;
        for (ReminderPort.Due due : reminders.candidates()) {
            try {
                // Fresh settings per learner: a long pass must not act on a snapshot taken before the learner opened Bolo.
                var fresh = reminders.find(due.learner()).orElse(null);
                if (fresh != null && fresh.enabled() && sendOne(due.learner(), fresh, now)) sent++;
            } catch (RuntimeException e) {
                log.warn("reminder for {} failed: {}", due.learner().value(), e.getClass().getSimpleName());
            }
        }
        return sent;
    }

    private boolean sendOne(LearnerId learner, ReminderPort.Settings s, Instant now) {
        State state = state(s);
        var window = ReminderRules.windowDay(state, now);
        if (window.isEmpty() || window.get().equals(s.lastSentOn())) return false;
        LocalDate day = window.get();
        Facts facts = facts(learner, s, state.zone(), day, now);
        Decision d = ReminderRules.decide(state, facts, now);
        if (d.stop()) {
            reminders.stop(learner, d.ignored(), now);
            return false;
        }
        if (d.kind() == Kind.NONE) return false;
        String story = storyLine(learner, s);
        String body = ReminderLines.text(d.kind(), s.keepPrivate(), story, (int) d.day().toEpochDay());
        UUID id = UUID.randomUUID();
        if (!reminders.claim(learner, d.day(), id, now, d.ignored(), d.kind() == Kind.COMEBACK)) return false;
        int reached = deliver(learner, body, "/?from=reminder&r=" + id, now);
        if (reached == 0) return false;
        // Only a reminder that reached a browser can be ignored (review, reminders I2) or counted as sent.
        reminders.markDelivered(learner, id);
        events.publishEvent(new ReminderSent(learner, ReminderLines.slot(s.localTime()), hookType(d.kind(), s.keepPrivate(), story)));
        return true;
    }

    /** Admin "Send me a test reminder": the next line, now, to every browser; ignores the rules. */
    public int sendTest(LearnerId learner, Instant now) {
        var s = reminders.find(learner).orElse(null);
        boolean keepPrivate = s != null && s.keepPrivate();
        String body = ReminderLines.text(Kind.NORMAL, keepPrivate, s == null ? null : storyLine(learner, s), 0);
        return deliver(learner, body, "/?from=reminder", now);
    }

    /** The stored story line, only while Bolo's next follow-up is still the one it was written from. */
    public String storyLine(LearnerId learner, ReminderPort.Settings s) {
        if (s.line() == null || s.lineSource() == null) return null;
        return memory.nextFollowUp(learner).filter(s.lineSource()::equals).map(f -> s.line()).orElse(null);
    }

    private int deliver(LearnerId learner, String body, String url, Instant now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "Bolo");
        payload.put("body", body);
        payload.put("url", url);
        payload.put("tag", "bolo-daily");
        byte[] bytes = json.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
        int reached = 0;
        for (ReminderPort.Device device : reminders.devices(learner)) {
            switch (push.send(new PushPort.Subscription(device.endpoint(), device.p256dh(), device.auth()), bytes)) {
                case SENT -> {
                    reached++;
                    reminders.markOk(device.endpoint(), now);
                }
                case GONE -> reminders.deleteEndpoint(device.endpoint());
                case FAILED -> { }
            }
        }
        return reached;
    }

    private Facts facts(LearnerId learner, ReminderPort.Settings s, ZoneId zone, LocalDate day, Instant now) {
        boolean talkedToday = talks.startedSince(learner, day.atStartOfDay(zone).toInstant());
        boolean talkSince = s.delivered() && s.lastSentAt() != null && talks.startedSince(learner, s.lastSentAt());
        var allowance = freeTalks.allowance(learner);
        boolean available = allowance.isEmpty() || allowance.get().left() > 0;
        boolean back = allowance.isPresent() && "free".equals(allowance.get().plan()) && "month".equals(allowance.get().window())
                && allowance.get().left() == allowance.get().total() && now.atZone(BANGLADESH).getDayOfMonth() == 1;
        return new Facts(talkedToday, talkSince, available, back);
    }

    static State state(ReminderPort.Settings s) {
        // A reminder that reached no browser doesn't count for the ignored run: as if none was sent.
        return new State(s.enabled(), LocalTime.parse(s.localTime()), zone(s.timeZone()), s.lastSentOn(), s.delivered() ? s.lastSentAt() : null, s.tapped(),
                s.ignored(), s.stoppedAt(), s.comebackSent());
    }

    private static ZoneId zone(String id) {
        try {
            return ZoneId.of(id);
        } catch (DateTimeException e) {
            return BANGLADESH;
        }
    }

    private static String hookType(Kind kind, boolean keepPrivate, String story) {
        if (kind == Kind.COMEBACK) return "comeback";
        if (kind == Kind.TALKS_BACK) return "talks_back";
        if (keepPrivate) return "private";
        return ReminderLines.valid(story) ? "story" : "plain";
    }
}
