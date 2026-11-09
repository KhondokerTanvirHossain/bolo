# P1g Your Voice, Then and Now (Day 7/14/21) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal (PRG-2, US-6.1):**
- On Day 7, 14 and 21, and every 30 days after, the learner records their 30-second intro again.
- Progress shows "Your voice, then and now": the Day 1 clip and the latest intro side by side, with a one-line summary.
- Between unlocks it shows the Day 1 clip and "Day 7 unlocks in N days".
- Home's Next up offers the re-record when it's due (US-6.9 priority 2).

**Architecture:**
- **learner module:**
  - Day 1 is the learner's `created_at` date in Asia/Dhaka: onboarding records the Day 1 clip right after the guest is made, and a guest merged into an account keeps the account's date.
  - A pure `IntroSchedule` gives the unlock days, what's due, and the next unlock.
  - New table `voice_intro(learner_id, day, recorded_at, on_server)` remembers each re-record's date.
  - Audio goes to the server only for learners who chose to keep recordings, as `learner/<id>/intro-<day>.<ext>`, the same as Day 1. Otherwise the clip stays in this browser (IndexedDB `intro-<day>`).
- **Endpoints:**
  - `GET /api/me/voice`;
  - `POST /api/me/intro/{day}` (multipart; the `audio` part is optional);
  - `GET /api/me/intro/{day}`.
- **web:**
  - `Day1Screen`'s recorder becomes `IntroRecorder`, used by Day 1 and the re-record.
  - A `VoiceCompare` section at the top of Progress.
  - Home's "Record your intro again" card.

**Spec:**
- `Bolo-PRD-MVP.md` PRG-2, US-6.1, US-6.9 (priority: Finish your feedback → Re-record your intro → practice).
- Design: `design/design-system/components/ScreenProgress` (item 2) and `ScreenProgressNew` ("Voice").
- Prototype `progress` ("Your voice, then and now": Day 1 | Today + summary).

## Global Constraints
- **Unlock days:** 7, 14, 21, then every 30 days after: 51, 81, … Day N is `day1 + (N - 1)` days.
- **Due:** the latest unlocked day that has no recording, and only for 7 days after it unlocks. After that it waits for the next unlock, so nothing nags.
- **Keep choice:**
  - Audio is uploaded only when the learner's latest consent keeps recordings.
  - Otherwise only the date is stored, and the clip stays in this browser.
  - The server never stores audio for a learner who chose not to keep recordings: if audio arrives anyway, it's 422 `not-kept`.
- **The summary line** (US-6.1). No new scoring, from the learner's own data:
  - if a skill's level rose between the first and the latest finished talk: "Your fluency went from Building to Good.", using the first skill that rose, in the order pronunciation, fluency, sentences, vocabulary;
  - otherwise effort: "N talks and M minutes of speaking since Day 1." Never "you didn't improve".
- **No Day 1 clip on this device or server** (a new browser, not kept): the first re-record becomes the starting point, with "Your Day 1 clip isn't here, so this recording is your new starting point."
- **Copy:**
  - Section title: "Your voice, then and now".
  - Left: "Your first intro / Day 1". Right: "Today / Day 7".
  - Between unlocks: "Day 7 unlocks in 3 days"; on unlock day: "Day 7 is here".
  - Home card: "Record your intro again" with "Day 7: hear how far you've come".
- **Recording rules:** the same as Day 1 (30 s max, a gentle "a few more seconds" under 10 s, up to 3 re-records), Space to start and stop, and Esc goes back.
- **Delete everything** removes the intro audio (`learner/<id>/` prefix, already) and the `voice_intro` rows (cascade).

## Review Focus
1. Another learner's intro audio is never served (404). Test: Task 1 `anotherLearnerGets404`.
2. An intro for a day that hasn't unlocked is refused (422 `not-yet`). Test: Task 1 `aDayNotYetUnlockedIsRefused`.
3. Day boundaries in Dhaka: learner created 2026-11-03T20:00Z (= 4 November in Dhaka) → Day 7 is 10 November. Test: Task 1 `IntroScheduleTest.dayOneIsTheDhakaDate`.
4. Recording the same day twice replaces the clip, never duplicates it. Test: Task 1 `recordingAgainReplaces`.
5. The not-kept choice: audio is refused with 422 and nothing is stored. Test: Task 1 `notKeptRefusesAudio`.

---

### Task 1: Intro schedule and endpoints (api)
- **Create:**
  - `V16__voice_intro.sql`;
  - `learner/domain/IntroSchedule.java`:
    - `record Status(LocalDate day1, List<Integer> unlocked, Integer due, Integer nextDay, LocalDate nextOn)`;
    - `static Status of(LocalDate day1, Set<Integer> recorded, LocalDate today)`.
  - `learner/application/port/in/VoiceIntroUseCase.java` and its service;
  - `learner/adapter/in/web/VoiceIntroController.java`.
- **Tests:**
  - **IntroScheduleTest:**
    - `beforeDay7NothingIsDue`;
    - `day7IsDueOnItsDay`;
    - `dueLastsSevenDays`;
    - `afterDay21EveryThirtyDays`;
    - `aRecordedDayIsNotDue`;
    - `dayOneIsTheDhakaDate`.
  - **VoiceIntroAcceptanceTest:**
    - `voiceShowsTheScheduleFromSignUp`;
    - `aKeptIntroIsStoredAndServedToItsOwner`;
    - `notKeptRefusesAudio`;
    - `notKeptRecordsTheDateOnly`;
    - `aDayNotYetUnlockedIsRefused`;
    - `recordingAgainReplaces`;
    - `anotherLearnerGets404`;
    - `deleteEverythingRemovesIntros`.
- **Commit:** `feat(api): Day 7/14/21 intro re-records`.

### Task 2: Home offers the re-record (api)
- **Modify** `HomePlan` (priority: feedback → intro due → practice) and `HomeService`, which reads the voice status through `VoiceIntroUseCase`. The card is `{kind: "intro", day}`.
- **Tests:**
  - `HomePlanTest.anUnseenFeedbackComesBeforeTheIntro`;
  - `anIntroDueComesBeforePractice`;
  - `HomeAcceptanceTest.homeOffersTheDueIntro`.
- **Commit:** `feat(api): Home offers the intro re-record when it's due`.

### Task 3: Web: record, compare, Home card
- **Create:**
  - `web/src/voice/IntroRecorder.tsx`: Day1Screen's recorder, parameterised by clip key and copy;
  - `RecordIntro.tsx` (view `intro`, day N; uploads when kept, else marks the date and keeps the clip locally);
  - `VoiceCompare.tsx`;
  - `voice.ts`: `fetchVoice`, `recordIntro`, `summaryLine(progress)`.
- **Modify:**
  - `Day1Screen` uses `IntroRecorder`;
  - `ProgressScreen` puts VoiceCompare first;
  - `HomeDashboard` adds the intro card;
  - the container adds the `intro` view.
- **Tests:**
  - **Vitest:**
    - `summaryNamesTheSkillThatRose`;
    - `summaryFallsBackToEffort`;
    - `betweenUnlocksShowsDaysLeft`.
  - **e2e:**
    - Home's "Record your intro again" → record → Progress shows Day 1 and Day 7 side by side with the summary;
    - not kept: no upload, the clip plays from this browser;
    - no Day 1 clip: the new-starting-point line;
    - 390 px with no sideways scroll.
- **Commit:** `feat(web): your voice, then and now`.

### Task 4: Ship
- Full suites, then `scripts/ship.sh`.
- RUNBOOK "Your voice, then and now".
- Commit `docs: voice compare`.
