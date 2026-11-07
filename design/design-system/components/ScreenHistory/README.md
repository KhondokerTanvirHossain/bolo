# ScreenHistory

Progress → History (PRG-8, US-6.7): every past talk, newest first, grouped by week.

- Each SessionRow shows the date, a topic the Coach names from the talk ("The payment bug demo"), the scenario and length, and its fixes as violet tags, because fixes belong to the Coach.
- A talk whose feedback wasn't finished carries a sun-soft **In your drills** tag first: its fixes moved to drills, and the row still opens the full feedback.
- Tapping a row opens ScreenHistorySession.
- **Empty state:** History can only be empty if the learner deleted everything, because the first talk always lands here. Then use a StatePanel: "Your talks will appear here", one Bangla line, and **Talk** as the action.
- **Free plan (public launch):** rows older than 7 days show a lock in place of their fix tags and "See all with Plus" in `sun-ink` at the end of the list (US-8.7). Locked rows still show the date and topic, so nothing feels deleted.
