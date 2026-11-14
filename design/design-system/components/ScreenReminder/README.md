# ScreenReminder

Step 3 of the end of the first loop (PRG-5, PRG-7, US-6.5). It asks one question and asks for the notification permission on the same step, after the learner has chosen.

- **Top:** the PRD's own question, "When should Bolo check in?", with the promise that makes it safe to say yes: one a day, and none if they already talked.
- **The NotificationPreview** shows a real Buddy-story reminder at the chosen time, so the learner knows exactly what will arrive. It updates as they change the slot.
- **ReminderSlots:** Morning commute, Lunch, Evening commute and Night, with a sensible default time each. Preselect the slot nearest the time of this first session. **Pick an exact time** opens the phone's own time picker and replaces the slot times.
- **Bottom third:** one line warning that the phone asks next, then **Remind me at 6:30 pm** (the label follows the choice) and a quiet **Not now**.
- **If notifications are denied:** save the time anyway, land on Home with "Come back around 6:30 pm" under the Talk button, and don't ask again for 14 days.
- **On an iPhone in a browser tab** (changed 2026-11-14): the button saves the time and opens ScreenReminderIphone instead of the permission box, since iPhone only sends reminders to apps on the Home Screen. Opened from the Home Screen, the iPhone asks as usual.
- **Not now** saves no reminder; Me → Daily reminder can turn it on later.
- This screen comes after the drills offer (or its Later) at the end of the first talk only (PRG-7). Learners who finished their first talk before reminders existed see a one-time Home card "Get a daily reminder" that opens it.
