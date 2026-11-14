# ScreenReminderIphone

Shown instead of the browser's permission box when the learner chose a reminder time on an **iPhone, in a browser tab** (Safari or Chrome; every iPhone browser is Safari underneath). Apple lets a website send notifications only after it is added to the Home Screen and opened from there (iOS 16.4 or later). Added 2026-11-14 (PRG-5, US-6.5).

- **When:** ScreenReminder's primary button on an iPhone tab reads **Remind me at 6:30 pm** as everywhere; tapping it saves the time first, then opens this screen instead of asking for permission (asking from a tab can't work).
- **Three steps** as a numbered SettingsRow group with icons: Share (`ios_share`), Add to Home Screen (`add_box`), open Bolo from the Home Screen (`touch_app`). The Share hint names both browsers' button places; there is no arrow pointing at the browser bar, since it moves between browsers and versions.
- **Your time is saved** line, so the learner knows nothing is lost if they add Bolo later.
- **Got it** lands on Home ("See you tomorrow" as usual). **No reminders** turns the reminder off and lands on Home.
- When the learner later opens Bolo from the Home Screen with a saved time and no permission yet, ScreenReminderTurnOn asks once.
- iPhones older than iOS 16.4 can't get web reminders at all: there the step is skipped quietly and Me → Daily reminder says so.
- Bangla helper line: to be written by the copywriter (not machine-translated).
