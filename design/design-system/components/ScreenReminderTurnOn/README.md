# ScreenReminderTurnOn

A bottom sheet over Home, shown **once** when Bolo is opened from the iPhone Home Screen (standalone) with a saved reminder time and notifications not yet allowed. Follows ScreenReminderIphone. Added 2026-11-14 (PRG-5).

- Title "Turn on your reminder?", the promise line with the saved time, the NotificationPreview, and the one-line warning that the iPhone asks next.
- **Turn on reminders** asks for permission (the tap is what iOS requires) and closes the sheet with a short toast "Reminder on: 6:30 pm". If the learner taps Don't Allow, the time stays saved and Home's note reads "Come back around 6:30 pm" (as ScreenReminder's denied rule).
- **Not now** closes it; it is not shown again. Me → Daily reminder can still turn it on.
- Never over a talk, the feedback, or another sheet; it waits for the next Home.
