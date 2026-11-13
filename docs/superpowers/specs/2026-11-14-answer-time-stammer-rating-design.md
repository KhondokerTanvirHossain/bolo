# Time to answer, I stammer, and "How was today?" — design

Founder approval: 2026-11-14 ("OKAY GO") on the chat proposal; rating card ScreenRating in design system v40.
PRD items: AX-7 (changed), AX-8, DBF-8 / US-4.11 (changed: typed note), PRG-7 (changed 2026-11-13).

## Why

- **Time to answer (AX-7, changed).** People differ in thinking time. The founder wants Bolo to answer the moment he
  stops; his wife needs 10–30 s before she starts. Today one timing fits everyone: Bolo encourages at 8 s of silence,
  so a slow starter is interrupted almost every turn, and a fast speaker waits 2.5 s after every turn. The PRD's
  "double the timers / off" switch is replaced by three speeds and an automatic default. Never "off": if someone stays
  quiet, Bolo still steps in (founder: "if it is quiet it should force").
- **Automatic default.** The founder suggested choosing by how good the learner is in the first talk. Accepted
  pushback: measure the behaviour itself (how long they take to start answering), not English skill, and keep
  adjusting over later talks rather than deciding from one nervous first talk.
- **I stammer (AX-8).** Bolo waits through repeats and blocks without prompting, the turn doesn't end early, and the
  feedback never names repeats or long pauses as something to fix.
- **How was today? (DBF-8).** The founder needs a simple satisfaction signal (PRD goal: ≥ 70 % of ratings are 4–5).

## Goals

1. Settings → Speaking → **Time to answer**: Automatic (default), Quick, Normal, Relaxed. Applies from the next talk.
2. Bolo measures each answer's start delay and, while the setting is Automatic, picks the speed after every talk.
3. Settings → Speaking → **I stammer** switch (off by default). Applies from the next talk and to that talk's feedback.
4. A "How was today?" sheet after the feedback of talks 2, 3, 4, 7, 10, 13 …; five faces, optional typed note.
5. Admin portal → **Ratings** tab: 30-day average, share of 4–5, count, latest notes.

## Non-goals

- No "off" for the silence help. No per-talk speed switch inside the talk screen.
- No voice comment on the rating (typed note only; decision 2026-11-13).
- No change to the Coach's filler habit ("um", "uh" …) for people who stammer.
- No rating from History (only right after a talk's feedback).

## Anti-goals

- Bolo must never wait forever: every speed ends in a pause of the talk.
- A learner who chose a speed must never have it changed by Automatic.
- The rating must never block the feedback, the drills offer or Talk again, and never appears after talk 1.

## 1. Speeds

Two timers decide pace (both exist today; numbers move into config per speed, `bolo.talk.speeds.*`):

- **Silence help (TLK-6, QuietClock)** — counted over the learner's turn time before they speak: encourage, then a
  starter card, then pause the talk.
- **Turn end (TLK-4, TurnTaker/TurnPolicy)** — after they stop: start a speculative reply, end the turn, or end it
  later when the last word sounds unfinished ("and", "but", "um" …).

| Speed | Encourage | Starter card | Pause | Speculate | Turn ends | Unfinished turn ends |
|---|---|---|---|---|---|---|
| quick | 5 s | 10 s | 30 s | 0.7 s | 1.5 s | 3 s |
| normal (today) | 8 s | 15 s | 45 s | 1 s | 2.5 s | 4 s |
| relaxed | 30 s | 45 s | 120 s | 1.5 s | 3.5 s | 5.5 s |

**I stammer** raises each value to at least: encourage 16 s, starter 30 s, pause 90 s, speculate 1.5 s, turn ends
4.5 s, unfinished 6.5 s (i.e. `max(speed, stammer floor)` per value). Speech with no words (UNCLEAR, TLK-7) already
waits the turn-end time, so it waits the longer time too.

The speed and stammer are fixed for a talk when it starts (like the voice and mood): `TalkView` carries the resolved
speed (`quick|normal|relaxed`, never `auto`) and `stammer`.

## 2. Measuring and Automatic

- **Start delay** of a learner turn = the silent turn time from "your turn" to the first speech frame of that turn,
  as the QuietClock counts it (it already carries across Bolo's encourage re-prompt and excludes the time Bolo
  speaks). Saved on the learner's `talk_turn` row as `start_ms` (null for Buddy rows and old rows).
- **After every talk ends** (the `TalkEnded` event, any reason), the talk module takes the learner's last 30 learner
  turns with a `start_ms`, newest first, across talks. Fewer than 5 → no change. Otherwise the median picks:
  `< 3000 ms → quick`, `≤ 6000 ms → normal`, `> 6000 ms → relaxed`. It saves this as the learner's
  `answerTimeAuto` through a learner use case, whatever the learner's own choice is (so switching back to Automatic
  gives a current value). A failure only logs.
- The first talk runs at the default `answerTimeAuto = normal`.
- **Resolution at talk start:** `answerTime == auto ? answerTimeAuto : answerTime`.

## 3. Settings

- Table `learner_settings` (V28): `answer_time text not null default 'auto'` check in (auto, quick, normal, relaxed);
  `answer_time_auto text not null default 'normal'` check in (quick, normal, relaxed);
  `stammer boolean not null default false`.
- `SettingsView` and `GET /api/me` add `answerTime`, `answerTimeAuto`, `stammer`.
- `PUT /api/me/settings` accepts `answerTime` and `stammer` (null/missing keeps the current value; bad value → 422
  code `answer-time`). `answerTimeAuto` in the body is ignored: only Bolo sets it.
- New learner use case `AutoAnswerTimeUseCase.record(LearnerId, String speed)`; refuses anything but
  quick/normal/relaxed.

### Web (design system ScreenSettings, updated to v41)

- Phone, Me → Settings → Speaking: a **Time to answer** row showing "Automatic · Relaxed" (or the chosen speed),
  opening a picker screen in the style of ScreenMoodPicker: four radio cards
  - **Automatic** — "Bolo learns how long you take to start answering and matches it. Right now: Relaxed."
  - **Quick** — "Bolo replies as soon as you stop. Help comes after 5 seconds of silence."
  - **Normal** — "A short wait after you stop. Help comes after 8 seconds."
  - **Relaxed** — "Plenty of time to think. Help comes after 30 seconds."
  Footer line: "Bolo always helps if you stay quiet, and pauses the talk if you stay quiet for long." Choice saves
  at once; a failed save puts the earlier choice back with "That didn't save. Try again."
- An **I stammer** switch row: "Bolo waits through repeats and pauses, and your feedback won't count them."
- Desktop (≥ 900 px, Me page): the four options inline as a radio group named "Time to answer", the switch below.
- The DS ScreenSettings README's old "More time to answer" text is replaced by this.

## 4. The talk with stammer: feedback

When the talk was held with `stammer` on (the Coach reads the learner's current setting when it builds the
feedback):
- The fluency level ignores long pauses (computed as if 0 long pauses); words per minute still count.
- The Coach prompt gets one line: the learner stammers; never pick a sentence or suggest a "better" version because
  of repeated sounds or words, blocks or pauses, and never mention them.
- Fillers ("um", "uh" …) are unchanged.

## 5. How was today? (DBF-8)

### Data

`talk_rating` (V28): `talk_id uuid primary key references talk_session(id) on delete cascade`,
`learner_id uuid not null references learner(id) on delete cascade`, `score smallint` null or 1–5 (null = skipped),
`note text` null, ≤ 300 chars, `created_at`, `updated_at timestamptz not null`. Owned by the coaching module.
On `LearnerMerged` the rows move to the account.

### When it asks

n = 1 + the learner's talks with a ready debrief that started before this talk. Ask when this talk's debrief is ready,
there is no `talk_rating` row for it, and n ∈ {2, 3, 4} or (n ≥ 7 and (n − 7) % 3 == 0). Never in History.

### API

- `GET /api/talks/{id}/rating` → `{ "ask": true|false }`. Not the learner's talk → 404.
- `PUT /api/talks/{id}/rating` body `{ "score": 1..5 | null, "note": string | null }` → 204. Upserts. `score` null =
  "Not now" before tapping a face (skipped). A note without a score → 422 `note`; note > 300 chars → 422 `note`;
  score outside 1–5 → 422 `score`. Notes are trimmed; empty → null. Not the learner's talk → 404.

### Web (ScreenRating)

- After a talk (not History), when the learner reaches the last feedback card and `ask` is true, the sheet opens once
  (phone: bottom sheet; desktop: centred dialog).
- Five faces in a radio group (Bad, Not great, Okay, Good, Great). A tap saves the score at once (PUT score), then
  shows "Want to tell us why?" (max 300) with Send (PUT score + note, then close) and Not now (close).
- "Not now" or close before a tap → PUT score null. Esc closes like Not now. A failed save closes quietly.

### Admin

`GET /api/admin/ratings` → `{ average30, share45, count30, latest: [{ score, note, at }] }` (scores of the last 30
days, null scores excluded; latest 20 rows with a note, newest first). New **Ratings** tab: the three numbers (share
against the 70 % goal) and the notes list.

## Docs

PRD: AX-7 (Changed 2026-11-14, founder), AX-8, DBF-8 and US-4.11 (typed note), PRG-7 (Changed 2026-11-13, founder:
first talk ends with the drills offer, then the reminder time; the save step is dropped). RUNBOOK: "Time to answer"
and "Ratings". Design system: ScreenSettings README + preview (v41).

## Left to the builder

File layout inside each module, test names, exact CSS, the PRD wording, the admin layout within the existing style.

## Builder check (2026-11-14) — settled

Calls made by Claude (flagged to the founder):
1. **Stammer and words per minute.** With stammer on, words per minute is computed over speech time minus the gaps
   between words longer than 1.2 s (the long-pause cutoff), so blocks don't pull fluency down; long pauses aren't counted.
2. **Stammer and sound cards.** With stammer on, a word is left out of sound detection when it is repeated straight
   after itself ("I I", "to to") or is a broken start of the next word ("w- want", "wa want": a 1–3 letter prefix of
   the next word). The other words are judged as usual.
3. **Stammer at feedback time** uses the learner's setting when the feedback is built (right after the talk).

Settled by the builder:
- Start delay: `QuietClock.quietMs()` getter; captured once per learner turn at its first speech frame; survives
  Bolo's encourage line and an UNCLEAR re-ask; reset when a turn is committed. When the silence pause fires before
  any speech, the quiet time counted up to the pause is kept and used as that turn's start delay (resume resets the
  clock, so otherwise slow starters would look quick).
- `answer_time_auto` is written by its own single-column upsert, never by the learner's settings save (no lost update).
- Config: `bolo.talk.speeds.{quick,normal,relaxed}.{encourage-ms,hint-ms,pause-ms,speculate-after-ms,end-after-ms,unfinished-end-after-ms}`
  and `bolo.talk.stammer.*` floors; tests that override the old flat keys move to `speeds.normal.*`.
- `TalkSettings` holds all speeds and the floors; `TalkConversation` picks per talk from `TalkView`.
- Talk number for the rating: a new `DebriefPort.readyCountBefore(learner, talk)` (ready debriefs of talks started
  before this one).
- A rating PUT with `score: null` never clears a score already saved.
- `CoachFlow` gets an explicit `askRating` prop; only the after-talk path sets it (not History, not FirstTalkScreen's
  first talk — talk 1 never asks anyway).
- Admin: `average30` null with no ratings; "latest" is the 20 newest notes of all time.
- `DesktopMe` preview in the design system loses the old "More time to answer" switch too.
