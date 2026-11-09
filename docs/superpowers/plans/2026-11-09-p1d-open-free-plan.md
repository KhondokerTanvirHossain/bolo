# P1d Open to Everyone: Sign-in First and the Free Plan, Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Anyone can open Bolo, with no invite, and talk on the PRD free plan once signed in with Google.
- Welcome, consent, setup, mic check and the Day 1 clip stay open to a guest.
- The first live talk needs Google sign-in.
- Each account gets 7 free talks in its first 7 days, then 2 a month.

**Architecture:**
- **talk module:**
  - A pure `FreePlan` (`talk/domain`) turns a learner's counted talk starts into an allowance.
  - `TalkSessionService.start` refuses a guest with `sign-in` and an account with nothing left with `free-limit`.
  - A new port in, `FreeTalksUseCase`, exposes the allowance to Home.
- **learner module:**
  - Learners can be `unlimited` (the probes, the founder). A guest is unlimited when created with an unlimited invite; an account is made unlimited by script.
  - When invites aren't required, an invite code is still read, only for that flag.
- **web:**
  - The guest sheet asks for sign-in before the first talk.
  - A FreeLimit sheet, Home's footnote counter, and the plan line in Me.
  - The closed-beta copy goes.

**Tech Stack:** Spring Boot 4 / Java 21 hexagonal modules, Flyway, JdbcClient; Next.js 15, Vitest, Playwright.

**Spec:**
- `Bolo-PRD-MVP.md` ACC-5 and US-8.7 (free plan).
- ACC-2 and US-8.2 are **replaced** by the founder's decision of 2026-11-09: sign in before the first live talk. The reason is that a cookie-only guest can clear browser data to get new free talks.
- Design: `design/design-system/components/ScreenHomeFree`, `ScreenFreeLimit`, `ScreenGuestLimit`.

## Global Constraints
- **Free plan:**
  - **7 free talks in the first 7 days**, counted from the first talk; then **2 free talks a month**.
  - A talk is one 5-minute conversation plus its debrief, so on the free plan every talk is 5 minutes, whatever the setup answer said.
  - The month starts on the 1st at 00:00 Asia/Dhaka.
  - Unused first-week talks don't carry over: when the week ends, that month's 2 are available at once.
  - Limits are checked **only when a talk starts**. A talk in progress and its debrief always finish.
- **Counted talks:** a talk counts once it was attached (the socket opened), unless its debrief ended `too_short` or `failed`. A short or failed talk is not the learner's fault.
- **Guests:**
  - A guest who isn't `unlimited` gets `sign-in` (422) at talk start.
  - Everything up to the talk (welcome, consent, setup, mic check, Day 1 clip) works without an account.
  - Practice (`/api/practice`) stays open to guests and accounts. ScreenFreeLimit says drills stay free.
- **Copy (design, minus plans):**
  - Home footnote: "4 of 7 free talks left in your first week", "1 of 2 free talks left this month", or at 0 "Free talks come back on 1 December".
  - Shown once when the week ends: "Your free week is over. From now on you get 2 free talks a month."
  - FreeLimit sheet title: "You've had this month's 2 free talks" (first week: "You've had your 7 free talks for this week"). Body: "They come back on 1 December." One button: "Wait for 1 December".
  - There are no paid plans yet, so no "See plans" and no invite offer.
- **Sign-in sheet before the first talk:**
  - Title: "Sign in to start talking".
  - Body: "Bolo is free with your Google account: 7 talks in your first week, then 2 every month. Your answers and your Day 1 clip come with you."
  - Then GoogleButton, and Not now.
- **Unlimited learners** (probes, founder) see no counter and are never refused for the plan. The live-talk cap (`max-live-sessions`, 1 on Azure F0) still applies to everyone.
- **No "closed beta" copy remains** when invites are off. Me says "Free plan".

## Review Focus
1. Deleting everything and signing in again starts a new free week; this plan does nothing about it. Ruled: logged as a founder decision, because preventing it means keeping a hashed Google id after deletion, which is a privacy change. Test: none; ledger.
2. Month boundary in Dhaka: a talk at 2026-11-30T18:30Z is 1 December in Dhaka and counts for December. Test: Task 2 `theMonthTurnsAtMidnightInDhaka`.
3. Two tabs starting talks at once must not get round the limit. The existing `endOpenFor` plus one live talk per learner bounds it to one extra. Test: Task 2 `aTalkStillOpenCounts` (an attached, not yet ended talk counts).
4. The probes still pass after invites are off. Probe guests are unlimited through the probe invite. Test: Task 1 `anUnlimitedInviteMakesAnUnlimitedGuest`, and Task 5 runs the live probes.
5. A signed-in learner whose setup said 10 minutes gets a 5-minute talk. Test: Task 2 `freeTalksAreFiveMinutes`.

---

### Task 1: Unlimited learners; invites read even when not required (api)
**Files:**
- Create: `V14__free_plan.sql`, which adds:
  - `learner.unlimited boolean not null default false`;
  - `invite.unlimited boolean not null default false`;
  - `update invite set unlimited = true where label = 'smoke and live probes'`.
- Modify:
  - `InviteGate.admitNewcomer(String code) -> boolean unlimited`: when invites are required, refuse as now. A valid code is consumed and returns its `unlimited`; when invites aren't required, an unknown code is ignored.
  - `InvitePort.consume` returns `Optional<Boolean>`.
  - `GuestService` and `SignInService` store the flag on the new learner.
  - `LearnerView` gains `boolean unlimited`.
  - `scripts/probe-invite.sh` inserts `unlimited = true`.
- Create: `scripts/unlimited.sh <email>`, which sets `unlimited = true` on the account with that email and prints the number of rows changed.
- **Tests:** `learner/InviteAcceptanceTest` (or the existing invite test class):
  - `anUnlimitedInviteMakesAnUnlimitedGuest`;
  - `anOrdinaryInviteMakesAnOrdinaryGuest`;
  - `withInvitesOffAnUnknownCodeIsIgnored`.
- **Commit:** `feat(api): unlimited learners for the probes and the founder`.

### Task 2: Sign-in before the first talk; the free plan (api)
**Files:**
- Create:
  - `talk/domain/FreePlan.java`:
    - `record Allowance(String window /* "first_week" | "month" */, int left, int total, LocalDate resetsOn)`;
    - `static Allowance allowance(List<Instant> countedStarts, Instant now)`;
    - constants `FIRST_WEEK_TALKS = 7`, `MONTH_TALKS = 2`, `FIRST_WEEK = 7 days`, `ZONE = Asia/Dhaka`, `FREE_MINUTES = 5`.
    - `resetsOn` is the date talks come back: in the first week, the day the week ends (Dhaka date of first + 7 days); after that, the next 1st.
  - `talk/application/port/in/FreeTalksUseCase.java`: `Optional<FreePlan.Allowance> allowance(LearnerId)`, empty for guests and unlimited learners.
  - `TalkSessionPort.countedStarts(LearnerId) -> List<Instant>`: attached talks, left join `debrief`, excluding statuses `too_short` and `failed`.
- Modify `TalkSessionService.start`:
  - a guest who isn't unlimited gets `sign-in`; `GUEST_TALKS` is removed;
  - an account with `left == 0` gets `free-limit`;
  - minutes = `FREE_MINUTES` unless unlimited.
- **Tests:**
  - **`FreePlanTest`:**
    - `noTalksYetIsSevenOfSeven`;
    - `theFirstWeekCountsFromTheFirstTalk`;
    - `afterTheFirstWeekItIsTwoAMonth`;
    - `firstWeekTalksDoNotUseThisMonthsTwo`;
    - `theMonthTurnsAtMidnightInDhaka`;
    - `resetsOnIsTheNextFirst`.
  - **`FreePlanAcceptanceTest`:**
    - `aGuestMustSignInBeforeTheFirstTalk`;
    - `anUnlimitedGuestMayTalk`;
    - `theEighthTalkInTheFirstWeekIsRefused`;
    - `tooShortAndFailedTalksDoNotCount`;
    - `aTalkStillOpenCounts`;
    - `freeTalksAreFiveMinutes`.
  - Update every existing test that starts talks as a guest: `TalkTestClient.consentedGuest` creates an unlimited guest through an unlimited invite. Ledger the ruling.
- **Commit:** `feat(api): sign in before the first talk; the free plan (7 in the first week, then 2 a month)`.

### Task 3: Home and Me show the allowance (api)
- Modify:
  - `HomeService` and `HomeView`, which gain `freeTalks` (the Allowance, or null) from `FreeTalksUseCase`;
  - `GET /api/me`, which gains `freeTalks` the same way (Me's plan line).
- **Tests:**
  - `HomeAcceptanceTest.homeCountsFreeTalksLeft` (an account with 3 counted talks: first_week, left 4, total 7);
  - `aGuestHomeHasNoCounter`.
- **Commit:** `feat(api): Home and Me count free talks left`.

### Task 4: Web: sign-in sheet, FreeLimit sheet, Home counter, Me plan, no closed-beta copy
- Modify:
  - `GuestLimit.tsx`: the sign-in-first copy from Global Constraints. The KeepList rows become "Your answers" and "Your Day 1 clip".
  - `talkState` / `useTalk`: refusal `free-limit` → phase `out_of_talks`.
  - `FirstTalkScreen`: shows Home plus a `FreeLimit` sheet.
  - `HomeScreen`: a footnote from `home.freeTalks`, plus the one-time "Your free week is over…" note (localStorage `bolo.freeWeekOverSeen`, cleared on sign-out).
  - `MeScreen`: `email · Free plan`, and a "Plan" row with the counter sentence.
  - `AppShell`: remove the "Closed beta" card.
  - `privacy/page.tsx`: drop "Bolo is in a closed beta… ask the person who invited you".
- Create: `web/src/plan/FreeLimit.tsx` and `web/src/plan/freeTalks.ts`, containing `counterLine(a: Allowance): string` and `comeBackDay(a): string` ("1 December", formatted in Asia/Dhaka).
- **Tests:**
  - **Vitest:**
    - `counterLineReadsLikeTheDesign`: first week 4/7, month 1/2, and 0 → "Free talks come back on 1 December";
    - `freeWeekOverShowsOnce`.
  - **e2e:**
    - a guest's first talk shows "Sign in to start talking";
    - an account with 0 left gets the FreeLimit sheet, and "Wait for 1 December" closes it;
    - Home shows "4 of 7 free talks left in your first week";
    - Me shows "Free plan";
    - no "closed beta" text in the app shell.
- **Commit:** `feat(web): sign in to talk, free talks counter and limit sheet`.

### Task 5: Ship
- `smoke.sh`:
  - "a newcomer is let in without an invite" (200 plus cookie);
  - "a guest can't start a talk before signing in" (422 `sign-in`);
  - keep the probes invite for the probe guest.
- On the box, set `BOLO_INVITES_REQUIRED=false` in `~/bolo/bolo.env` (sed on that one line; never print the file).
- Full suites, push, CI (deploy only if green), deploy, smoke, then the 4 live probes.
- RUNBOOK "Free plan and open sign-up" (rules, `unlimited.sh`, what the probes use).
- Commit `docs: open sign-up and the free plan`.
