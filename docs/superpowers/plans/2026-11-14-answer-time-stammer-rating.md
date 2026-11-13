# Time to answer, I stammer, How was today? — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Per-learner talk pace (Automatic / Quick / Normal / Relaxed, measured from start delays), an "I stammer" setting that lengthens waits and softens feedback, and a "How was today?" rating with an admin report.

**Architecture:** A `Pace` value (silence-help + turn-end timings) is chosen per talk from the learner's settings and carried on `TalkView`; `TalkConversation` builds its `QuietClock`/`TurnTaker` from it and saves each learner turn's start delay. A talk-module listener on `TalkEnded` recomputes the learner's automatic pace. Ratings live in the coaching module (`talk_rating`), reported by the admin module's read model.

**Tech Stack:** Spring Boot 4 / Java 21 hexagonal modules, JdbcClient, Flyway, Jackson 3; Next.js 15 / React 19, Vitest, Playwright.

**Spec:** `docs/superpowers/specs/2026-11-14-answer-time-stammer-rating-design.md` (read "Builder check — settled" too)

## Global Constraints

- Speeds (encourage / hint / pause / speculate / end / unfinished-end, ms): quick 5000/10000/30000/700/1500/3000; normal 8000/15000/45000/1000/2500/4000; relaxed 30000/45000/120000/1500/3500/5500.
- Stammer floors (each value `max(speed, floor)`): 16000/30000/90000/1500/4500/6500.
- Automatic: last 30 learner turns with `start_ms`, ≥ 5 needed; median `< 3000 → quick`, `≤ 6000 → normal`, else `relaxed`. Default `answerTimeAuto = normal`.
- Settings values: `answerTime ∈ {auto, quick, normal, relaxed}` (default auto); 422 code `answer-time`.
- Rating asks when n ∈ {2,3,4} or (n ≥ 7 and (n−7) % 3 == 0); score 1–5 or null (skipped); note ≤ 300 chars, only with a score; 422 codes `score`, `note`.
- Copy is fixed by design system v41/v42 `ScreenAnswerTime` and v40 `ScreenRating` READMEs (quoted in tasks).
- Modules meet only via `application.port.in` or `common` (`LayerRulesTest`). Gradle: `cd api && ./gradlew test --tests '…'`. Docker Desktop must be running for acceptance tests.
- Normal speed keeps the existing flat config keys (`bolo.talk.speculate-after-ms`, `end-after-ms`, `unfinished-end-after-ms`, `quiet-encourage-ms`, `quiet-hint-ms`, `quiet-pause-ms`) so existing tests keep working; quick/relaxed/stammer are new keys under `bolo.talk.speeds.quick.*`, `bolo.talk.speeds.relaxed.*`, `bolo.talk.stammer.*` (names: `encourage-ms`, `hint-ms`, `pause-ms`, `speculate-after-ms`, `end-after-ms`, `unfinished-end-after-ms`).
- Endpoint paths follow the existing singular `/api/talk/{id}/…` (spec's `/api/talks/` is a typo against DebriefController).

## Review Focus

1. A learner who chose Quick/Normal/Relaxed by hand must keep it after any number of talks (Automatic writes only `answer_time_auto`) — test in Task 4.
2. A learner silent until the silence pause, then resuming and speaking, must record the pre-pause quiet time, not ~0 — test in Task 3.
3. A settings save from the web carrying a stale `answerTimeAuto` must not overwrite Bolo's value — test in Task 2.
4. `PUT rating {score:null}` after a score was saved keeps the score — test in Task 6.
5. A guest who rated then signed in keeps the rating on the account (no cascade loss) — test in Task 6.

---

### Task 1: Pace and per-speed config

**Files:**
- Create: `api/src/main/java/app/bolo/talk/domain/Pace.java`
- Modify: `talk/domain/QuietClock.java` (add `long quietMs()`), `talk/application/service/TalkSettings.java`, `TalkRelayService.java` (read new keys), `api/src/main/resources/application.yml`
- Test: `api/src/test/java/app/bolo/talk/domain/PaceTest.java`, `QuietClockTest.java`

**Interfaces:**
- Produces: `record Pace(long encourageMs, long hintMs, long pauseMs, TurnPolicy policy)` with `Pace atLeast(Pace floor)` (per-value max; unfinished words from `this`). `TalkSettings.pace(String speed, boolean stammer): Pace` — speed `quick|normal|relaxed`; unknown/null → normal. `QuietClock(Pace)` convenience constructor; `long quietMs()`.

- [ ] Step 1: Tests. `PaceTest.atLeast_takes_the_larger_value_each` (quick.atLeast(stammerFloor) → 16000/30000/90000/1500/4500/6500; relaxed.atLeast(floor) → 30000/45000/120000/1500/4500/6500). `QuietClockTest.quietMs_counts_and_resets_on_speech` (onQuiet(3000) → quietMs 3000; onSpeech → 0). A `TalkSettingsTest.pace_picks_speed_and_applies_stammer` (package `app.bolo.talk.application.service`): `pace("relaxed", false).encourageMs()==30000`, `pace(null,false)` equals normal, `pace("quick", true).policy().endAfterMs()==4500`.
- [ ] Step 2: Run `./gradlew test --tests '*PaceTest' --tests '*QuietClockTest' --tests '*TalkSettingsTest'` → FAIL (no Pace).
- [ ] Step 3: Implement. `TalkSettings` gains components `Pace quick, Pace relaxed, Pace stammerFloor` (normal stays built from the existing fields); keep a constructor with the old argument list that fills them with the Global Constraints values (TalkConversationTest uses it). application.yml adds the quick/relaxed/stammer keys with the spec values.
- [ ] Step 4: Re-run → PASS; also `--tests '*TalkConversationTest'` still PASS.
- [ ] Step 5: Commit `feat(api): talk pace per speed, stammer floors (AX-7, AX-8)`.

### Task 2: Settings — answer time, auto pick, stammer

**Files:**
- Create: `api/src/main/resources/db/migration/V28__answer_time.sql`, `learner/application/port/in/AutoAnswerTimeUseCase.java`
- Modify: `learner/domain/Settings.java`, `LearnerView.SettingsView`, `UpdateSettingsUseCase`, `SettingsService`, `SettingsController` (body), `JdbcSettingsAdapter` (+ port out), `GuestService` (defaults)
- Test: `api/src/test/java/app/bolo/learner/SettingsAcceptanceTest.java`

**Interfaces:**
- Produces: `SettingsView(… mood, String answerTime, String answerTimeAuto, boolean stammer)`; `UpdateSettingsUseCase.update(learner, buddySpeed, captions, talkView, voiceGender, voiceAccent, mood, String answerTime, Boolean stammer)` (null keeps); `AutoAnswerTimeUseCase.record(LearnerId, String speed)` (refuses non quick/normal/relaxed with `RefusedException("answer-time")`); `Settings.validAnswerTime(String)`, `Settings.validAutoSpeed(String)`.
- V28: `learner_settings` add `answer_time text not null default 'auto'` check (auto, quick, normal, relaxed), `answer_time_auto text not null default 'normal'` check (quick, normal, relaxed), `stammer boolean not null default false`. `record` is a single-column upsert of `answer_time_auto` (insert defaults row if none); the learner's save never writes `answer_time_auto`.

- [ ] Step 1: Tests in `SettingsAcceptanceTest`: `new_learner_has_auto_answer_time_normal_and_no_stammer` (GET /api/me → answerTime "auto", answerTimeAuto "normal", stammer false); `saves_answer_time_and_stammer`; `bad_answer_time_is_422_answer_time` ("slow"); `missing_answer_time_keeps_current` (old-shape body); `client_cannot_set_answer_time_auto` (PUT answerTimeAuto "quick" → GET still "normal"); `auto_record_does_not_touch_the_learners_choice` (PUT answerTime quick; `AutoAnswerTimeUseCase.record(l,"relaxed")` → answerTime quick, answerTimeAuto relaxed).
- [ ] Step 2: Run `./gradlew test --tests '*SettingsAcceptanceTest'` → FAIL.
- [ ] Step 3: Implement as in Interfaces; update existing callers of `update(...)` (pass null, null).
- [ ] Step 4: Re-run → PASS; `./gradlew test --tests '*LayerRulesTest'` PASS.
- [ ] Step 5: Commit `feat(api): Time to answer and I stammer settings (AX-7, AX-8)`.

### Task 3: Talks run at the learner's pace and record start delays

**Files:**
- Create: `api/src/main/resources/db/migration/V29__turn_start.sql` (`alter table talk_turn add column start_ms int`)
- Modify: `TalkView` (add `String answerTime, boolean stammer`; keep old constructors defaulting `"normal", false`), `TalkSessionService.toView` (resolve `auto → answerTimeAuto`), `TalkConversation` (build QuietClock/TurnTaker from `settings.pace(talk.answerTime(), talk.stammer())`; capture start delay), `TurnPort.saveLearner(…, Integer startMs)`, `JdbcTurnAdapter`, `TalkRecord.Turn` if needed
- Test: `api/src/test/java/app/bolo/talk/AnswerTimeTalkTest.java`, `TalkConversationTest`

**Interfaces:**
- Consumes: Task 1 `Pace`, `TalkSettings.pace`, `QuietClock.quietMs()`; Task 2 `SettingsView.answerTime/answerTimeAuto/stammer`.
- Produces: `talk_turn.start_ms` filled for learner turns; `TurnPort.recentStartDelays(LearnerId, int limit): List<Integer>` (newest learner turns with start_ms, across talks).
- Start delay rule: a per-turn `Integer turnStartMs` set from `quiet.quietMs()` at the first speech frame of the turn if not yet set; when the quiet stage PAUSE fires before any speech, remember `quiet.quietMs()` as `pausedQuietMs` and on the first speech after resume use `pausedQuietMs + quiet.quietMs()`; Bolo's encourage line and an UNCLEAR re-ask keep the capture; reset to null after `saveLearner`.

- [ ] Step 1: Tests. `TalkConversationTest.relaxed_pace_does_not_encourage_at_eight_seconds` (talk view answerTime relaxed; 10 s of silent frames → no `thinking`/encourage cue; at 30 s → encourage). `TalkConversationTest.stammer_turn_waits_past_normal_end` (speech then 3 s silence → no commit; 4.5 s → commit). `TalkConversationTest.start_delay_saved_with_learner_turn` (2 s silence, speech, commit → TurnPort fake got startMs ≈ 2000 ±40). `TalkConversationTest.start_delay_survives_quiet_pause` (Review Focus 2: silence to pause, resume, 1 s silence, speech → startMs ≥ pause + 1000). `AnswerTimeTalkTest.learner_choice_quick_reaches_the_talk` (PUT answerTime quick → started TalkView answerTime "quick"; auto → answerTimeAuto).
- [ ] Step 2: Run `./gradlew test --tests '*TalkConversationTest' --tests '*AnswerTimeTalkTest'` → FAIL.
- [ ] Step 3: Implement per Interfaces.
- [ ] Step 4: Re-run → PASS; then the talk acceptance set `--tests 'app.bolo.talk.*'` PASS.
- [ ] Step 5: Commit `feat(api): each talk runs at the learner's pace; start delays saved (AX-7)`.

### Task 4: Automatic picks the pace after every talk

**Files:**
- Create: `talk/domain/AnswerSpeed.java`, `talk/application/service/AnswerTimeService.java`
- Test: `talk/domain/AnswerSpeedTest.java`, `api/src/test/java/app/bolo/talk/AutoAnswerTimeAcceptanceTest.java`

**Interfaces:**
- Consumes: `TurnPort.recentStartDelays`, `AutoAnswerTimeUseCase.record`.
- Produces: `AnswerSpeed.pick(List<Integer> startMs): Optional<String>` (empty when < 5); `AnswerTimeService.onTalkEnded(TalkEnded)` `@EventListener`, takes 30, records; any exception logs `warn` with the class name only.

- [ ] Step 1: Tests. `AnswerSpeedTest`: 4 values → empty; median 2999 → quick; 3000 → normal; 6000 → normal; 6001 → relaxed; even count uses the mean of the middle two. `AutoAnswerTimeAcceptanceTest.slow_starter_becomes_relaxed` (seed 6 learner turns with start_ms 12000 via talks, end talk → GET /api/me answerTimeAuto "relaxed"); `manual_choice_is_kept` (Review Focus 1: answerTime quick, same seed → answerTime still "quick", answerTimeAuto "relaxed").
- [ ] Step 2: Run → FAIL. Step 3: Implement. Step 4: Run → PASS.
- [ ] Step 5: Commit `feat(api): Automatic time to answer from the learner's start delays (AX-7)`.

### Task 5: Stammer-aware feedback

**Files:**
- Create: `assessment/domain/StammerFilter.java`
- Modify: `FluencyStats.of(List<TurnScore>, boolean stammer)` (keep 1-arg = false), `AssessTalkUseCase.assess(List<TurnInput>, boolean stammer)` (keep 1-arg), `AssessmentService`, `CoachPrompt.system(boolean stammer)` (keep no-arg), `CoachService` (reads `CurrentLearnerUseCase.view(learner).settings().stammer()`)
- Test: `assessment/domain/FluencyStatsTest`, `StammerFilterTest`, `coaching/domain/CoachPromptTest` (or existing coach test)

**Interfaces:**
- `FluencyStats` with stammer: wpm over speech time minus each inter-word gap > 1200 ms; `longPauses = 0`. `speechMs()` unchanged (the too-short check).
- `StammerFilter.drop(List<TurnScore>): List<TurnScore>` removes a word equal (case-insensitive, letters only) to the next word, or whose 1–3 letters are a prefix of the next word and shorter than it. Applied only to `PatternDetector.candidates` input.
- Stammer prompt line (exact): `This learner stammers. Never choose a sentence, or suggest a better version, because of repeated sounds or words, blocks or pauses, and never mention them.`

- [ ] Step 1: Tests. `FluencyStatsTest.stammer_ignores_long_gaps_in_words_per_minute` (words 0–300, 3300–3600 ms: no-stammer wpm uses 3600 ms, stammer uses 600 ms; stammer longPauses 0). `StammerFilterTest.drops_doubles_and_broken_starts` ("I I want to to go" → "I want to go"; "w want" → "want"; "wa want" → "want"; "a apple" keeps both? → "a" is a 1-letter prefix of "apple" → dropped: accept, assert documented). `CoachPromptTest.stammer_line_only_when_on`.
- [ ] Step 2: Run → FAIL. Step 3: Implement. Step 4: Run `--tests 'app.bolo.assessment.*' --tests 'app.bolo.coaching.*'` → PASS.
- [ ] Step 5: Commit `feat(api): feedback never counts repeats or blocks for learners who stammer (AX-8)`.

### Task 6: Rating API

**Files:**
- Create: `V30__talk_rating.sql`, `coaching/application/port/in/RatingUseCase.java`, `coaching/application/port/out/RatingPort.java`, `coaching/application/service/RatingService.java`, `coaching/adapter/out/persistence/JdbcRatingAdapter.java`, `coaching/adapter/in/web/RatingController.java`
- Modify: `DebriefPort` + `JdbcDebriefAdapter` (`int readyCountBefore(LearnerId, TalkSessionId)`; `boolean ready(TalkSessionId)` if absent)
- Test: `api/src/test/java/app/bolo/coaching/RatingAcceptanceTest.java`, `coaching/application/service/RatingRuleTest.java`

**Interfaces:**
- `RatingUseCase.ask(LearnerId, TalkSessionId): Optional<Boolean>` (empty = not the learner's talk → 404); `save(LearnerId, TalkSessionId, Integer score, String note): boolean` (false → 404). Static `RatingService.asksAt(int n)`.
- `GET /api/talk/{id}/rating` → `{"ask":bool}`; `PUT /api/talk/{id}/rating` → 204.
- V30 table as in spec §5; `RatingService` `@EventListener LearnerMerged` → `RatingPort.move(from, to)`.
- Upsert keeps an existing score when the new score is null (`coalesce(excluded.score, talk_rating.score)`); note replaced only when score given.

- [ ] Step 1: Tests. `RatingRuleTest.asks_at_2_3_4_then_every_third` (true for 2,3,4,7,10,13; false for 1,5,6,8,9,11). Acceptance: `second_ready_talk_asks`, `first_talk_never_asks`, `rated_talk_never_asks_again`, `skip_then_no_ask`, `null_score_keeps_saved_score` (RF4), `note_without_score_is_422_note`, `long_note_is_422_note` (301 chars), `score_6_is_422_score`, `other_learners_talk_is_404`, `guest_rating_moves_on_sign_in` (RF5; follow the merge setup in `SignInAcceptanceTest`).
- [ ] Step 2: Run → FAIL. Step 3: Implement. Step 4: Run → PASS; `LayerRulesTest` PASS.
- [ ] Step 5: Commit `feat(api): How was today? rating (DBF-8)`.

### Task 7: Admin ratings report

**Files:**
- Modify: `AdminReportsPort` (`record Ratings(Double average30, Double share45, int count30, List<Note> latest)`, `record Note(int score, String note, Instant at)`, `Ratings ratings(Instant since)`), `JdbcAdminReports`, `AdminService.ratings()`, `AdminController` `GET /api/admin/ratings`; web `src/admin/admin.ts`, `AdminApp.tsx` tab `ratings: "Ratings"`
- Test: `AdminAcceptanceTest.ratings_report`, web `src/admin/admin.test.ts` if a pattern exists (else e2e not needed)

- [ ] Step 1: Test: seed ratings 5, 4, 2, null (skipped), one 40 days old score 1 with note → average30 3.67 (±0.01), share45 0.67, count30 3, latest includes the old note first-by-date order newest first; no ratings → average30 null. Non-admin → 404/403 as the other admin endpoints do.
- [ ] Step 2: Run → FAIL. Step 3: Implement API + Ratings tab ("Average (30 days)", "Rated 4–5 · goal 70%", "Ratings (30 days)", list of score · date · note). Step 4: Run → PASS; `cd web && npx tsc --noEmit` clean.
- [ ] Step 5: Commit `feat(admin): ratings report (DBF-8)`.

### Task 8: Web — Time to answer and I stammer

**Files:**
- Create: `web/src/voice/AnswerTimePicker.tsx`
- Modify: `web/src/settings/settings.ts` (types `AnswerTime`, fields `answerTime?`, `answerTimeAuto?`, `stammer?`; defaults auto/normal/false), `web/src/voice/voices.ts` (`ANSWER_TIMES`, `answerTimeSummary(s)`), `VoiceMoodScreens.tsx` (kind `"answer"`), `SpeakingSettings.tsx` (`onAnswerTime` row on phone, picker inline on desktop, I stammer switch row), `MeScreen.tsx`, `FirstTalkScreen.tsx` (view kind `"answer"`)
- Test: `web/e2e/answer-time.spec.ts`, `web/src/voice/voices.test.ts`

**Copy (exact, from ScreenAnswerTime):** heading "Time to answer"; intro "How long Bolo waits for you to start, and how soon it replies when you stop. From your next talk."; options Automatic "Bolo learns how long you take to start answering and matches it. Right now: {Quick|Normal|Relaxed}", Quick "Bolo replies as soon as you stop. Help comes after 5 seconds of silence", Normal "A short wait after you stop. Help comes after 8 seconds", Relaxed "Plenty of time to think. Help comes after 30 seconds"; footer "Bolo always helps if you stay quiet, and pauses the talk if you stay quiet for long."; row value "Automatic · Relaxed" or the chosen label; switch "I stammer" with sub "Bolo waits through repeats and pauses, and your feedback won't count them". Desktop: radiogroup "Time to answer" (chips), sub "Automatic matches how long you take to start. Right now: Relaxed".

- [ ] Step 1: Tests. `voices.test.ts`: `answerTimeSummary({answerTime:"auto",answerTimeAuto:"relaxed"})==="Automatic · Relaxed"`, `({answerTime:"quick"})==="Quick"`, missing → "Automatic · Normal". e2e (stub API as `voice-mood.spec.ts`): phone `row shows Automatic · Relaxed`; `choosing Quick saves answerTime quick`; `I stammer switch saves stammer true`; `failed save puts Automatic back`; `fits at 390 px`; desktop `Time to answer radiogroup on the Me page`.
- [ ] Step 2: Run `cd web && npx vitest run src/voice && npx playwright test e2e/answer-time.spec.ts --project=firefox` → FAIL.
- [ ] Step 3: Implement. Step 4: Run → PASS; `npx vitest run` and `npx tsc --noEmit` clean; `e2e/voice-mood.spec.ts` still PASS.
- [ ] Step 5: Commit `feat(web): Time to answer and I stammer in Settings (AX-7, AX-8)`.

### Task 9: Web — How was today? sheet

**Files:**
- Create: `web/src/coach/RatingSheet.tsx`, `web/src/coach/rating.ts` (`fetchAsk(talkId): Promise<boolean>`, `saveRating(talkId, score: number|null, note?: string): Promise<boolean>`)
- Modify: `CoachFlow.tsx` (prop `askRating?: boolean`; when the learner reaches the last card and `askRating` and `fetchAsk` true → open once), `TalkScreen.tsx` (pass `askRating`), `bolo.css` (use existing `bo-faces`, `bo-face`, `bo-note` from DS v40 bundle; sheet/dialog as existing sheets)
- Test: `web/e2e/rating.spec.ts`

**Copy (ScreenRating):** title "How was today?"; faces Bad, Not great, Okay, Good, Great (icons `sentiment_very_dissatisfied`, `sentiment_dissatisfied`, `sentiment_neutral`, `sentiment_satisfied`, `sentiment_very_satisfied`) in a radiogroup "How was today?"; then "Want to tell us why?" textarea maxLength 300; buttons Send, Not now.

- [ ] Step 1: e2e: `asks on the last card when ask is true` (stub GET rating {ask:true}); `a face saves its score at once` (PUT body {score:4}); `Send saves the note`; `Not now before a face saves score null`; `Esc closes like Not now`; `no sheet when ask is false`; `no sheet in History`; desktop `centred dialog`.
- [ ] Step 2: Run → FAIL. Step 3: Implement. Step 4: Run → PASS; full `npx vitest run`, `npx tsc --noEmit` clean.
- [ ] Step 5: Commit `feat(web): How was today? after the feedback (DBF-8)`.

### Task 10: Docs and smoke

**Files:** `Bolo-PRD-MVP.md` (AX-7 "Changed 2026-11-14 (founder)", AX-8, DBF-8 + US-4.11 typed note, PRG-7 "Changed 2026-11-13 (founder)"), `docs/RUNBOOK.md` ("Time to answer", "Ratings"), `scripts/smoke.sh` (`GET /api/talk/<random-uuid>/rating` without session → 401; `/api/admin/ratings` without session → 401).

- [ ] Step 1: Edit docs and smoke. Step 2: `bash -n scripts/smoke.sh`. Step 3: full `cd api && ./gradlew test` PASS. Step 4: Commit `docs: PRD AX-7/AX-8/DBF-8/PRG-7, RUNBOOK time to answer and ratings`.
