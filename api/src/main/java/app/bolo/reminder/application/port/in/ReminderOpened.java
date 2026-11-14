package app.bolo.reminder.application.port.in;

import app.bolo.common.id.LearnerId;

/** The learner opened Bolo from a reminder (analytics: reminder_opened). */
public record ReminderOpened(LearnerId learner) {
}
