# P1c-2 Me and Privacy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The prototype's **Me** screen, inside the desktop app shell (sidebar: Talk, Me), and **Me → Privacy**:
- what Bolo keeps, why and for how long;
- the keep-recordings switch;
- **Delete everything**, confirmed by typing DELETE.

Settings move from the dialog into Me.

**Architecture:**
- **API:** `POST /api/me/delete {"confirm":"DELETE"}` (JSON, so the write guard applies). It publishes `LearnerDeleting`, then deletes the learner (DB cascade) and clears the cookie.
  - The `talk` module deletes the audio of each of the learner's talks (`talk/<id>/`).
  - `learner` deletes `learner/<id>/` (the Day 1 clip).
  - Changing the keep choice reuses `POST /api/consent`: the ledger is append-only, and the latest choice applies from the next talk.
- **Web:** an `AppShell` (prototype `dk-app` / `dk-side` / `dk-nav`), `MeScreen` and `PrivacyScreen`. Home's gear becomes a **Me** button.

**Spec:** `Bolo-PRD-MVP.md` ACC-3, ACC-4, US-8.5, US-8.6, US-9.5, NAV (Talk / Progress / Me); the design-system recipe "Me tab" (ScreenMe, ScreenSettings, ScreenPrivacy: **Delete everything** is the app's only `critical` button); prototype screen `me`.

## Global Constraints
- **Sidebar:** Talk and Me. Progress joins when it's built (next plan); an item that leads nowhere isn't shown.
- **Home layout:** Home keeps its full-scene layout for now. The prototype's dashboard Home inside the sidebar is a later redesign.
- **The Me page:**
  - the account email (or "Guest: your progress is in this browser only", with **Save your progress**, which opens the Google sign-in);
  - "Free during the closed beta" (plans are Phase 2);
  - **My goals** (read-only list of the setup answers);
  - **Speaking** (Bolo's speed, captions);
  - **Privacy**;
  - **Sign out** (accounts).
- **Privacy, in plain words:**
  - **what:** voice clips if you chose, the text of your talks and scores, your Google email and name;
  - **why:** feedback and progress;
  - **how long:** clips 90 days, or deleted within 24 h if not kept; the rest until you delete it.
  - **Keep my recordings** is a switch, saved at once, applying from your next talk.
  - **Delete everything** opens a dialog: type DELETE to enable the critical button. Afterwards this browser's state is cleared (as on sign-out) and the app returns to the welcome screen.
- **Deletion is immediate.** It's well within the PRD's 7 days, so no sweep is needed. There's no confirmation email: Bolo can't send email yet (ruled; the PRD says "SMS or email").

## Review Focus
1. Delete everything removes every row (talks, debriefs, attempts, events, usage, account, consent, settings) and every audio file of that learner, and nobody else's. Test: Task 1.
2. A missing or wrong confirm (`delete`, `DELETE `, an empty body) → 422, with nothing deleted. Test: Task 1.
3. Signed out → 401. A deleted learner's old cookie → 401 afterwards. Test: Task 1.
4. Turning keep-recordings off applies to the next talk, not the running one, and the switch shows the latest choice after a reload. Test: Task 2.
5. On a phone (390 px), the shell collapses so there's no horizontal scroll. Test: Task 2.

---

### Task 1: Delete everything (api)

**Files:**
- Create:
  - `common/event/LearnerDeleting.java`;
  - `learner/application/port/in/DeleteLearnerUseCase.java`;
  - `learner/application/service/DeleteLearnerService.java`;
  - the controller method in `learner/adapter/in/web/AuthController.java` (`POST /api/me/delete`).
- Modify: the talk module listens and deletes `talk/<id>/` for each of the learner's talks (`TalkSessionPort.idsFor(learner)`).
- Test: `learner/DeleteEverythingAcceptanceTest.java`.

**Interfaces:** `void DeleteLearnerUseCase.deleteEverything(LearnerId, String confirm)` refuses with `"confirm"` unless `confirm` equals `"DELETE"` exactly.

- [ ] **Step 1: Write the failing tests**
  - `everythingOfTheLearnerIsGone` (talk, debrief, attempt, event and usage rows; the account; the Day 1 clip; turn and clip media);
  - `anotherLearnerIsUntouched`;
  - `aWrongConfirmDeletesNothing`;
  - `signedOutIs401`;
  - `theOldCookieIsSignedOutAfterwards`.
- [ ] **Step 2:** Run `./gradlew test --tests '*DeleteEverythingAcceptanceTest*'`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Same command plus `LayerRulesTest`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(api): delete everything`.

### Task 2: Web — the app shell, Me and Privacy

**Files:**
- Create: `web/src/me/AppShell.tsx`, `MeScreen.tsx`, `PrivacyScreen.tsx`, `DeleteEverything.tsx`, and tests (Vitest and e2e `me.spec.ts`).
- Modify:
  - `HomeScreen` (the gear → a **Me** button);
  - the container (views `me` and `privacy`);
  - `SettingsCard` (its rows become reusable in Me; the dialog is removed).

- [ ] **Step 1: Write the failing tests**
  - **Vitest:**
    - `deleteNeedsTheWordDELETE` (the button is disabled until the input equals DELETE);
    - `keepSwitchPostsConsent`.
  - **e2e (stubbed):**
    - Home → Me shows the email and the sections;
    - Speaking saves Slower;
    - Privacy: the switch off → `POST /api/consent {keepRecordings:false}`, and after a reload the switch is off;
    - Delete everything: type DELETE → `POST /api/me/delete` → the start screen;
    - a guest's Me offers "Save your progress";
    - 390 px with no horizontal scroll.
- [ ] **Step 2:** Run `pnpm test && pnpm e2e --project=firefox me.spec.ts`. Expected: FAIL.
- [ ] **Step 3:** Implement with the prototype classes (`dk-app`, `dk-side`, `dk-nav`, `dk-main`, `dk-page`, `dk-h1`), `bo-btn--critical` for the delete, and chips for choices.
- [ ] **Step 4:** Run all web tests. Expected: PASS.
- [ ] **Step 5:** Commit `feat(web): Me and Privacy in the app shell; delete everything`.

### Task 3: Ship
- [ ] Full API suite, push for CI, deploy, smoke 12/12, then the probes.
- [ ] RUNBOOK: Me and Privacy, deletion.
- [ ] Commit `docs: me, privacy and deletion`.
