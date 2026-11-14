package app.bolo.reminder.application.port.in;

import app.bolo.common.id.LearnerId;

import java.time.LocalDate;

/** Daily reminders (PRG-5/6): the learner's time and choices, the browsers that get them, and Home's one-time card. */
public interface ReminderUseCase {

    /**
     * asked: the learner answered the reminder step; time: local HH:MM; preview: the line the next reminder would show;
     * devices: browsers subscribed; publicKey: the VAPID key a browser subscribes with.
     */
    record ReminderView(boolean asked, boolean on, String time, String timeZone, boolean keepPrivate, String preview,
                        int devices, String publicKey) {
    }

    /** Reminders are for signed-in accounts only. */
    boolean account(LearnerId learner);

    ReminderView view(LearnerId learner);

    /** Null keeps a value. Refuses with "reminder" for a bad time or zone. Marks the step answered. */
    ReminderView save(LearnerId learner, Boolean on, String time, String timeZone, Boolean keepPrivate);

    /** "Not now" on the reminder step: answered, off. */
    void notNow(LearnerId learner, String timeZone);

    void dismissCard(LearnerId learner);

    /** Refuses with "subscription" for a non-https endpoint or missing keys. An endpoint moves to this learner. */
    void subscribe(LearnerId learner, String endpoint, String p256dh, String auth, String userAgent);

    void unsubscribe(LearnerId learner, String endpoint);

    /** The learner opened Bolo: clears the back-off, keeps the zone current, and a matching reminder id counts as tapped. */
    void opened(LearnerId learner, String timeZone, String reminderId);

    /** Home's "Get a daily reminder" card: never answered, not dismissed, shown on at most 3 days (this call counts the day). */
    boolean cardDue(LearnerId learner, LocalDate today);

    /** Admin "Send me a test reminder": the next line to every browser now; returns how many it reached. */
    int sendTest(LearnerId learner);
}
