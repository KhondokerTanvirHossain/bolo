# P1c-3 History and Progress Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Progress tab and History, as the PRD and the design specify (PRG-1, PRG-3, PRG-8, US-6.2, US-6.3, US-6.7; ScreenProgress, ScreenProgressNew, ScreenHistory, ScreenHistorySession, SessionRow):
- **Effort:** one line ("N talks · M minutes of speaking").
- **Skills:** 4 skill trends in words.
- **My patterns:** tagged Improving, Steady or New, with a Fixed section.
- **The latest 3 talks,** with **See all**, which opens History.
- **History:** grouped by week; each talk opens its feedback with **Practise these (3 min)** and **Delete recording**.

The Day 7/14/21 voice comparison (PRG-2) is the next plan.

**Architecture:**
- **coaching:**
  - The debrief body gains `topic` (named by the Coach, 2–5 words, optional, "Free talk" when missing) and `patterns` (every sound pattern Bolo found, with its count, plus the habit).
  - A new port in, `DebriefHistoryUseCase`, lists a learner's debriefs with their talk's start and length.
  - `POST /api/talk/{id}/recording/delete` deletes a talk's audio, and the body's clip and model links become null.
- **progress (new module):** a pure `ProgressPlan` turns that list into effort, trends, patterns and history rows. `GET /api/progress` and `GET /api/history`.
- **web:** a Progress tab in the app shell, a History screen, and a history mode of the Coach's debrief view.

**Spec:** `Bolo-PRD-MVP.md` PRG-1, PRG-3, PRG-8, US-6.2, US-6.3, US-6.7, US-8.6 (delete a recording); `design/design-system/4-screen-recipes.md` "Progress tab" and "History"; components ScreenProgress, ScreenProgressNew, ScreenHistory, ScreenHistorySession, SessionRow.

## Global Constraints
- **Never a score or a percentage on Progress.** Skills are in words: Building, Good, Strong.
- **Trends:** the last 30 days, one small line per skill, in a 2×2 grid.
  - With fewer than 3 finished talks (ready debriefs), the area is "Your trends appear after 3 sessions" with "n of 3".
  - Chart rules (dataviz): one colour (the Coach's violet); no legend, since each title names its skill; 2 px line; 8 px markers; a hover tooltip (date and level); a visually hidden table of the same values.
- **Pattern tags,** computed over finished talks oldest → newest, from each talk's `patterns` (or, for older debriefs, its fixes):
  - **Fixed:** last seen ≥ 5 talks ago (and seen at least once before). Listed under "Fixed".
  - **New:** first seen in one of the last 2 talks.
  - **Improving:** appears in fewer of the last 3 talks than in the 3 before them, or its count fell between its last two appearances.
  - **Steady:** otherwise.
  - Up to 5 active patterns, the most recent and most frequent first.
  - Names: a sound uses its title ("The v sound"); a filler is `Says "um" often`; short answers are "Short answers".
  - The first time a pattern is seen as Fixed, the web shows a one-off "Fixed: you stopped …" note, remembered in this browser.
- **History:** every talk that has a debrief, newest first, grouped "This week", "Last week", then by month.
  - Each row: a big day number over a short month, the topic, "Free talk · 5 min", and up to 3 fix tags (the fix cards' titles).
  - A talk that was too short or failed shows "Short talk" or "No feedback" instead of tags.
  - History is never limited by plan.
- **History session:** the debrief as the Coach showed it.
  - The last card's one primary is **Practise these (3 min)** (practice on the first sound fix, else the sounds list), with a secondary **Back**.
  - A quiet **Delete recording** opens a sheet: "Delete this recording?" with **Keep it** (primary) and **Delete recording**. The audio goes at once and the feedback stays.
  - With keep-recordings off: "Recording not kept, as you chose. Feedback and words are still here."
  - The whole-talk player is left out: raw talk audio is deleted once feedback is ready (privacy and disk ruling).
- **Sidebar:** Talk, Progress, Me.

## Review Focus
1. Another learner's debriefs never appear in my History or Progress, and deleting another learner's recording is a 404. Test: Task 2 and Task 3.
2. A pattern that disappears and comes back is never both Fixed and active. Test: Task 2.
3. Old debriefs without `patterns` or `topic` still list correctly. Test: Task 2.
4. Deleting a recording twice, or deleting one whose audio was already swept, is fine (204) and the feedback text stays. Test: Task 3.
5. At 390 px, Progress and History fit with no horizontal scroll, and the 2×2 grid becomes 1 column. Test: Task 4.

---

### Task 1: Topic and patterns in the debrief (api)
- **Modify:**
  - `CoachPrompt` (schema: an optional `topic` string, at most 40 characters; system line: "Name the talk in 2–5 plain words, the way the learner would, e.g. 'The payment bug demo'");
  - `CoachService.build` (`body.topic` validated, otherwise "Free talk"; `body.patterns` = every `scored.sounds()` `{pattern, count}`, plus the habit `{habit: kind, word, count}`);
  - `FakeLlm.STRUCTURED_REPLY` (+ topic).
- **Tests (CoachAcceptanceTest):**
  - `theDebriefNamesTheTalkAndListsItsPatterns`;
  - `aMissingOrOverlongTopicFallsBackToFreeTalk`.
- **Commit:** `feat(api): the Coach names the talk; the debrief keeps every pattern it found`.

### Task 2: Progress and History (api)
- **Create:**
  - `coaching/application/port/in/DebriefHistoryUseCase.java` (`List<PastDebrief> list(LearnerId)`, where `PastDebrief(talkId, startedAt, minutes, status, body)`);
  - the `progress` module: `domain/ProgressPlan.java` (pure), `application/service/ProgressService.java`, `adapter/in/web/ProgressController.java` (`GET /api/progress`, `GET /api/history`).
- **Tests:**
  - **ProgressPlanTest:**
    - `effortCountsTalksAndMinutes`;
    - `trendsWaitForThreeTalks`;
    - `trendsKeepTheLast30Days`;
    - `aPatternFirstSeenInTheLastTwoIsNew`;
    - `fewerRecentAppearancesIsImproving`;
    - `fiveTalksWithoutItIsFixed`;
    - `aFixedPatternThatReturnsIsActiveAgain`;
    - `oldDebriefsUseTheirFixes`;
    - `atMostFiveActive`;
    - `historyGroupsByWeekNewestFirst`.
  - **ProgressAcceptanceTest:**
    - `myProgressShowsMyTalksOnly`;
    - `historyListsMyTalksNewestFirst`;
    - `signedOutIs401`.
- **Commit:** `feat(api): progress — effort, skill trends, my patterns, history`.

### Task 3: Delete a recording (api)
- **Create:** `POST /api/talk/{id}/recording/delete` in `DebriefController`, through `DebriefUseCase.deleteRecording(learner, talk)`.
- **What it does:**
  - deletes `talk/<id>/`;
  - sets the body's `strength.clip`, each fix's `clip` and `model` to null, and `recordingDeleted: true`, through `updateReadyBody`;
  - marks the talk's audio as purged.
- **Tests (CoachAcceptanceTest):**
  - `deletingARecordingRemovesItsAudioAndKeepsTheFeedback`;
  - `deletingTwiceIsFine`;
  - `anotherLearnerGets404`.
- **Commit:** `feat(api): delete one recording`.

### Task 4: Web — Progress, History, history session
- **Create:**
  - `web/src/progress/ProgressScreen.tsx`, `SkillTrends.tsx` (SVG sparklines per the chart rules), `PatternList.tsx`, `HistoryScreen.tsx`, `SessionRow.tsx`, `progress.ts`;
  - tests (Vitest and e2e `progress.spec.ts`).
- **Modify:**
  - `AppShell` (the Progress tab);
  - the container (views `progress`, `history`, `session`);
  - `CoachFlow` (a `history` mode: the last card's Practise these (3 min) / Back, quiet Delete recording with its sheet, and the "recording deleted" and "not kept" lines).
- **Tests:**
  - **Vitest:**
    - `trendsShowWordsNotNumbers`;
    - `fewerThanThreeShowsTheCount`;
    - `historyRowReadsAsOneSentence` (its aria-label);
    - `fixedNoteShowsOnce`.
  - **e2e:**
    - the Progress tab shows effort, trends and patterns;
    - See all opens History;
    - a row opens the session;
    - Delete recording → the sheet → the clip disappears;
    - Practise these opens practice;
    - 390 px with no horizontal scroll.
- **Commit:** `feat(web): Progress and History`.

### Task 5: Ship
- Full suites, then push for CI, deploy, smoke and probes.
- RUNBOOK section.
- Commit `docs: progress and history`.
