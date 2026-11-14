# Daily reminders — design

Founder: "go" 2026-11-14 (skipped the questions; design system first, approval before development).
PRD items: PRG-5, PRG-6, PRG-7 (changed 2026-11-13), US-6.5, US-6.6, US-6.8; events `reminder_sent` / `reminder_opened`.
Design system v43: ScreenReminder (changed), ScreenReminderIphone, ScreenReminderTurnOn, ScreenReminderSettings (new),
ScreenMe and DesktopMe (Daily reminder row), DesktopReminder (phone link moved to P1), NotificationPreview, ReminderSlots.

## Why

Habit is the product's retention engine (PRD: "Daily reminder at a chosen time, Buddy story continuity"). Today
nothing brings a learner back the next day. One kind, personal reminder at the learner's own time, and never a nag.

## Goals

1. At the end of the first talk (after the drills offer or its Later), ask "When should Bolo check in?" with four
   slots and an exact time, then the notification permission on the same step.
2. One reminder a day at that time, only if the learner hasn't talked that day (their local day).
3. The reminder continues Bolo's story from Bolo's notes ("Bolo wants to know how the demo went."), unless
   Keep it private is on.
4. Back off: after 3 ignored in a row, stop; 7 days later send one "We're here whenever you're ready." and nothing
   more until the learner opens Bolo again.
5. Free plan after the first week: remind only when a free talk is available; on the day free talks come back, the
   reminder says "Your 2 free talks are back."
6. Me → Daily reminder: on/off, the time, Keep it private. The same on desktop (DesktopMe row → DesktopReminder dialog).
7. iPhone: reminders work once Bolo is on the Home Screen (iOS 16.4+); the reminder step explains how.
8. Existing learners who finished their first talk before this ships get one Home card "Get a daily reminder".

## Non-goals

- No email, SMS or WhatsApp reminders. No native app.
- No "Remind me on my phone" scan code on desktop (P1).
- No streak-loss or guilt copy, no counts of missed days, never "you haven't practised".
- No reminders for guests (sign-in already comes before the first talk).
- No per-weekday schedules, no second reminder per day.

## Anti-goals

- Never more than 1 reminder per learner per local day, across all their devices (each device gets the same one).
- Never a reminder on a day the learner already talked, or at a time far from their chosen time (a server restart
  must not send a 6:30 pm reminder at midnight).
- Never sensitive content on a lock screen: lines come only from Bolo's notes (which already exclude health,
  religion, politics, money, contact details and crisis talks), and Keep it private removes even that.
- Never ask for notification permission when a page opens; only after the learner taps.

## How it works (decided)

**Delivery: Web Push** (service worker + VAPID keys), sent by Bolo's server. A web app manifest makes Bolo
installable (name Bolo, icons, `display: standalone`), which iPhone requires for push. VAPID private key is stored on
the box like the other vendor keys, never in the repo or chat.

**Where the learner sees it**
- Phone, end of first talk: ScreenReminder. Preselect the slot nearest the current time. Primary "Remind me at
  6:30 pm" saves the time, then asks the browser. Not now saves no reminder.
- iPhone in a browser tab: the button saves the time and opens ScreenReminderIphone (Share → Add to Home Screen →
  open from there). First open from the Home Screen with a saved time and no permission: ScreenReminderTurnOn, once.
- Permission denied: the time stays saved; Home's note reads "Come back around 6:30 pm"; Bolo doesn't ask again on
  its own for 14 days (Me can still turn it on).
- Desktop: DesktopReminder card at the same step; "Add to my calendar instead" downloads a daily `.ics` event at the
  chosen time and saves the time with reminders off.
- Home after the first loop: "See you tomorrow" as today.
- Me → Daily reminder (ScreenReminderSettings), DesktopMe row. Status line rules are in ScreenReminderSettings.

**What a reminder says** (≤ 80 characters, no emoji, never names English, a course or a lesson)
- Normal day: one line from Bolo's notes — the same follow-up the next talk would open with (TLK-11's picker:
  a due plan, then a worry, then their practice), written as one short line by the LLM at send time
  ("Bolo wants to know how the demo went.").
- No notes, remembering off, Keep it private, or the line fails: a rotating plain line ("Time for today's talk.",
  "Bolo has a question for you today.", "Bolo is on the hilltop. Got 5 minutes?"). Keep it private always uses
  "Time for today's talk."
- Free talks back (free plan, the day the monthly free talks return): "Your 2 free talks are back."
- After the 3-ignored stop, day 7: "We're here whenever you're ready."
- Tapping it opens Bolo's Home (the installed app on iPhone) and logs `reminder_opened`.

**Rules (server, local time = the learner's saved time zone)**
- Due when: reminders on, at least one device subscribed, local now ≥ chosen time and < chosen time + 2 h, nothing
  sent today, no talk started today, not stopped.
- Free plan after the first week (7 days since the account was made) with no free talk available: not sent; on the
  day talks come back, send the "free talks are back" line instead of the normal one.
- Ignored: a sent reminder that, by the next due time, wasn't tapped and no talk was started since. 3 ignored in a
  row → stopped. 7 days after stopping → one "We're here whenever you're ready." Then nothing.
- Opening Bolo (any Home load) clears the ignored count and the stop.
- Every device the learner turned reminders on in gets the reminder. A device whose subscription is gone (404/410)
  is removed.
- The time zone comes from the browser on every save and on Home load if it changed.

**Data (owned by a new `reminder` module, or progress — builder's call within the hexagonal rules)**
- Reminder settings per learner: on, local time (HH:MM), time zone, keep private, ignored count, stopped at,
  comeback sent, last sent local date, last sent at, last asked (for the 14-day rule).
- Push subscriptions: learner, endpoint (unique), keys, user agent, created, last success.
- "Never asked" = no settings row: that's how the Home card for existing learners is chosen (HOM-1 secondary card,
  once; tapping opens ScreenReminder; dismiss or answer and it never shows again).

**For the founder to test (testable increment):** Admin portal → "Send me a test reminder" sends the next real
reminder line to the admin's own devices now, ignoring the rules. Set the time 2 minutes ahead in Me → Daily reminder
to test the real path.

## Docs

PRD: PRG-5 / US-6.5 (iPhone Home Screen step, Keep it private, calendar on desktop), PRG-6 (opening Bolo resets),
US-6.8 (drills offer → reminder; already changed). RUNBOOK: "Reminders" (VAPID keys on the box, how to send a test,
how to read `reminder_sent`). Smoke: reminder endpoints need a session.

## Left to the builder

Module placement, table and endpoint names, the Web Push library (or a direct implementation), the scheduler's
interval (≤ 1 minute), the exact rotating lines beyond those above, icons for the manifest, test names.

## Builder check (2026-11-14) — settled by Claude (flagged to the founder)

1. **Several devices.** "Remind me" is one switch for the account (off stops every device). A browser that hasn't
   been signed up while the switch is on shows "Not on for this browser yet." with a quiet **Turn on here** button
   (asks permission, then subscribes this browser). Signing out removes that browser's subscription.
2. **The preview line.** After each talk, once Bolo's notes are updated, the server writes the learner's *next
   reminder line* (the LLM line from the follow-up Bolo would open with) and stores it. The preview on every reminder
   screen and the real reminder both use that stored line; if there is none yet (notes still updating, remembering
   off, Keep it private) both use a plain line. So the preview never promises a line that won't arrive, and the
   first-loop screen adds no LLM wait.
3. **The Home card for existing learners** ("Get a daily reminder") ranks after "Finish your feedback" and before the
   drills card in US-6.9's secondary column. It shows until answered or dismissed, on at most 3 different days.
   "Not now" on any reminder screen saves the row as asked-and-off, so the card never comes back.
4. **Free first week** follows the existing free plan (`FreeTalksUseCase`: from the first talk, Bangladesh dates).
   No reminder is sent to anyone, free or paid, with no talk available that day; the "talks are back" line goes out on
   the day they return (US-6.5's "on the 1st" is corrected in the PRD).
5. **Same-day conflicts:** stopped beats everything except the one day-7 "We're here whenever you're ready";
   "talks are back" beats the normal line. A late time whose 2-hour window crosses midnight counts as the day it
   started. The PRD's US-6.5/US-6.8 still mention the dropped save step; fixed with the build.
