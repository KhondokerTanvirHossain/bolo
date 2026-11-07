# ScreenReminder

Step 3 of the end of the first loop (PRG-5, PRG-7, US-6.5). It asks one question and asks for the notification permission on the same step, after the learner has chosen.

- **Top:** the PRD's own question, "When should Bolo check in?", with the promise that makes it safe to say yes: one a day, and none if they already talked.
- **The NotificationPreview** shows a real Buddy-story reminder at the chosen time, so the learner knows exactly what will arrive. It updates as they change the slot.
- **ReminderSlots:** Morning commute, Lunch, Evening commute and Night, with a sensible default time each. Preselect the slot nearest the time of this first session. **Pick an exact time** opens Android's time picker and replaces the slot times.
- **Bottom third:** one line warning that Android will ask next, then **Remind me at 6:30 pm** (the label follows the choice) and a quiet **Not now**.
- **If notifications are denied:** save the time anyway, land on Home with "Come back around 6:30 pm" under the Talk button, and don't ask again for 14 days.
