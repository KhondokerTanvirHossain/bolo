# Bolo knows you (P3b) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bolo keeps a short note per learner, updated after each talk. Each talk opens with one personal follow-up, and the talk stays interesting. The learner sees and controls the note in Me → What Bolo remembers.

**Architecture:** A new hexagonal `memory` module owns the note:
- It holds table `learner_memory` (one row per learner) and the domain `MemoryNote`, which sets the caps, the 30-day drop and the opener pick.
- It listens to `TalkEnded` and updates the note with one Sonnet structured call, off the talk's path.
- It serves a `TalkBrief` through `MemoryUseCase` (port.in). `TalkRelayService` asks for the brief when a talk opens and passes it to `BuddyPrompt.system`.
- The web client gets a `MemoryScreen` under Me.

**Tech Stack:** Spring Boot 4 / Java 21, JdbcClient, Flyway, Postgres jsonb; Next.js 15 / React 19, Vitest, Playwright.

**Spec:** `docs/superpowers/specs/2026-11-13-bolo-knows-you-design.md`. Design system v35 cards: ScreenMemory, ScreenMemoryEmpty, DesktopMemory, ScreenConversationRemembers, the Me row.

## Global Constraints
- **Caps, enforced in code whatever the model returns:**
  - About you: 12 lines;
  - To follow up: 8 threads;
  - Interests: 8 lines;
  - Recent topics: 6;
  - each line at most 120 characters.
- **Threads drop 30 days after they were last mentioned.** A thread that opened a talk is skipped for the next 2 talks and for 3 days, whichever is later.
- **Opener order:**
  1. a plan whose due date has passed;
  2. a worry;
  3. prep progress;
  - within a kind, the most recently mentioned first.
- **Never update the note from:**
  - a talk that ended with reason `crisis`;
  - a talk with fewer than 2 learner turns;
  - a learner with remembering off.
- **The note never holds:** health (including mental health), religion, politics, sexuality, exact money amounts, phone numbers, addresses or ID numbers. Details of other people are limited to a first name and role.
- **Models:** the Buddy stays `claude-haiku-5-5`. The updater is `claude-sonnet-5-5` (`bolo.memory.model`), with max tokens 1500, structured output and `fast=false`, and a 60 s timeout.
- **Copy, verbatim:**
  - privacy line: "Bolo keeps short notes from your talks so it can follow up, like a friend would. Only you can see them. Remove any line, or turn this off."
  - empty state: "Nothing yet", then "After your next talk, the things Bolo will ask you about show up here."
  - with remembering off: "Bolo isn't keeping notes. Each talk starts fresh."
  - switch: "Let Bolo remember our talks", with the sub-line "Off: Bolo starts fresh each time".
  - button: "Forget everything Bolo knows", which confirms with "Bolo will forget everything it knows about you. Talks still work as before."
  - kind labels: Plan / On your mind / Your practice;
  - each thread's date: "You said this on 11 Nov".
- **Module boundaries:** modules meet only through `application.port.in` or `common` (LayerRulesTest).
- **No notes from past talks.** No admin view of anyone's note.
- **Timezone:** dates are Bangladesh dates (`BoloClock.today()`).

## Review Focus
1. **The updater returns junk:** invalid JSON, wrong types, over the caps, or 300-character lines. The old note stays, or the result is capped and trimmed. The note is never corrupted. Owned by Task 2: `junkFromTheModelKeepsTheOldNote` and `oversizedNoteIsCapped`.
2. **The learner removes a line, or turns remembering off, while an update is running.** The removed line stays removed, and an off switch means nothing is written. Owned by Task 2: `aLineRemovedDuringAnUpdateStaysRemoved` and `turningOffDuringAnUpdateWritesNothing`.
3. **Two talks for one learner end close together.** Both updates apply in turn, and neither is lost. Owned by Task 2: `updatesForOneLearnerRunOneAtATime`.
4. **The same talk asks for its brief twice** (a socket reopen). It gets the same opener and the talk is counted once. Owned by Task 3: `aSecondBriefForTheSameTalkKeepsItsOpener`.
5. **Dates near midnight Bangladesh time,** and an overdue plan versus a plan due later. "Said" and "due" use Dhaka dates, and only a past due date ranks a plan first. Owned by Task 1: `overduePlanBeatsAWorry` and `aPlanDueLaterRanksBelowAWorry`.

---

### Task 1: The note (domain, table, persistence)

**Files:**
- Create: `api/src/main/resources/db/migration/V26__learner_memory.sql`
- Create: `api/src/main/java/app/bolo/memory/domain/MemoryNote.java`, `api/src/main/java/app/bolo/memory/domain/MemoryThread.java` (not `Thread`, which would clash with java.lang.Thread), `api/src/main/java/app/bolo/memory/domain/Line.java`
- Create: `api/src/main/java/app/bolo/memory/application/port/out/MemoryPort.java`, `api/src/main/java/app/bolo/memory/adapter/out/persistence/JdbcMemoryAdapter.java`
- Test: `api/src/test/java/app/bolo/memory/domain/MemoryNoteTest.java`

**Interfaces:**
- Produces:
  - **Table.** `learner_memory(learner_id uuid primary key references learner(id) on delete cascade, enabled boolean not null default true, note jsonb not null default '{}', talks int not null default 0, last_talk_id uuid, updated_at timestamptz not null default now())`.
  - `record Line(String id, String text)`.
  - `record MemoryThread(String id, Kind kind, String text, LocalDate said, LocalDate due, Instant openedAt, Integer openedTalk)`, with `enum Kind { PLAN, WORRY, PREP }`. The json values are `"plan"`, `"worry"` and `"prep"`.
  - `record MemoryNote(List<Line> about, List<MemoryThread> threads, List<Line> interests, List<String> recent)`, with:
    - `static MemoryNote empty()`;
    - `MemoryNote capped(LocalDate today)`: applies the caps and the 120-character trim, and drops threads with `said` more than 30 days before `today`;
    - `Optional<MemoryThread> opener(LocalDate today, Instant now, int talkNumber)`: the ranking and resting rule from Global Constraints;
    - `MemoryNote withOpened(String threadId, Instant now, int talkNumber)`;
    - `MemoryNote without(String lineId)`, which removes from all three lists;
    - `Set<String> ids()`;
    - `MemoryNote keepOnly(Set<String> existingIds, Set<String> oldIds)`: drops lines whose id was in `oldIds` but is no longer in `existingIds`, so a line removed mid-update stays removed.
  - `MemoryPort`:
    - `record Row(boolean enabled, MemoryNote note, int talks, UUID lastTalkId)`;
    - `Optional<Row> find(LearnerId)`;
    - `Row lockOrCreate(LearnerId)` (select … for update, inserting the row first if missing; call it inside a transaction);
    - `void save(LearnerId, boolean enabled, MemoryNote note, int talks, UUID lastTalkId)`;
    - `void move(LearnerId from, LearnerId to)`.
  - JSON mapping uses the project's Jackson `JSON` mapper. Dates are ISO strings.

- [ ] **Step 1: Write the failing tests** in `MemoryNoteTest`:
  - `capsApplyWhateverTheModelSent`: 20 about lines, 12 threads and 10 interests become 12 / 8 / 8. A 300-character line becomes ≤ 120 characters, ending in "…".
  - `threadsFallAway30DaysAfterLastMention`: with today 2026-12-13, a thread said 2026-11-12 is dropped and one said 2026-11-13 is kept.
  - `overduePlanBeatsAWorry`: a plan due 2026-11-12 (today 2026-11-13) beats a worry said 2026-11-12.
  - `aPlanDueLaterRanksBelowAWorry`: a plan due 2026-11-20 loses to a worry. A plan with no due date ranks with the plans below worries, as kind order plan > worry > prep applies only to overdue plans. The ranking is overdue plan, then worry, then prep, then other plans, most recent `said` first within a group.
  - `aThreadThatOpenedRestsTwoTalksAndThreeDays`: opened at talk 4 on day D, it is skipped at talks 5 and 6, still skipped at talk 7 on D+2, and eligible at talk 7 on D+3.
  - `allRestingMeansNoOpener`: the opener is empty.
  - `withoutRemovesTheLineEverywhere`.
  - `keepOnlyDropsLinesRemovedMeanwhile`: old ids {a,b}, existing {a}, and the new note has a, b and a new line c, so the result has a and c.
- [ ] **Step 2: Run them.** `cd api && ./mvnw -q test -Dtest=MemoryNoteTest`. Expected: compilation failure (classes missing).
- [ ] **Step 3: Implement** the domain records and methods, `V26__learner_memory.sql` (with a header comment naming the spec), `MemoryPort` and `JdbcMemoryAdapter`. For `move`, if `to` has no row, update `learner_id`. Otherwise merge `from`'s lines into `to`'s note up to the caps, then delete `from`'s row.
- [ ] **Step 4: Run them.** Same command. Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(api): the learner's memory note (P3b)"`

### Task 2: Updating the note after a talk

**Files:**
- Create: `api/src/main/java/app/bolo/memory/domain/MemoryPrompt.java`, the updater's system prompt, transcript format and JSON schema
- Create: `api/src/main/java/app/bolo/memory/application/service/MemoryService.java`
- Modify: `api/src/main/java/app/bolo/llm/adapter/out/fake/FakeLlm.java`: route a structured request whose schema properties contain `"threads"` to `memoryReply`, with setters `memoryReply(String json)` and `failMemory(boolean)`. The default reply is a valid note with one plan, "Sending the CV to the bank job", kind plan, due null.
- Modify: `api/src/main/resources/application.yml`: `bolo.memory.model: claude-sonnet-5-5` and `bolo.memory.max-tokens: 1500`.
- Test: `api/src/test/java/app/bolo/memory/MemoryUpdateAcceptanceTest.java`

**Interfaces:**
- Consumes:
  - `TalkEnded(id, learner, learnerTurns, reason)`;
  - `TalkQueryUseCase.record(TalkSessionId) → Optional<TalkRecord>`;
  - `LlmClient.stream(TalkContext, LlmRequest, onDelta)`;
  - `MemoryPort` and `MemoryNote` (Task 1);
  - `BoloClock`.
- Produces:
  - `MemoryService.onTalkEnded(TalkEnded)` (`@EventListener`; starts a virtual thread);
  - `void update(TalkSessionId id, LearnerId learner)` (package-visible, synchronous, for tests).
  - **The updater's input:** today's date, the current note as JSON with ids, and the transcript as `Bolo:` / `You:` lines.
  - **Its schema (all required):**
    - `about: [{id: string|null, text}]`;
    - `threads: [{id: string|null, kind: plan|worry|prep, text, said: "YYYY-MM-DD", due: "YYYY-MM-DD"|null}]`;
    - `interests: [{id|null, text}]`;
    - `topic: string` (2 to 5 words).
  - **The prompt's rules:**
    - keep ids for kept lines;
    - null id for new lines;
    - leave out finished plans;
    - set `said` to today when a thread is mentioned again;
    - only what the learner said about themselves;
    - none of the never-store categories (Global Constraints);
    - write lines the way the learner would say them, in at most 120 characters.
  - **How the server applies the reply:**
    - new ids are generated server-side (8 hex chars), and ids the model invented that aren't in the old note count as new;
    - `openedAt` and `openedTalk` carry over by id;
    - `topic` is pushed to the front of `recent`;
    - `keepOnly(current ids at save time, ids sent to the model)`, then `capped(today)`;
    - saving happens in a transaction that re-locks the row and writes nothing if `enabled` is now false.
  - **One at a time per learner:** an in-process per-learner lock (`ConcurrentHashMap<LearnerId, ReentrantLock>`) around read → model → save.

- [ ] **Step 1: Write the failing acceptance tests.** Set up as `TalkRelayAcceptanceTest` does (fake speech and LLM, shrunk thresholds), seeding talks with `jdbc` inserts into `talk_session` and `talk_turn` (4 turns: buddy, learner, buddy, learner) or running a real talk. Then call `memory.update(id, learner)` directly, or publish `TalkEnded` and poll for up to 5 s:
  - `aTalkWithTwoTurnsWritesTheNote`: the row has the plan "Sending the CV to the bank job" with `said` equal to today's Dhaka date, and `recent` starts with the fake topic.
  - `aCrisisTalkIsNeverUsed`: `TalkEnded` with reason `crisis`, so no row is written.
  - `oneTurnIsNotEnough`: learnerTurns 1, so no change.
  - `rememberingOffWritesNothing`.
  - `junkFromTheModelKeepsTheOldNote`: `memoryReply("not json")`, and the earlier note is unchanged.
  - `oversizedNoteIsCapped`: a reply with 15 threads stores 8.
  - `aLineRemovedDuringAnUpdateStaysRemoved`: start an update with a slow fake (`deltaMs(50)`), remove an existing line via `MemoryPort`/service mid-way, and the line is absent after the update.
  - `turningOffDuringAnUpdateWritesNothing`.
  - `updatesForOneLearnerRunOneAtATime`: two concurrent `update` calls for the same learner both finish, and `recent` has 2 entries.
  - `theUpdaterIsToldTheRules`: the structured request's system prompt contains "health", "religion" and "Only what the learner said about themselves".
- [ ] **Step 2: Run them.** `./mvnw -q test -Dtest=MemoryUpdateAcceptanceTest`. Expected: FAIL (no MemoryService).
- [ ] **Step 3: Implement** `MemoryPrompt` and `MemoryService` (the listener, `update`, the per-learner lock and the transactional save via `TransactionTemplate`), the FakeLlm route and the config. On failure, log type and location only, never the learner's words; reuse the pattern of `CoachService.where`.
- [ ] **Step 4: Run them.** Expected: PASS. Also run `./mvnw -q test -Dtest=LayerRulesTest`: PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(api): Bolo updates its note after each talk (P3b)"`

### Task 3: The brief, and a talk that opens from it

**Files:**
- Create: `api/src/main/java/app/bolo/memory/application/port/in/MemoryUseCase.java`, `api/src/main/java/app/bolo/memory/application/port/in/TalkBrief.java`, `api/src/main/java/app/bolo/memory/domain/TopicBank.java`
- Modify: `MemoryService` (implements `brief`)
- Modify: `api/src/main/java/app/bolo/talk/domain/BuddyPrompt.java`: `system(goal, fearedMoment, minutes, String brief)`, keeping the 3-argument overload as `brief = null`
- Modify: `api/src/main/java/app/bolo/talk/application/service/TalkRelayService.java` and `TalkConversation.java`: get the brief in `open`, then pass it to the constructor and `BuddyPrompt.system`
- Test: `api/src/test/java/app/bolo/talk/domain/BuddyPromptTest.java` (add cases), `api/src/test/java/app/bolo/memory/MemoryBriefAcceptanceTest.java`

**Interfaces:**
- Produces:
  - `record TalkBrief(String text)`, the block appended to Bolo's system prompt.
  - `MemoryUseCase.brief(LearnerId learner, TalkSessionId talk, String goal) → TalkBrief`. The brief text holds:
    - when remembering is on and the note isn't empty, "What you remember about them:" followed by the about lines, the open threads with their dates, and the interests;
    - when there's an opener thread, "Open the talk with a short hello and one follow-up about: <thread text> (they said this on <date>). Ask it once. If they move on, follow them and don't bring it back.";
    - "Topics you've talked about recently (pick something else): …";
    - "Ideas if the talk needs a new direction: " followed by 3 `TopicBank` seeds for the goal (or the general list), none of them in `recent`.
    - **Side effects (in a transaction on the locked row):**
      - when `talk` ≠ `last_talk_id`, `talks` goes up by 1 and `last_talk_id` becomes `talk`, and the chosen opener is marked with `withOpened(id, now, talks)`;
      - when `talk` = `last_talk_id`, it returns the same opener: the thread whose `openedTalk == talks`.
    - With remembering off, the brief holds only the seeds, and nothing is written. A learner with no row gets a row created (enabled, empty).
  - `TopicBank.seeds(String goal, Set<String> recent, Random rnd, int n) → List<String>`. It holds 20 seeds for each goal key (`work_meetings`, `job_interviews`, `study_abroad`, `daily_confidence`) and 20 general ones. They're concrete, set in everyday Bangladeshi adult life, and are not routine chatter. Example: "A colleague takes credit for your idea in a meeting. What do you say?".
  - **`BuddyPrompt.system` additions, always present:**
    - "Ask about stories, opinions, small what-ifs and choices, and now and then a light, real rehearsal of their goal. Never a test."
    - "Never ask what they ate, what they did today, or about the weather, unless they bring it up."
    - "React to what they said with a short opinion or feeling of your own before your question. You never invent a life of your own: no trips, meals or family."
    - "If they want to talk about something else, follow them fully."
    - **First-line rule, with no opener in the brief:** "Open with a short hello and a real question about their situation for their goal (for job interviews: what kind of job they're going for), never a routine question."
    - The brief, when non-blank, is appended as its own block.

- [ ] **Step 1: Write the failing tests.**
  - `BuddyPromptTest`:
    - `theRulesForInterestingTalksAreAlwaysThere` (contains "Never ask what they ate");
    - `theBriefIsAppended`;
    - `noBriefMeansOpeningFromTheGoal` (contains "kind of job" for `job_interviews`).
  - `MemoryBriefAcceptanceTest` (fake LLM):
    - `aTalkOpensWithTheFollowUp`: seed a note with a plan, start a talk over the socket, and `llm.lastReplyRequest()` contains the plan text and "one follow-up about".
    - `theNextTwoTalksDontReuseIt`: talks 2 and 3 don't contain "one follow-up about: <that plan>".
    - `aSecondBriefForTheSameTalkKeepsItsOpener`: `brief` twice with one talk id gives equal text, and `talks` goes up by 1.
    - `rememberingOffSendsNoNotes`: no "What you remember" in the request.
    - `recentTopicsAreNotSuggested`: `TopicBank` with `recent` containing a seed never returns it (unit-level, 200 random draws).
- [ ] **Step 2: Run them.** `./mvnw -q test -Dtest='BuddyPromptTest,MemoryBriefAcceptanceTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.** If the brief call throws, the talk still opens with `brief = null` (log it). A brief failure must never stop a talk.
- [ ] **Step 4: Run them, plus `TalkRelayAcceptanceTest` and `LayerRulesTest`.** Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(api): Bolo opens each talk from what it remembers (P3b, TLK-11)"`

### Task 4: The learner's controls (API, merge, delete everything)

**Files:**
- Create: `api/src/main/java/app/bolo/memory/adapter/in/web/MemoryController.java`
- Modify: `MemoryUseCase` and `MemoryService`
- Test: `api/src/test/java/app/bolo/memory/MemoryApiAcceptanceTest.java`

**Interfaces:**
- Produces:
  - `MemoryUseCase.view(LearnerId) → MemoryView`, where `record MemoryView(boolean enabled, List<FollowUp> followUps, List<Line> about, List<Line> interests)` and `record FollowUp(String id, String kind, String text, LocalDate said)`. `kind` is "plan", "worry" or "prep". Recent topics are never included.
  - `MemoryUseCase.remove(LearnerId, String lineId)`, `setEnabled(LearnerId, boolean)` (off empties the note) and `forget(LearnerId)` (empties the note, keeps `enabled`).
  - HTTP:
    - `GET /api/memory` returns 200 and the MemoryView JSON;
    - `DELETE /api/memory/lines/{id}` returns 204 (idempotent);
    - `PUT /api/memory` with `{"enabled":bool}` returns 204;
    - `POST /api/memory/forget` returns 204;
    - all return 401 when there's no session.
  - `@EventListener onLearnerMerged(LearnerMerged)` calls `MemoryPort.move`. Delete everything is covered by the FK cascade.

- [ ] **Step 1: Write the failing acceptance tests:**
  - `aNewLearnerSeesAnEmptyNoteWithRememberingOn`.
  - `theViewShowsFollowUpsAboutAndInterestsButNotRecentTopics`.
  - `removingALineRemovesIt`.
  - `turningOffEmptiesTheNote`.
  - `forgetKeepsRememberingOn`.
  - `signedOutIs401` (all four endpoints).
  - `deleteEverythingRemovesTheNote`: account with a note, then the delete-everything endpoint as `PrivacyAcceptanceTest` uses it, and the row is gone.
  - `aGuestsNoteMovesWhenSigningIntoAnExistingAccount`.
- [ ] **Step 2: Run them.** `./mvnw -q test -Dtest=MemoryApiAcceptanceTest`. Expected: FAIL (404s).
- [ ] **Step 3: Implement** the controller, the service methods and the merge listener.
- [ ] **Step 4: Run the full API suite.** `./mvnw -q verify > /tmp/p3b-api.log 2>&1; tail -40` (or the workspace log). Expected: BUILD SUCCESS.
- [ ] **Step 5: Commit.** `git commit -m "feat(api): see, remove and switch off what Bolo remembers (P3b)"`

### Task 5: The web screen

**Files:**
- Create: `web/src/memory/memory.ts` (`fetchMemory(): Promise<MemoryView | null>`, `removeLine(id)`, `setRemembering(on)`, `forgetAll()`, all through `api` from `@/api/browser`; `saidLabel(iso) → "You said this on 11 Nov"` in Asia/Dhaka; `KIND_LABEL = { plan: "Plan", worry: "On your mind", prep: "Your practice" }`)
- Create: `web/src/memory/MemoryScreen.tsx`
- Create: `web/src/memory/memory.test.ts`
- Modify: `web/src/me/MeScreen.tsx` (new prop `onMemory`; the row "What Bolo remembers" with icon `auto_stories`, placed before Privacy; its sub-line comes from `fetchMemory`: "N things to follow up", "1 thing to follow up", "Nothing yet" or "Off")
- Modify: `web/src/onboarding/screens/FirstTalkScreen.tsx` (View kind `"memory"`, routed like `"invite"`)
- Modify: `web/src/me/PrivacyScreen.tsx` (one list item: "Short notes from your talks, so Bolo can follow up. See them in Me → What Bolo remembers.") and `web/src/app/privacy/page.tsx` (a "What Bolo keeps" item plus a "For how long" item: "Notes Bolo keeps about you: until you remove them, turn remembering off, or delete everything." Update the "Updated" date to 13 November 2026). Also `web/src/app/terms/page.tsx`: one sentence in the section about what Bolo keeps (or after the privacy reference): "Bolo keeps short notes from your talks so it can follow up; you can see, remove or switch them off in Me → What Bolo remembers."
- Modify: `web/src/styles/bolo.css` (or the app stylesheet that holds `bo-row`): add `.bo-row__remove`, `.bo-row__kind` and `.bo-memnote`, copied from design-system `components/bundle.css` v35
- Test: `web/e2e/memory.spec.ts`

**Interfaces:**
- Consumes: Task 4's HTTP API.
- Produces: `MemoryScreen({ onBack }: { onBack: () => void })`. Layout per ScreenMemory:
  - a back button "Me";
  - an h1 "What Bolo remembers";
  - the privacy line;
  - groups "To follow up", "About you" and "Your interests", each shown only when non-empty, with each row's ×, aria-label "Remove: <text>";
  - with all three groups empty, the empty card (ScreenMemoryEmpty) instead, or the off copy when remembering is off;
  - the switch row;
  - "Forget everything Bolo knows" (shown only when the note isn't empty), which opens an inline confirm with the confirm text and the buttons "Forget everything" and "Cancel".
  - Removing updates the list at once (optimistic). On failure it restores the line and shows "That didn't save. Try again." in `role="alert"`.

- [ ] **Step 1: Write the failing tests.**
  - `memory.test.ts`: `saidLabel("2026-11-11")` is "You said this on 11 Nov", and the kind labels map.
  - `memory.spec.ts`, with stubbed `/api/me`, `/api/home` and `/api/memory` as in `referral.spec.ts`:
    - `Me shows the row with its count, and the screen lists the three groups`;
    - `removing a line hides it and calls DELETE /api/memory/lines/:id`;
    - `turning remembering off shows the off copy and calls PUT with enabled:false`;
    - `forget everything asks first, then empties`;
    - `the empty note shows "Nothing yet"`;
    - `fits at 390 px with no horizontal scroll`.
- [ ] **Step 2: Run them.** `cd web && npx vitest run src/memory && npx playwright test e2e/memory.spec.ts --project=firefox`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them, plus `npx tsc --noEmit`, `npx vitest run`, and `npx playwright test e2e/me.spec.ts e2e/privacy.spec.ts --project=firefox`.** Expected: PASS.
- [ ] **Step 5: Commit.** `git commit -m "feat(web): Me → What Bolo remembers (P3b)"`

### Task 6: Docs and ship checks

**Files:**
- Modify: `Bolo-PRD-MVP.md`:
  - TLK-11 becomes "**Changed 2026-11-13 (founder):** Bolo keeps short notes about the learner (plans, worries, practice, facts, interests), updated after each talk. Each talk opens with one personal follow-up, picked on the server; the learner steers. *Was: remember the previous session's topic …*";
  - add **TLK-17** (interesting talks: stories, opinions, what-ifs, choices, rehearsal; no routine chatter; topic bank);
  - add **ACC-9** (Me → What Bolo remembers: see, remove, switch off, forget);
  - add "Changed 2026-11-13" to US-2.11.
- Modify: `docs/RUNBOOK.md`: a section "What Bolo remembers". It covers:
  - how the note is updated, and that the updater uses Sonnet once per talk;
  - how to test it by clicking;
  - that there's no admin view;
  - that delete everything removes it.
- Modify: `scripts/smoke.sh`: `GET /api/memory` without a cookie returns 401.

- [ ] **Step 1:** Edit the three files.
- [ ] **Step 2:** `bash -n scripts/smoke.sh`. Expected: no output.
- [ ] **Step 3:** `git commit -m "docs: PRD TLK-11 changed, TLK-17, ACC-9; RUNBOOK What Bolo remembers (P3b)"`
