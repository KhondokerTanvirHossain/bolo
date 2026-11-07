# SessionRow

One past talk in History (PRG-8).

**Needs:** the date, a short topic, the scenario and length, up to three fix names, and whether the feedback was finished.

**Use**
- There is no chevron: the whole row is the target, and dropping it gives the fix tags the width to sit on one or two lines.
- Rows sit in a `bo-group` card, grouped under week labels ("This week", "Last week", then month names).
- The date is a big day number over a short month, so the list scans like a diary.
- Fix tags are `hills-soft` with `hills-ink`, using the fix card's own title ("The v sound", "Say it better"). They wrap to a second line rather than truncate.
- An unfinished feedback adds a `bo-tag--new` **In your drills** before the fix tags.
- TalkBack reads the row as one sentence: "6 November, The payment bug demo, free talk, 5 minutes, 3 things to try."

**Behaviour:** the whole row is the tap target; press dims the row to `surface-sunken` for 120 ms.
