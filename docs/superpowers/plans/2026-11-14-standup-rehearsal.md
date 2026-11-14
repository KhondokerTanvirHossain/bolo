# Standup Rehearsal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Status: waiting for the founder.** Written 2026-11-14 while the founder was away ("continue, fix and plan the
> next"). Build only after the founder approves the spec and design system v44. The 3 open founder questions, and the
> tasks each answer would change, are in "If the founder answers differently" at the end.

**Goal:** A learner can switch Home's Talk from Free talk to a Standup rehearsal (Friendly or Realistic), where Bolo plays their manager, Realistic cuts in at a breath, and the debrief adds one standup tip.

**Architecture:** The choice is two learner settings (next to voice, mood and Time to answer), copied onto each talk at start. The talk module gets a standup prompt, a `CUT_IN` turn decision with a pure `CutIns` rule, and standup starters. The Coach's structured reply gains an optional `standupTip`; `DebriefPlan` puts it in place of the last fix. The web adds the picker (sheet on phone, dialog ≥ 900 px), the Home line, the talk label, the tip card and a history tag.

**Tech Stack:** Spring Boot 4 / Java 21 hexagonal modules (JdbcClient, Flyway V32, Jackson 3), Next.js 15 / React 19, Vitest, Playwright Firefox.

**Spec:** `docs/superpowers/specs/2026-11-14-standup-rehearsal-design.md` (with its "Builder check — settled" section). Design system v44: ScreenScenarioPicker, DesktopScenarioPicker, ScreenStandupTalk, StandupTip.

## Global Constraints

- Scenarios: `free` (default) | `standup`. Difficulty: `friendly` | `realistic` (default `realistic`).
- Cut-in (Realistic only): after **20 000 ms** of the learner's speech in one turn, end the turn at **400 ms** of silence; at most **2** per talk, at least **60 000 ms** apart, never in the first learner turn, never with under **30 000 ms** left, never with I stammer on. All as config `bolo.talk.standup.*`.
- Never more than 3 fixes; the tip replaces the last card in the fixed order (sound, upgrades, habit) or is added when there are fewer than 3.
- Bolo's speaking speed and Time to answer are the learner's own settings in both difficulties.
- Copy: picker title "What do you want to practise?"; "Free talk" / "Chat about your day, your work, anything."; "Standup rehearsal" / "Bolo plays your manager and asks for your update."; footnote "Realistic interrupts once or twice, like a real meeting." (with I stammer on: "With I stammer on, Bolo won't cut in."); Home line "Standup rehearsal · Realistic · 5 min · Change"; talk pill "Standup · Realistic"; card eyebrow "Standup tip".
- Tip titles (fixed list, id → title): `outcome_first` → "Lead with the outcome"; `give_a_date` → "Give a date, not \"soon\""; `blocker_and_ask` → "Say the blocker and what you need".
- Coach words never used: mistake, wrong, error, bad, fail, incorrect, poor, accent (existing rule).
- Stage `api/src` only (never `git add api`); leave design/design-system/SYNC.md, docs/product/feedback-log.md, .impeccable/, api/bin/ alone.

## Review Focus

1. A setting changed while a talk is open must not change that talk (it was copied at start) — test in Task 1.
2. An old web client's settings PUT without `scenario`/`difficulty` keeps the saved values (null keeps) — test in Task 1.
3. A cut-in turn that is "Sorry?" / "Slower please" or trips the crisis screen goes through those paths unchanged (no cut-in cue) — test in Task 3.
4. A Coach `standupTip` whose `said` isn't in that learner turn, or whose rule id is unknown, is dropped (no card, no slot) — test in Task 4.
5. A Free talk debrief never has a standup card, even if the model returns one — test in Task 4.

---

### Task 1: The scenario setting, copied onto each talk

**Files:**
- Create: `api/src/main/resources/db/migration/V32__scenario.sql`
- Modify: `api/src/main/java/app/bolo/learner/domain/Settings.java`, `learner/application/port/in/UpdateSettingsUseCase.java`, `learner/application/service/SettingsService.java`, `learner/adapter/out/persistence/JdbcSettingsAdapter.java`, `learner/adapter/in/web/SettingsController.java`
- Modify: `api/src/main/java/app/bolo/talk/application/port/in/TalkView.java`, `talk/application/service/TalkSessionService.java` (`toView`, `start`), `talk/adapter/out/persistence/JdbcTalkSessionAdapter.java` (insert), `talk/application/port/in/TalkStarted.java`, `analytics/application/service/AnalyticsService.java`, `analytics/domain/EventRules.java`
- Create: `api/src/main/java/app/bolo/common/event/ScenarioChanged.java`
- Test: `api/src/test/java/app/bolo/talk/ScenarioSettingAcceptanceTest.java`

**Interfaces:**
- Produces: `Settings(..., boolean stammer, String scenario, String difficulty)`; `Settings.DEFAULTS` ends `"free", "realistic"`; `Settings.validScenario(String)`, `Settings.validDifficulty(String)`.
- Produces: `UpdateSettingsUseCase.update(..., Boolean stammer, String scenario, String difficulty)` (null keeps; refuses `"scenario"` / `"difficulty"`).
- Produces: `TalkView(..., boolean stammer, String scenario, String difficulty)` (old constructors pass `"free", "realistic"`).
- Produces: `record ScenarioChanged(LearnerId learner, String from, String to, String difficulty)`; analytics event `scenario_changed`; `session_started` props `{scenario, difficulty}`.
- Migration: `learner_settings.scenario text not null default 'free' check (scenario in ('free','standup'))`, `learner_settings.difficulty text not null default 'realistic' check (difficulty in ('friendly','realistic'))`; `talk_session.scenario text not null default 'free'` (same check), `talk_session.difficulty text`.

- [ ] **Step 1: Write the failing tests** (`ScenarioSettingAcceptanceTest`, consented account via `TalkTestClient`):
  - `theDefaultIsFreeTalk`: GET `/api/me/settings` → `$.scenario == "free"`, `$.difficulty == "realistic"`.
  - `standupIsSavedAndCopiedOntoTheNextTalk`: PUT `{..., "scenario":"standup","difficulty":"friendly"}` → 204; start a talk → `select scenario, difficulty from talk_session where id = ?` = `standup`, `friendly`.
  - `aChangeDuringATalkDoesNotChangeIt` (Review Focus 1): start (free), then PUT standup → the open talk row is still `free`.
  - `anOldClientWithoutTheFieldsKeepsThem` (Review Focus 2): PUT standup, then PUT a body without `scenario`/`difficulty` → GET still `standup`.
  - `anUnknownScenarioIsRefused`: PUT `"scenario":"interview"` → 422 body contains `scenario`.
  - `changingScenarioIsAnEvent`: free → standup logs one `scenario_changed` row with props `from=free,to=standup`; saving the same value again logs none.
  - `theStartResponseNamesTheScenario`: POST start for a standup learner → JSON `$.scenario == "standup"`, `$.difficulty == "realistic"` (the web's talk label reads these).
  - `sessionStartedCarriesTheScenario`: attach a standup talk → `session_started` props `scenario=standup`.
- [ ] **Step 2: Run** `cd api && ./gradlew test --tests 'app.bolo.talk.ScenarioSettingAcceptanceTest'` — Expected: FAIL (fields missing / 0 rows).
- [ ] **Step 3: Implement** V32 and the interfaces above, following how V28 added `answer_time` and `stammer` end to end. `SettingsService` publishes `ScenarioChanged` only when the saved scenario differs. `TalkSessionService.start` writes the learner's scenario/difficulty into the new row (difficulty null for `free`).
- [ ] **Step 4: Run** the test class, then `cd api && ./gradlew test` — Expected: PASS, whole suite green (existing settings tests updated only for the new constructor arity).
- [ ] **Step 5: Commit** `feat(api): scenario and difficulty settings, copied onto each talk; scenario_changed event`.

### Task 2: Bolo the manager (prompt and starters)

**Files:**
- Modify: `api/src/main/java/app/bolo/talk/domain/BuddyPrompt.java`, `talk/application/service/TalkConversation.java` (constructor's `system`), `talk/application/service/Starters.java`
- Test: `api/src/test/java/app/bolo/talk/domain/BuddyPromptTest.java`, `api/src/test/java/app/bolo/talk/StandupTalkTest.java`

**Interfaces:**
- Consumes: `TalkView.scenario()`, `TalkView.difficulty()` (Task 1).
- Produces: `BuddyPrompt.system(goal, fearedMoment, minutes, brief, opening, mood, String scenario)`; existing overloads pass `"free"`. `BuddyPrompt.cutInSystem(String system)` (used in Task 3; its test is in Task 3). `Starters.forLine(String buddyLine, boolean standup)`.

- [ ] **Step 1: Write the failing tests**
  - `BuddyPromptTest.standupMakesBoloAWarmManager`: the standup system contains "manager", "yesterday", "today", "blockers", "Got it, carry on", and does **not** contain "Never ask what they ate, what they did today" nor "loves a relaxed chat".
  - `BuddyPromptTest.standupKeepsTheSafetyAndNoCorrectionRules`: contains "Never correct their pronunciation" and "If they bring up something harmful".
  - `BuddyPromptTest.standupWithNoNotesAsksWhatTheyWorkOn`: with `brief == null`, the opening line asks what they work on before the update; with a brief, it doesn't.
  - `BuddyPromptTest.freeTalkIsUnchanged`: `system(..., "free")` equals today's `system(...)` output.
  - `StandupTalkTest.aStandupTalkOpensWithTheManagerPrompt` (FakeLlm, like `VoiceAndMoodTalkTest.theMoodReachesBolo`): the first LLM request's system prompt contains "manager".
  - `StandupTalkTest.standupStartersAreAboutAnUpdate`: the starters request's system text contains "status update".
- [ ] **Step 2: Run** `./gradlew test --tests 'app.bolo.talk.domain.BuddyPromptTest' --tests 'app.bolo.talk.StandupTalkTest'` — Expected: FAIL.
- [ ] **Step 3: Implement.** Split today's core rules into shared rules (reply length, one question, never correct, simple words, harmful topics, Banglish, never invent a life, follow their lead, talk length) and the free-talk-only rules (the friend persona, no routine questions, stories and what-ifs). Standup block: "You are the learner's friendly manager at a quick daily standup on a call. Ask for their update: what they finished yesterday, what's on today, and any blockers. Follow up like a manager: when it will be done, who is waiting on it, what they need. Stay warm and curious, never impatient. After they answer a follow-up, say a few words like \"Got it, carry on.\" and let them continue their update." Mood line and brief are appended as today. Starters for standup: same call, system adds "These are for a status update to a manager."
- [ ] **Step 4: Run** both classes, then the whole API suite — Expected: PASS.
- [ ] **Step 5: Commit** `feat(talk): Standup rehearsal prompt — Bolo as a warm manager; standup starters`.

### Task 3: Realistic cuts in at a breath

**Files:**
- Modify: `api/src/main/java/app/bolo/talk/domain/TurnTaker.java`, `talk/domain/TurnPolicy.java`
- Create: `api/src/main/java/app/bolo/talk/domain/CutIns.java`
- Modify: `talk/application/service/TalkSettings.java` (config `bolo.talk.standup.cut-in-after-ms=20000`, `cut-in-silence-ms=400`, `cut-in-max=2`, `cut-in-gap-ms=60000`, `cut-in-not-last-ms=30000`), `talk/application/service/TalkConversation.java`, `api/src/main/resources/application.yml`, analytics (`standup_interrupted`)
- Test: `api/src/test/java/app/bolo/talk/domain/TurnTakerTest.java`, `api/src/test/java/app/bolo/talk/domain/CutInsTest.java`, `api/src/test/java/app/bolo/talk/StandupTalkTest.java`

**Interfaces:**
- Consumes: `BuddyPrompt.cutInSystem(String)` (Task 2), `TalkView.difficulty()`, `TalkView.stammer()` (Task 1).
- Produces: `TurnTaker.Decision.CUT_IN`; `TurnTaker.armCutIn(long afterSpeechMs, long silenceMs)` / `disarmCutIn()`.
- Produces: `CutIns(int max, long gapMs, long notLastMs)`, `boolean allowed(int learnerTurn, long nowMs, long leftMs)`, `void used(long nowMs)`; `CutIns.forTalk(String scenario, String difficulty, boolean stammer, …)` returns a rule that never allows for free, friendly or stammer.

- [ ] **Step 1: Write the failing tests**
  - `TurnTakerTest.armedCutInEndsALongTurnAtAShortBreath`: armed (20 000, 400); 20 s speech with a transcript, then 400 ms silence → `CUT_IN`; at 399 ms → not `CUT_IN`.
  - `TurnTakerTest.cutInWaitsFor20sOfSpeech`: 19.9 s speech + 400 ms silence → `NONE` (the normal end-of-turn still applies after `endAfterMs`).
  - `TurnTakerTest.unarmedNeverCutsIn`: same frames unarmed → no `CUT_IN`.
  - `CutInsTest`: not in learner turn 1; at most 2; second not within 60 s of the first; none with < 30 s left; `forTalk("free"|…)`, `forTalk(…, "friendly", …)` and `forTalk(…, stammer=true)` never allow.
  - `StandupTalkTest.realisticCutsInWithAFollowUp` (fake recogniser frames like the turn tests): turn 2 speaks 21 s then pauses 0.5 s → the turn ends, the reply's system prompt contains the cut-in instruction, one `standup_interrupted` event.
  - `StandupTalkTest.friendlyNeverCutsIn`: same frames, friendly → turn ends only at the normal end-of-turn wait.
  - `StandupTalkTest.aCutInSorryIsAnsweredAsSorry` (Review Focus 3): the cut turn's transcript is "Sorry?" → Bolo repeats its last line (TLK-13 path), no cut-in cue, the cut-in still counts as used.
- [ ] **Step 2: Run** `./gradlew test --tests 'app.bolo.talk.domain.TurnTakerTest' --tests 'app.bolo.talk.domain.CutInsTest' --tests 'app.bolo.talk.StandupTalkTest'` — Expected: FAIL.
- [ ] **Step 3: Implement.** `TurnTaker` returns `CUT_IN` (and marks committed) when armed, transcript not blank, `speechMs >= afterSpeechMs` and `silence >= silenceMs`; checked before the speculate branch. `TalkConversation` arms the taker at the start of each learner turn when `cutIns.allowed(learnerTurns + 1, now, leftMs())`, treats `CUT_IN` like `COMMIT` (crisis screen and intents first), and for a normal reply uses `BuddyPrompt.cutInSystem(system)` = system + "- You are cutting in on their update now: ask ONE short follow-up about what they just said, starting with \"Sorry, quick one:\". Max 15 words." Then `cutIns.used(now)` and the event.
- [ ] **Step 4: Run** those classes, then the whole API suite — Expected: PASS.
- [ ] **Step 5: Commit** `feat(talk): Realistic standup cuts in at the learner's next breath (at most 2, never with I stammer)`.

### Task 4: The standup tip in the debrief

**Files:**
- Modify: `api/src/main/java/app/bolo/coaching/domain/CoachPrompt.java`, `coaching/domain/CoachReply.java`, `coaching/application/service/CoachService.java` (validation, `standupFix`), `assessment/domain/DebriefPlan.java`, `assessment/domain/FixChoice.java`, `assessment/application/service/AssessmentService.java`, `assessment/application/port/in/AssessTalkUseCase.java`
- Modify: history row (`progress` module) to carry `scenario`
- Test: `api/src/test/java/app/bolo/assessment/domain/DebriefPlanTest.java`, `api/src/test/java/app/bolo/coaching/StandupTipAcceptanceTest.java`

**Interfaces:**
- Produces: `CoachPrompt.system(boolean stammer, boolean standup)`; schema adds optional `standupTip {rule: enum[outcome_first, give_a_date, blocker_and_ask], turn, said, better, why}` only when `standup`.
- Produces: `CoachReply.StandupTip(String rule, int turn, String said, String better, String why)`; `CoachReply.standupTip()` (nullable).
- Produces: `DebriefPlan.choose(sounds, upgrades, habit, boolean standupTip)`; `FixChoice.StandupTip`; `AssessTalkUseCase.plan(TalkAssessment a, int upgrades, boolean standupTip)`; fix JSON `kind: "standup"` with `title`, `said`, `better`, `changed`, `why`, `model` clip.
- Produces: history row `scenario` (`free` | `standup`); fix tag "Standup tip".

- [ ] **Step 1: Write the failing tests**
  - `DebriefPlanTest.aStandupTipReplacesTheLastOfThree`: 1 sound + 2 upgrades + tip → [Sound, Upgrade(0), StandupTip].
  - `DebriefPlanTest.aStandupTipIsAddedWhenThereIsRoom`: 1 sound + 0 upgrades, no habit + tip → [Sound, StandupTip].
  - `DebriefPlanTest.theSoundFixIsNeverPushedOut`: 1 sound + tip and nothing else → [Sound, StandupTip].
  - `StandupTipAcceptanceTest.aStandupDebriefShowsOneTip` (FakeLlm reply with a valid tip): `/debrief` has exactly one `kind == "standup"` fix, title "Give a date, not \"soon\"", ≤ 3 fixes in total.
  - `StandupTipAcceptanceTest.aTipNotFromTheTranscriptIsDropped` (Review Focus 4): `said` not in that turn, or `rule` unknown → no standup fix, no empty slot.
  - `StandupTipAcceptanceTest.freeTalkNeverHasATip` (Review Focus 5): a free talk whose model reply includes a tip → no standup fix; the Coach request for a free talk has no `standupTip` in its schema.
  - `StandupTipAcceptanceTest.historyTagsAStandup`: `/api/progress/history` row has `scenario == "standup"` and fix tag "Standup tip".
- [ ] **Step 2: Run** `./gradlew test --tests 'app.bolo.assessment.domain.DebriefPlanTest' --tests 'app.bolo.coaching.StandupTipAcceptanceTest'` — Expected: FAIL.
- [ ] **Step 3: Implement.** The tip prompt line (standup only): "standupTip: ONE way their update could be clearer for a manager, from this list: outcome_first (say what's done first, then the detail), give_a_date (an ETA with a day or time instead of 'soon'), blocker_and_ask (name the blocker and ask for what they need). 'said' copied exactly from one learner turn, 'better' their sentence rewritten that way (at most 25 words), 'why' one warm sentence, at most 15 words." Validate with `ReplyCheck.quoteIn` and `ReplyCheck.upgradeOk`; model clip as for upgrades.
- [ ] **Step 4: Run** those classes, then the whole API suite — Expected: PASS.
- [ ] **Step 5: Commit** `feat(coach): one standup tip in a Standup rehearsal's debrief (replaces the last fix)`.

### Task 5: Web — picker, Home line, talk label, tip card, history tag

**Files:**
- Create: `web/src/scenario/ScenarioPicker.tsx`, `web/src/scenario/scenario.ts`, `web/src/scenario/scenario.test.ts`
- Modify: `web/src/settings/settings.ts` (Settings gains `scenario`, `difficulty`), `web/src/home/HomeDashboard.tsx` (line + Change), `web/src/talk/TimeLeft.tsx`, `web/src/talk/TalkScreen.tsx`, `web/src/coach/CoachFlow.tsx` + `web/src/coach/debrief.ts` (kind `"standup"`), `web/src/progress/SessionRow.tsx`, `web/src/styles/bolo.css` (`.bo-scenario` sheet → centred 480 px dialog at ≥ 900 px, as `.bo-rate`)
- Test: `web/e2e/standup.spec.ts`, `web/src/home/HomeDashboard.test.tsx`

**Interfaces:**
- Consumes: settings API fields (Task 1), talk view `scenario`/`difficulty` in the talk start response (Task 1), fix `kind: "standup"` (Task 4), history `scenario` (Task 4).
- Produces: `scenarioLine(s: {scenario, difficulty}, minutes: number): string` ("Free talk · 5 min", "Standup rehearsal · Realistic · 5 min"); `talkLabel(s): string` ("Free talk", "Standup · Realistic").

- [ ] **Step 1: Write the failing tests**
  - `scenario.test.ts`: `scenarioLine` and `talkLabel` for free, standup/realistic, standup/friendly.
  - `HomeDashboard.test.tsx`: the line shows "Standup rehearsal · Realistic · 5 min" and a "Change" button.
  - `standup.spec.ts` (Firefox, 1280 and a 390 px phone page): Change opens "What do you want to practise?"; choosing Standup shows Friendly / Realistic with Realistic pressed; Friendly applies and closes, the line reads "Standup rehearsal · Friendly · 5 min", and survives a reload; Esc closes the desktop dialog without changing; with I stammer on, the footnote reads "With I stammer on, Bolo won't cut in."; a mocked debrief with a `standup` fix shows eyebrow "Standup tip", You said, Try and Try it; a history row of a standup shows the "Standup" tag.
- [ ] **Step 2: Run** `cd web && pnpm vitest run src/scenario src/home && npx playwright test e2e/standup.spec.ts --project=firefox` — Expected: FAIL.
- [ ] **Step 3: Implement** per DS v44 (ScreenScenarioPicker, DesktopScenarioPicker, ScreenStandupTalk, StandupTip). One tap applies: tapping Free talk saves and closes; tapping Standup reveals the chips with the saved (or `realistic`) difficulty pressed, and tapping a chip saves and closes. Keyboard on desktop: ↑↓ cards, ←→ chips, Enter applies, Esc closes. The standup card reuses the `upgrade` layout with the tip row (`why`) and Try it.
- [ ] **Step 4: Run** the tests, then `pnpm vitest run`, `pnpm tsc --noEmit`, `pnpm lint`, `npx playwright test --project=firefox` — Expected: all green.
- [ ] **Step 5: Commit** `feat(web): Standup rehearsal picker, Home line, talk label, Standup tip card and history tag (DS v44)`.

### Task 6: Docs, smoke and ship

**Files:**
- Modify: `Bolo-PRD-MVP.md` (US-7.3 speed line → "Bolo's speaking speed is the learner's own setting"; US-7.4 "for 8 s" → "at my usual hint time"; SCN-2 "client or manager" → "manager (P0)"; analytics table: `scenario_changed`, `standup_interrupted`, `session_started.difficulty`), `docs/RUNBOOK.md` (how to read `standup_interrupted`; the `bolo.talk.standup.*` values), `scripts/smoke.sh` (settings GET has `scenario`)

- [ ] **Step 1:** Edit the docs and the smoke check; run `bash -n scripts/smoke.sh` — Expected: no output.
- [ ] **Step 2:** Whole suites: `cd api && ./gradlew test`; `cd web && pnpm vitest run && pnpm tsc --noEmit && pnpm lint && npx playwright test --project=firefox` — Expected: all green.
- [ ] **Step 3: Commit** `docs: PRD SCN-2, US-7.3/7.4 and RUNBOOK for Standup rehearsal`; final review per executing-plans; then `git push origin p0-foundations && scripts/ship.sh` — Expected: CI ok, deploy ok, smoke ok, 4 probes exit 0.

## If the founder answers differently

| Founder question | Default in this plan | If the answer differs |
|---|---|---|
| 1. Cut in at a breath, or true talk-over? | At a breath (Task 3) | Talk-over: drop Task 3's `TurnTaker` rule; build after project 6 (TLK-15 barge-in), reusing `CutIns`. |
| 2. Default difficulty | Realistic | Friendly: change V32's default and `Settings.DEFAULTS` (Task 1), the pressed chip in Task 5. |
| 3. Who sees Change | Everyone | Only work goals: Task 5 hides Change unless `profile.goal` is `work_meetings` or `job_interviews`; Task 1 refuses `standup` for others with `"scenario"`. |
