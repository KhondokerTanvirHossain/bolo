# P1a-6 Home, Settings and the Funnel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish Phase 1a so that a stranger can go from first open to talk to feedback with no help, and we can measure it. The four pieces:
- **Home:** one clear next step (US-6.9).
- **Settings:** Bolo's speed and captions (SET-1/2, US-9.5). "Slower please" asks to make the slower voice the default (US-2.7).
- **The onboarding funnel events:** including time to first word (§10, R4).
- **A stranger-test checklist** for the founder.

**Architecture:**
- **Settings:** stored per learner in `learner` (V8) and returned by `/api/me`. The talk reads the speed when it starts.
- **Home:** a new `home` module decides the card from `coaching`'s latest debrief, reached through a new inbound port, using `debrief.viewed_at` (V9).
- **Analytics:** a new `analytics` module stores allow-listed events with no text and no audio (V10).
  - The browser sends the funnel events; the talk publishes session events.
  - `scripts/metrics.sh` prints the funnel and the median time to first word from the box.

**Tech Stack:** as P1a-2…P1a-5: Spring Boot 4 / Java 21 / JDBC / Flyway; Next.js 15, Vitest + RTL, Playwright.

**Spec:**
- `Bolo-PRD-MVP.md`: HOM-1 and US-6.9; SET-1, SET-2 and US-9.5 (Speaking group); US-2.7 ("asks if I want it as my default"); §10 analytics events (`app_first_open`, `mic_permission_result`, `onboarding_step_completed`, `first_word_spoken`, `session_started`, `session_ended`, `debrief_viewed`); R4 and Gate 1's "first word ≤ 60 s median".
- `Bolo-Architecture-MVP.md`: §10 Phase 1a exit ("a stranger completes onboarding → talk → debrief on the deployed site with no help, and the time-to-first-word event fires").

## Global Constraints
- **R1 / HOM-1:** exactly one filled (primary) button and at most one secondary card on Home. Anything else is a quiet text link.
- **Home priority** (US-6.9, adapted to what exists):
  - **Primary:** always **Talk**. Resuming a chat after a reconnect isn't built: P1a-4 ruling.
  - **Card:** (1) "Finish your feedback" when the latest talk's debrief is `ready` and not yet viewed; else (2) "Practise *the v sound*" when that debrief's first sound fix names a pattern; else none.
  - **Quiet links:** "Practise sounds" and "Play my Day 1 clip" (when one exists).
- **Settings, Speaking group only:**
  - Bolo's speed `normal | slower` (default normal);
  - captions `on | off` (default on).
  - A change applies from the next talk, never mid-talk.
  - Other US-9.5 rows (more time, I stammer, ambient sound, scene, theme, data saver) are 1b/P1 and not shown.
- **Events carry no audio and no transcript text (§10):**
  - names are allow-listed;
  - prop values are numbers, booleans, or strings of at most 40 characters from `[a-z0-9_ -]`;
  - at most 8 props;
  - at most 60 events per learner per minute (429);
  - unknown names → 422.
- **Time to first word:** seconds from `app_first_open` (stored in the browser at first launch) to the first spoken answer. That is the Day 1 clip saved, or the first talk turn if Day 1 was skipped. It fires **once per browser**.
- Bolo's voice and persona don't change (founder discussion pending), apart from the slower rate the learner chooses.

## Review Focus
1. Home must offer "Finish your feedback" only for a `ready`, unviewed debrief. A debrief that is scoring, failed or too_short, or another learner's talk, never shows. Test: Task 2.
2. Events can't smuggle text: a 41-character string, a string with a capital or a full stop, a nested object, an array or a 9th prop → 422. Test: Task 3.
3. `first_word_spoken` fires once per browser, even after "Talk again" or a redone Day 1. Test: Task 4.
4. With slower already the default, "Slower please" doesn't ask "keep it?" again. Changing speed in Settings during a talk changes only the next talk. Test: Task 1.
5. A signed-out visitor gets 401 from `PUT /api/me/settings`, `POST /api/events` and `GET /api/home`. Marking another learner's debrief as viewed → 404. Tests: Tasks 1–3.

---

### Task 1: Learner settings, and the talk honouring them (api)

**Files:**
- Create:
  - `api/src/main/resources/db/migration/V8__learner_settings.sql`;
  - `learner/application/port/in/UpdateSettingsUseCase.java`;
  - `learner/adapter/in/web/SettingsController.java`;
  - a JDBC adapter method on the existing `ProfilePort` adapter, or a new `SettingsPort` (follow `ProfilePort`'s pattern).
- Modify:
  - `LearnerView` (adds `SettingsView settings`, never null: defaults when there's no row);
  - `/api/me` JSON;
  - `talk/application/port/in/TalkView` (+ `boolean slowVoice`), filled where a TalkView is built from the learner;
  - `TalkConversation` (start with `slow = talk.slowVoice()`; on a SLOWER intent when not already slow, send `{"type":"slower"}`).
- Test:
  - `api/src/test/java/app/bolo/learner/SettingsAcceptanceTest.java`;
  - `api/src/test/java/app/bolo/talk/SlowerDefaultTest.java` (relay acceptance with fakes, like the PilotTalk tests).

**Interfaces:**
- Produces:
  - `record LearnerView.SettingsView(String buddySpeed /* "normal" | "slower" */, boolean captions)`;
  - `void UpdateSettingsUseCase.update(LearnerId, String buddySpeed, boolean captions)`, which throws `IllegalArgumentException("buddy-speed")` on a bad speed;
  - `PUT /api/me/settings {buddySpeed, captions}` → 204. It answers 400 `{"error":"buddy-speed"}` for a bad speed and 401 when signed out, and needs the `X-Bolo` header like the other writes;
  - the S→C message `{"type":"slower"}`.
- `V8`: `create table learner_settings (learner_id uuid primary key references learner(id) on delete cascade, buddy_speed text not null default 'normal' check (buddy_speed in ('normal','slower')), captions boolean not null default true, updated_at timestamptz not null)`.

- [ ] **Step 1: Write the failing tests**
  - **SettingsAcceptanceTest:**
    - `defaultsBeforeAnyChoice` (`/api/me` → settings normal and true);
    - `savedSettingsComeBackOnMe`;
    - `aBadSpeedIs400`;
    - `signedOutIs401`.
  - **SlowerDefaultTest:**
    - `aSlowerDefaultSpeaksTheFirstLineSlowly` (FakeTextToSpeech's first voice is BUDDY_SLOW);
    - `slowerPleaseAsksToKeepIt` (a "slower" message is sent once);
    - `slowerPleaseWhenAlreadySlowerDoesNotAskAgain`;
    - `changingSpeedMidTalkWaitsForTheNextTalk` (PUT normal during a slower talk → the next line is still BUDDY_SLOW).
- [ ] **Step 2:** Run `cd api && ./gradlew test --tests '*SettingsAcceptanceTest*' --tests '*SlowerDefaultTest*'`. Expected: FAIL (compile).
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same command plus `--tests '*LayerRulesTest*' --tests '*PilotTalk*'`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(api): learner settings — Bolo's speed and captions; a slower default and "keep it slower?"`.

### Task 2: Debrief viewed, and the Home decision (api)

**Files:**
- Create:
  - `V9__debrief_viewed.sql` (`alter table debrief add column viewed_at timestamptz`);
  - `coaching/application/port/in/LatestDebriefUseCase.java`;
  - a `viewed` method on `DebriefUseCase`, or a new port in;
  - `home/` module: `home/domain/HomePlan.java` (pure), `home/application/service/HomeService.java`, `home/adapter/in/web/HomeController.java`.
- Modify: `DebriefController` (`POST /api/talk/{id}/debrief/viewed`) and `JdbcDebriefAdapter`.
- Test: `api/src/test/java/app/bolo/home/domain/HomePlanTest.java` and `api/src/test/java/app/bolo/home/HomeAcceptanceTest.java`.

**Interfaces:**
- Produces:
  - `record LatestDebriefUseCase.Latest(TalkSessionId talkId, String status, boolean viewed, String firstSoundPattern /* SoundPattern name or null */)` and `Optional<Latest> latestFor(LearnerId)` (the learner's most recent talk that has a debrief row);
  - `POST /api/talk/{id}/debrief/viewed` → 204 for the owner; 404 otherwise or when not ready;
  - `record HomePlan.Card(String kind /* "feedback" | "practice" */, String talkId, String pattern, String title)` and `static Optional<Card> HomePlan.card(Optional<Latest> latest)` (pure, applying the Global Constraints' order; the title comes from `SoundPattern.title()`);
  - `GET /api/home` → `{"primary":"talk","card":{…}|null}`.

- [ ] **Step 1: Write the failing tests**
  - **HomePlanTest:**
    - `aReadyUnviewedDebriefAsksToFinishIt`;
    - `aViewedDebriefWithASoundFixOffersThatSound` (card practice, pattern "V_BH", title "The v sound");
    - `scoringFailedAndTooShortOfferNothingFromFeedback` (each → empty, or practice only when ready);
    - `noTalkNoCard`.
  - **HomeAcceptanceTest:**
    - `afterATalkHomeAsksToFinishTheFeedback`;
    - `viewingItTurnsTheCardIntoPractice`;
    - `anotherLearnerCannotMarkItViewed` (404);
    - `signedOutIs401`.
- [ ] **Step 2:** Run `./gradlew test --tests '*HomePlanTest*' --tests '*HomeAcceptanceTest*'`. Expected: FAIL.
- [ ] **Step 3:** Implement. `home` reaches coaching only through `LatestDebriefUseCase`; ArchUnit enforces it.
- [ ] **Step 4:** Run the same plus `--tests '*LayerRulesTest*' --tests '*CoachAcceptanceTest*'`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(api): Home's one next step (US-6.9) and debrief viewed`.

### Task 3: Analytics events and the funnel readout (api + script)

**Files:**
- Create:
  - `V10__event.sql` (`create table event (id bigserial primary key, learner_id uuid not null references learner(id) on delete cascade, name text not null, props jsonb not null default '{}', at timestamptz not null)` plus an index on `(name, at)`);
  - `analytics/` module: `domain/EventRules.java` (pure allow-list and prop validation), `application/port/in/RecordEventUseCase.java`, `application/service/AnalyticsService.java` (rate limit 60/min per learner), `adapter/in/web/EventController.java`, `adapter/out/persistence/JdbcEventAdapter.java`, and a listener for the talk's start and end;
  - `scripts/metrics.sh`.
- Modify: `talk` publishes a `TalkStarted` Spring event, beside the existing `TalkEnded` (or analytics listens to both if one already exists).
- Test: `analytics/domain/EventRulesTest.java` and `analytics/EventAcceptanceTest.java`.

**Interfaces:**
- Client names: `app_first_open`, `mic_permission_result`, `onboarding_step_completed`, `first_word_spoken`, `debrief_viewed`.
- Server names: `session_started`, `session_ended` (props `turns`, `end_reason`, `speaking_s`).
- `POST /api/events {name, props}` → 202. It answers 422 `{"error":"event"}` for an unknown name or bad props, 429 `slow-down`, and 401 when signed out.
- `static Optional<String> EventRules.problem(String name, Map<String,Object> props)` returns empty when the event is acceptable.
- `scripts/metrics.sh` (uses `scripts/box.sh` and psql in bolo-db) prints the last 30 days:
  - learners with `app_first_open`;
  - mic granted;
  - onboarding completed;
  - `first_word_spoken` count and **median and p90 seconds**;
  - sessions started and ended;
  - debriefs viewed.

- [ ] **Step 1: Write the failing tests**
  - **EventRulesTest:**
    - `allowListedNamesOnly`;
    - `textCannotBeSmuggledIn` (41 characters; "Hello."; a capital; a nested map; a list; a 9th prop → each a problem);
    - `numbersBooleansAndShortKeysPass`.
  - **EventAcceptanceTest:**
    - `aFunnelEventIsStored`;
    - `anUnknownEventIs422`;
    - `the61stEventInAMinuteIs429`;
    - `signedOutIs401`;
    - `aTalkRecordsSessionStartedAndEnded` (talk via the relay fakes, then both rows exist with `turns` and `end_reason`).
- [ ] **Step 2:** Run `./gradlew test --tests '*EventRulesTest*' --tests '*EventAcceptanceTest*'`. Expected: FAIL.
- [ ] **Step 3:** Implement, and write `scripts/metrics.sh` (SQL with `percentile_cont` over `(props->>'seconds_since_first_open')::numeric`).
- [ ] **Step 4:** Run the same plus `--tests '*LayerRulesTest*'`. Expected: PASS. Run `bash -n scripts/metrics.sh`. Expected: no output.
- [ ] **Step 5:** Commit `feat(api): allow-listed analytics events, session events and scripts/metrics.sh`.

### Task 4: Web: Home, Settings, "keep it slower?" and the funnel events

**Files:**
- Create:
  - `web/src/home/HomeScreen.tsx` (replaces `FirstTalkScreen`'s idle view; FirstTalkScreen keeps the talk and practice routing or is renamed, whichever is the smaller diff);
  - `web/src/home/home.ts` (`fetchHome`);
  - `web/src/settings/SettingsCard.tsx` and `web/src/settings/settings.ts`;
  - `web/src/analytics/track.ts`;
  - tests `web/src/analytics/track.test.ts`, `web/src/home/HomeScreen.test.tsx` and `web/e2e/home.spec.ts`.
- Modify:
  - `Onboarding.tsx`/screens: fire `app_first_open` once (store `bolo.firstOpen` = ms); `mic_permission_result`; `onboarding_step_completed` (step, duration_s); `first_word_spoken` when the Day 1 clip is saved.
  - `useTalk`/`talkState`: handle `slower` by showing "Keep Bolo slower next time?" (Yes / Not now; Yes → PUT settings); fire `first_word_spoken` on the first learner turn end if it hasn't fired yet.
  - `TalkScreen`: captions default from `/api/me` settings (the localStorage toggle stays as the in-talk override).
  - `CoachFlow`: POST `viewed` when a ready debrief renders, and fire `debrief_viewed` with fixes_count.
  - `PracticeScreen`: accept an initial pattern.

**Interfaces:**
- `track(name: string, props?: Record<string, number | boolean | string>): void` (fire-and-forget; never throws; drops the call when offline).
- `firstWordSpoken(): void` fires once per browser (`bolo.firstWordSent` flag), with `seconds_since_first_open` rounded to an integer.

- [ ] **Step 1: Write the failing tests**
  - **track.test.ts:**
    - `firstWordFiresOncePerBrowser` (called twice → one POST);
    - `secondsAreCountedFromFirstOpen`;
    - `trackNeverThrowsWhenTheNetworkFails`.
  - **HomeScreen.test.tsx:**
    - `oneFilledButtonAndAtMostOneCard` (count `.bo-btn--primary` = 1);
    - `aFeedbackCardOpensTheDebrief`;
    - `aPracticeCardOpensThatSound`.
  - **e2e home.spec.ts (stubbed API):**
    - Home shows Talk and the "Finish your feedback" card;
    - the gear → Settings → choose Slower → PUT sent;
    - a talk receiving `{type:"slower"}` shows "Keep Bolo slower next time?" → Yes → PUT `buddySpeed: "slower"`;
    - at 390 px the Home and Settings cards fit (no horizontal scroll).
- [ ] **Step 2:** Run `cd web && pnpm test && pnpm e2e --project=firefox home.spec.ts`. Expected: FAIL.
- [ ] **Step 3:** Implement. Use only existing design-system classes (`bo-btn`, `bo-card`, `dk-card`, `bo-scenebtn`, `bo-chip`, if present) and keep the 16 px phone gutter.
- [ ] **Step 4:** Run `pnpm test && pnpm lint && pnpm e2e --project=firefox`. Expected: all pass, including the existing onboarding, talk, coach and practice specs.
- [ ] **Step 5:** Commit `feat(web): Home's one next step, Settings, "keep it slower?" and the onboarding funnel events`.

### Task 5: Ship and the stranger test

**Files:** Modify `docs/RUNBOOK.md` (sections "Home and settings", "Funnel and time to first word", and "Stranger test (Phase 1a exit)").

- [ ] **Step 1:** Run `scripts/deploy.sh`. Expected: smoke 11/11.
- [ ] **Step 2:** Run the live probes (talk, coach, practice). Expected: PASS.
- [ ] **Step 3:** Walk the founder's own path on the live site in a fresh private window (start → Day 1 → talk → feedback → Home), then run `scripts/metrics.sh`. Expected: one `first_word_spoken` with a seconds value, and session_started and session_ended rows.
- [ ] **Step 4:** Write the RUNBOOK stranger-test checklist:
  - **Setup:** a fresh private window on the person's own laptop; the founder watches and doesn't help.
  - **What to note:** where they hesitate or ask, and whether they reach the debrief.
  - **Afterwards:** read `scripts/metrics.sh`. The targets are first word ≤ 60 s and the first conversation turn ≤ 3 min.
- [ ] **Step 5:** Commit `docs: home, settings, funnel metrics and the stranger test`.
