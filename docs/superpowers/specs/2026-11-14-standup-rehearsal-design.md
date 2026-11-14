# Standup Rehearsal — design

Founder, 2026-11-14: "continue, fix and plan the next" (away; this draft uses my defaults, and the questions only the
founder can answer are listed at the end). Build waits for the founder's OK on this and the design system.
PRD items: SCN-1, SCN-2, US-7.1, US-7.3, US-7.4, ASM-4 (scenario fix), HOM-1 (the "Change" link), events
`session_started.scenario`. Earlier decision (2026-11-13): Standup Rehearsal counts like any talk.
Already in the design system: ScreenScenarioPicker ("What do you want to practise?", Free talk / Standup rehearsal,
Friendly / Realistic chips, "Bolo plays your manager and asks for your update.").

## Why

Bolo's learners are working adults who freeze when they must speak English at work (onboarding offers "work
meetings" as a goal and a feared moment). Free talk builds comfort; a standup rehearsal is the first time Bolo lets them practise the real moment: give
an update, get asked "what's the ETA?", and keep going. Success: learners who try it come back to it (scenario chosen
on a later talk), and the debrief gives one tip that is about how they gave their update, not just their English.

## Goals

1. **Choose it.** On Home, a quiet "Change" link under the Talk button's line opens ScreenScenarioPicker (phone sheet;
   desktop dialog). Free talk is the default. Picking Standup reveals Friendly / Realistic (default Realistic as in the
   DS). One tap applies and closes. The line then reads "Standup rehearsal · Realistic · 5 min · Change" and Bolo
   remembers the choice on the account until it's changed.
2. **Rehearse.** Bolo plays the learner's manager on a quick daily standup: it asks for the update (yesterday, today,
   blockers) and follows up like a manager would (what's the ETA, who's waiting on it, what do you need). It stays warm,
   never corrects English mid-talk and never turns into a test. It uses what Bolo remembers about the learner's work;
   if it knows nothing, its first line asks in one short question what they work on, then goes into the standup.
3. **Interruptions (Realistic only).** Once the learner has been speaking for about 20 s in one turn, Bolo cuts in at
   their next short breath (about 0.4 s of quiet instead of the usual end-of-turn wait) with one short follow-up
   ("Sorry, quick one: what's the ETA on that?"). At most 2 a talk, at least 60 s apart. Friendly never interrupts.
4. **After an interruption** (US-7.4): when the learner answers, Bolo acknowledges in a few words and hands back
   ("Got it, carry on."). If they freeze, the hint card shows standup starters ("Good question. I expect it by…",
   "It depends on…") at the learner's usual hint time (Time to answer; 15 s at Normal — see Builder check 3).
5. **Speed.** Bolo's speaking speed is the learner's own setting in both difficulties ("Slower please" still works);
   Realistic only means Bolo doesn't slow down on its own (see Builder check 2).
6. **Debrief.** One of the 3 fixes is a standup tip (ASM-4): the Coach reads the learner's update and picks the one
   that helps most ("Lead with the outcome, then the detail", "Give a date, not 'soon'", "Say the blocker and what
   you need"), showing one of their own sentences rewritten. It replaces the lowest-ranked fix, so never more than 3.
   With no usable update (too short), no tip.
7. **Everywhere else** the talk is a normal talk: the same free-plan count and 5 minutes, memory notes, reminders,
   history (the row is labelled "Standup rehearsal"), drills.

## Non-goals

- No "client" role (P0 is the manager only); no job interview, IELTS or other scenarios.
- No work-context recording step (SCN-3 / US-7.2, P1). Bolo's notes cover it.
- No talking over the learner: Bolo only cuts in at a breath (true barge-in is project 6, TLK-15).
- No score for the standup, no structure checklist shown during the talk.

## Anti-goals

- Bolo never sounds harsh or impatient, even in Realistic; an interruption is curious, never a reprimand.
- Never more than 3 fixes; the standup tip never pushes the sound fix out (it replaces the lowest-ranked one).
- An interruption never fires in the first turn, in a talk's last 30 s, or right after a pause or a hint.
- Free talk behaves exactly as today for everyone who never opens "Change".

## How it works (my defaults, for the founder to check)

- **Stored:** scenario (`free` | `standup`) and difficulty (`friendly` | `realistic`) on the learner's talk settings,
  next to voice, mood and Time to answer; copied onto each talk when it starts (history, debrief, analytics).
- **Prompt:** a standup block replaces the free-talk focus line and opening; mood still adds its style line; the brief
  (Bolo's notes) is still included so the manager knows their project.
- **Interruption:** a turn-taking rule on the server — in Realistic, after 20 s of the learner's speech in the turn,
  the end-of-turn wait drops to 0.4 s; the reply is generated with an "interrupt with one short follow-up about what
  they just said" cue. Counted and spaced per talk.
- **Debrief tip:** the Coach's existing structured reply gains an optional `standupTip` (rule id, the learner's
  sentence, the better version); DebriefPlan puts it in place of the lowest-ranked fix.
- **Events:** `session_started` gets `scenario` and `difficulty`; new `scenario_changed` (from, to, difficulty) and
  `standup_interrupted` (turn index) for tuning.

## Design system changes (v44, to show before building)

- ScreenHome / ScreenHomeFree and DesktopHome: the talk line with "Change" (exists in ScreenScenarioPicker's backdrop;
  added to the live Home dashboard).
- ScreenScenarioPicker: unchanged; DesktopScenarioPicker (new): the same choice as a dialog.
- TalkChat / DesktopTalk: a small header tag "Standup · Bolo is your manager"; an interruption is an ordinary Bolo
  turn (no special styling).
- Debrief fix card "Standup tip" (phone and DesktopFeedback): label, the learner's sentence, the better version, and
  Try it.

## Questions only the founder can answer (most important first)

1. **Interruptions:** cut in at the learner's next breath after ~20 s (my default, works on speakers and phones today),
   or wait for true talk-over (needs barge-in, project 6)?
2. **Default difficulty:** Realistic (as the design system shows) or Friendly for a first standup?
3. **Who sees it:** everyone, or only learners whose goal is work meetings / job interviews (others keep Free talk with
   the link hidden)? My default: everyone.

## Builder check (2026-11-14) — settled by Claude (flagged to the founder)

1. **I stammer wins.** With I stammer on (AX-8: a turn never ends before 4.5 s of silence, no silence help before
   16 s), Bolo never cuts in, even in Realistic; the picker says so under Realistic ("With I stammer on, Bolo won't
   cut in."). Accessibility guarantees beat realism.
2. **Speed.** "Realistic speaks at normal speed" means no extra slowing by Bolo; the learner's saved speaking speed
   (`buddy_speed`) and "Slower please" (TLK-13) always apply. No new speed rule.
3. **Hint timing.** After an interruption the existing silence steps keep the learner's Time to answer timings; only
   the hint card's starters change to standup ones. The PRD's "8 s" becomes "at the usual hint time" (fixed with the
   build). A separate 8 s timer would override Quick/Relaxed, which the founder chose on 2026-11-14.
4. **Prompt.** The core rules become scenario-aware: in Standup, Bolo is a warm manager, and "never ask what they did
   today" and "a relaxed chat with a friend" don't apply. Mood keeps adding its style line (funny stays kind, never
   about their English).
5. **Lowest-ranked fix** = the last card in today's fixed order (sound, upgrades, habit). With fewer than 3 fixes the
   tip is added, not swapped.
6. **History** keeps the Coach's topic as the row title and adds a "Standup" tag.
7. **Success metric** uses `scenario_changed` (learners who pick Standup again after switching away) plus Standup talk
   count per learner, since the choice is remembered.

## Left to the builder

Table and column names, the exact prompt wording and tip ids, the 20 s / 0.4 s / 60 s values as config, test names,
where the picker state lives on the web.
