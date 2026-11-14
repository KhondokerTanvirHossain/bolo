# ScreenReminderSettings

Me → **Daily reminder** (PRG-5, PRG-6, US-6.5, US-6.6). Added 2026-11-14. Every change saves at once; a failed save puts the earlier choice back with "That didn't save. Try again."

- **Remind me** switch, one for the account: off stops reminders on every device. Turning it on in a browser that hasn't allowed notifications asks for permission (the tap is the user gesture browsers need). Off hides the rest of the page except the switch.
- **ReminderSlots** and **Pick an exact time**, exactly as on ScreenReminder. The time is the learner's, the same on every device; Bolo keeps the learner's time zone from the browser.
- **Keep it private:** the reminder shows only "Time for today's talk." instead of a line from Bolo's notes (useful on a shared or work phone). The NotificationPreview below follows it. The preview shows the learner's real next line (stored after each talk) or a plain line if there is none yet.
- **The status line** under the preview says where reminders arrive, one of:
  - "On for this phone. Turn it on in each browser where you want reminders." (allowed here)
  - "Notifications are blocked for Bolo in this browser. Allow them in the browser's site settings." (denied; the switch stays on, so other devices still get them)
  - Switch on but this browser not signed up yet: "Not on for this browser yet." with a quiet **Turn on here** button.
  - iPhone tab: "On iPhone, add Bolo to your Home Screen first." with a quiet **How?** link to ScreenReminderIphone.
  - iPhone older than iOS 16.4: "This iPhone can't get reminders from a website. Update iOS to get them."
- There is no "reminders are resting" state: reminders pause after 3 ignored in a row (PRG-6) and start again whenever the learner opens Bolo, which they just did.
