package app.bolo.reminder.application.port.in;

import app.bolo.common.id.LearnerId;

/** A daily reminder went out (analytics: reminder_sent). slot: morning|lunch|evening|night|exact; hookType: story|plain|private|talks_back|comeback. */
public record ReminderSent(LearnerId learner, String slot, String hookType) {
}
