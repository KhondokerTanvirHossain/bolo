# P1a-1 Setup Questions and Day 1 Clip Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After the mic step, a learner answers Bolo's 3 setup questions (stored on the server), records a 30-second Day 1 clip (kept only on their device), and lands on a "first talk" screen where they can play the clip back.

**Architecture:** The `learner` module gains a profile (V2 migration, `PUT /api/profile`). The web onboarding state machine gains `setup` (3 questions) and `day1` steps, a `MediaRecorder`-based recorder, and an IndexedDB clip store. No vendor is needed.

**Tech Stack:** as P0-1 (Spring Boot 4.1.2, Next.js 15, Vitest, Playwright).

**Spec:** [Bolo-PRD-MVP.md](../../../Bolo-PRD-MVP.md) US-1.4, US-1.6, US-1.7, ONB-3, ONB-4, R3; locked desktop prototype screens `setup` and `day1`.

## Global Constraints

- Everything in P0-1's Global Constraints still holds.
- The Day 1 clip never leaves the device in this plan (ONB-4: a guest's clip is uploaded only after sign-in, and only if they chose to keep recordings; sign-in is plan 1c).
- No text input fields (R3): answers are chips or keys 1–4.
- Stable answer keys: goal `work_meetings | job_interviews | study_abroad | daily_confidence`; feared moment `speaking_up | interviews | phone_calls | small_talk`; talk minutes `5 | 10`.

## Review Focus

1. A reload in the middle of recording → the partial clip is discarded and the Day 1 step restarts (US-1.7).
2. A browser that can't record webm (Safari) → falls back to mp4 via `MediaRecorder.isTypeSupported`, and playback still works.
3. IndexedDB unavailable (private window) → recording still completes and plays back from memory; nothing throws.
4. Back on question 2 → question 1's answer is still selected (US-1.4).
5. `PUT /api/profile` with an unknown key → 422, never a 500.

---

### Task 1: Learner profile API

**Files:**
- Create: `api/src/main/resources/db/migration/V2__learner_profile.sql`
- Create: `learner/domain/{Goal,FearedMoment,Profile}.java`, `learner/application/port/in/UpdateProfileUseCase.java`, `learner/application/port/out/ProfilePort.java`, `learner/application/service/ProfileService.java`, `learner/adapter/in/web/ProfileController.java`; extend `JdbcLearnerAdapter` and `LearnerView` (adds `profile: null | {goal, fearedMoment, talkMinutes}`)
- Test: `learner/application/service/ProfileServiceTest.java`, extend `LearnerAcceptanceTest`

**Interfaces:**
- `PUT /api/profile` body `{goal, fearedMoment, talkMinutes}` → 204; upsert; 422 codes `goal`, `feared-moment`, `talk-minutes`; 401 without a session.
- Table `learner_profile(learner_id uuid pk fk, goal text not null, feared_moment text not null, talk_minutes int not null check (talk_minutes in (5, 10)), updated_at timestamptz not null)` plus check constraints on both enum columns.

- [ ] Step 1: Unit tests: an unknown goal → `RefusedException("goal")`; talk minutes 7 → `"talk-minutes"`; a valid profile → `ProfilePort.upsert` with `BoloClock.now()`.
- [ ] Step 2: Acceptance tests: PUT then GET `/api/me` shows the profile; PUT twice overwrites (one row); bad goal → 422 `{"code":"goal"}`; a raw insert with goal `'nonsense'` violates the database check.
- [ ] Step 3: Implement. Step 4: `./gradlew build` green. Step 5: Commit `feat(learner): setup answers as the learner profile`.

### Task 2: Setup questions screen

**Files:**
- Create: `web/src/onboarding/questions.ts` (the 3 questions: English, Bangla and chips, from the prototype script's `Q` array, each chip mapped to its stable key), `web/src/onboarding/screens/SetupScreen.tsx`
- Modify: `web/src/onboarding/steps.ts` (state `answers: {goal?, fearedMoment?, talkMinutes?}`, `questionIndex`; actions `answer`, `nextQuestion`, `prevQuestion`; the `setup` step precedes the new `day1` step), `web/src/onboarding/Onboarding.tsx`
- Test: extend `steps.test.ts`; extend `e2e/onboarding.spec.ts`

**Interfaces:**
- `nextQuestion` does nothing until the current question is answered; after question 3 it sends `PUT /api/profile`, and on `ok` it moves to `day1`.
- Keys 1–4 pick a chip; Enter means Next. A quiet "Back" link sits on questions 2 and 3. Step labels "3 of 5", "4 of 5" and "5 of 5" (prototype).

- [ ] Step 1: Vitest: Next is blocked without an answer; answer → next → prev keeps the answer; the third answer moves to `day1` only through the `profileSaved` action.
- [ ] Step 2: Playwright: pick "Job interviews" with key `2`, Next, "Phone calls", Back shows "Interviews"… (re-pick), "10 minutes", Next → the PUT body is `{goal:"job_interviews", fearedMoment:"phone_calls", talkMinutes:10}` → the Day 1 card shows.
- [ ] Step 3: Implement. Step 4: web checks green. Step 5: Commit `feat(web): setup questions`.

### Task 3: Day 1 clip recorder and first-talk screen

**Files:**
- Create: `web/src/audio/recorder.ts` (`pickMimeType(isSupported): string`, `startRecording(stream, mime): Recording` with `stop(): Promise<Blob>`), `web/src/audio/clipStore.ts` (`saveClip(key, blob)`, `loadClip(key)`, IndexedDB `bolo` / store `clips`, all in try/catch, in-memory fallback), `web/src/onboarding/day1.ts` (pure: `afterRecording(durationS, attempts, promptedShort) → "short-prompt" | "saved"`), `web/src/onboarding/screens/Day1Screen.tsx`, `web/src/onboarding/screens/FirstTalkScreen.tsx`
- Test: `web/src/audio/recorder.test.ts`, `web/src/onboarding/day1.test.ts`, extend `e2e/onboarding.spec.ts`

**Interfaces:**
- `pickMimeType` prefers `audio/webm;codecs=opus`, then `audio/mp4`, then `""` (browser default).
- `afterRecording`: under 10 s and not yet prompted → `"short-prompt"` (copy "Want to say a bit more?" with "Record again" primary and "Keep it" secondary, shown once); otherwise `"saved"`. At most 3 re-records (US-1.6); after the third the "Record again" button is gone.
- Recording stops at 30 s or on Space or the Stop button; the ring arc follows the prototype's `bo-rec` markup (`stroke-dasharray = 502.7 × left / 30`).
- "Saved. We'll play this back to you on Day 7." shows for 1.8 s, then the `talk` step: a FirstTalkScreen with the scene, the caption "Your first talk with Bolo starts here once Bolo's voice is connected.", and a "Play my Day 1 clip" button.
- On reload while recording: `day1` restarts and nothing partial is stored (the clip is written only after `stop()`).

- [ ] Step 1: Vitest: `pickMimeType` order; `afterRecording(6, 1, false)` → `short-prompt`; `(6, 1, true)` → `saved`; `(25, 1, false)` → `saved`.
- [ ] Step 2: Playwright (Firefox fake stream): finish the questions → Record → stop at about 3 s → the short prompt → Keep it → the Saved toast → the first-talk screen → Play reaches `currentTime > 0`.
- [ ] Step 3: Implement. Step 4: web checks green. Step 5: Commit `feat(web): Day 1 clip and first-talk screen`.

### Task 4: Deploy and verify

- [ ] Step 1: `scripts/deploy.sh` → smoke passes; the other tenants are unchanged.
- [ ] Step 2: Production run of the Playwright onboarding spec against `BOLO_URL` (Firefox), as far as the first-talk screen.
- [ ] Step 3: Commit any fix; ledger.
