# P1f Dashboard Home Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Home becomes the prototype's DesktopHome inside the app shell (Talk tab). It has:
- a greeting and "Ready when you are.";
- a scene hero with Bolo's line, the Talk disc, "Space to start talking" and "Free talk · 5 min";
- **Next up**;
- **This week**, a gentle run with 1 rest day;
- sidebar cards for the free plan and "Bolo on your phone".

Close feedback and Esc return here.

**Architecture:**
- **home module:**
  - A pure `Streak` turns active days into a run, rest days and a week strip.
  - `GET /api/home` gains `lastTopic` and `week`.
  - Active days come from two new port-in methods:
    - coaching `LoopDaysUseCase.loopDays(learner)`: the Dhaka dates when ready feedback was seen, i.e. a completed loop;
    - assessment `PracticeDaysUseCase.practiceDays(learner)`: the dates of practice attempts.
  - coaching's `latestFor` also returns the last ready topic.
- **web:**
  - `HomeDashboard.tsx` inside `AppShell`; the full-scene `HomeScreen` is retired.
  - `AppShell` gets a sidebar foot with the free-plan counter and the phone card.

**Spec:**
- Prototype `design/prototypes/desktop/index.html` screen `home`; `design/design-system/components/DesktopHome`, `ScreenHome`, `ScreenHomeFree`; recipe "Home (Talk tab)".
- PRD HOM-1, US-6.9 (priority table), PRG-4 and US-6.4 (gentle streak), R1 (one primary), R2 (Talk one tap away).
- Founder decisions of 2026-11-10 (memory bolo-home-dashboard).

## Global Constraints
- **Streak (US-6.4, founder):**
  - **Active day:** a day (Asia/Dhaka) with a completed loop (ready feedback seen) or a practice attempt.
  - **Run:** counted back from today. Today counts if it's active; if not, it's still open, not a miss.
  - **Rest:** a missed day is forgiven once per calendar week. Weeks run Saturday to Friday, as the prototype's strip does.
  - A second miss in the same week ends the run. The run never reaches back before the first active day.
  - **Run count:** the number of active days in it, e.g. "6-day run".
  - **Rest note:** "1 rest day used" when this week's rest is used.
  - **Strip:** Saturday to Friday of this week, each day `done`, `rest`, `missed`, `today` (not yet active) or `future`.
  - **Restart:** when the learner has activity but no run, the card says "Welcome back. Let's start a new run." No guilt words, no broken-flame graphic.
- **Bolo's line (founder):**
  - With a last topic: `Last time: <topic>. Tell me what happened next.`
  - Otherwise: "Bolo talks out loud, so turn your sound on."
  - A one-off note ("See you tomorrow. Bolo will be here.") replaces it when present.
- **Greeting:**
  - "Good morning" until 12:00, "Good afternoon" until 17:00, "Good evening" after that.
  - Add ", <first name>" when signed in.
- **Scenario line:** "Free talk · 5 min", with no "Change". The scenario picker isn't built, and Bolo never shows a dead link.
- **Next up** (US-6.9 priority):
  - "Finish your feedback" (sub: "Your coach's feedback is ready") when one is unseen;
  - else "Practise <the v sound>" (sub: "From your last talk");
  - else "Practise sounds" (sub: "Read sentences for one sound").
  - It's always a `bo-homecard` row, never a filled button: the Talk disc is the one primary.
- **Space** starts a talk on Home, unless focus is in a text field.
- **Sidebar foot:**
  - Free plan card: "Free plan" plus the counter line (from `/api/home` freeTalks). It's hidden for unlimited learners and guests; there's no "See plans".
  - "Bolo on your phone": "Open bolo.eliooo.org in your phone's browser and sign in with the same Google account." No QR code yet.
- **The Day 1 clip** stays reachable: a quiet "Play my Day 1 clip" under the scenario line when it's saved on this device.
- **Phones (≤ 720 px):** the sidebar is the top bar, the foot cards are hidden, the hero is shorter, and the columns stack. No sideways scroll.

## Review Focus
1. Streak week boundary: Saturday in Dhaka at 00:00. A miss on Friday and another on Saturday are in different weeks, so both are forgiven. Test: Task 1 `restIsOncePerSaturdayWeek`.
2. Today not yet active never breaks the run. Test: Task 1 `todayWithoutActivityIsOpenNotMissed`.
3. Late-night UTC times count on the Dhaka date: 2026-11-09T19:30Z is 10 November in Dhaka. Test: Task 1 `activityCountsOnTheDhakaDate`.
4. Space typed into a text field (Delete everything's input) never starts a talk. Test: Task 2 e2e.
5. On a 390 px phone, Home fits with no sideways scroll, and Talk is visible without scrolling. Test: Task 2 e2e.

---

### Task 1: Streak and Home data (api)
- **Create:**
  - `home/domain/Streak.java`:
    - `record Week(int run, boolean restUsed, boolean welcomeBack, List<Day> days)`, `record Day(LocalDate date, String state)`;
    - `static Week of(Collection<Instant> activity, Instant now)`.
  - `coaching/application/port/in/LoopDaysUseCase.java`: `List<Instant> loopDays(LearnerId)`, the `viewed_at` of ready debriefs.
  - `assessment/application/port/in/PracticeDaysUseCase.java`: `List<Instant> practiceDays(LearnerId)`.
- **Modify:**
  - `LatestDebriefUseCase.Latest` gains `lastTopic` (the latest ready debrief's topic, or null).
  - `HomeView` gains `lastTopic` and `week`.
- **Tests:**
  - **`StreakTest`:**
    - `noActivityIsNoRunAndNoWelcomeBack`;
    - `consecutiveDaysCount`;
    - `todayWithoutActivityIsOpenNotMissed`;
    - `oneMissInAWeekIsARestDay`;
    - `twoMissesInAWeekEndTheRun`;
    - `restIsOncePerSaturdayWeek`;
    - `activityCountsOnTheDhakaDate`;
    - `theStripIsSaturdayToFriday`;
    - `aBrokenRunSaysWelcomeBack`.
  - **`HomeAcceptanceTest`:**
    - `homeHasTheWeekAndTheLastTopic`;
    - `practiceCountsAsAnActiveDay`.
- **Commit:** `feat(api): Home's week (gentle run) and last topic`.

### Task 2: Web: the dashboard Home
- **Create:**
  - `web/src/home/HomeDashboard.tsx`;
  - `web/src/home/greeting.ts` (`greeting(hour, name)`, `boloLine(home, note)`);
  - `web/src/home/WeekCard.tsx`.
- **Modify:**
  - `home.ts` types;
  - `AppShell` (foot cards);
  - `FirstTalkScreen`: the `home` view renders `AppShell tab="talk"` with `HomeDashboard`; the GuestLimit and FreeLimit sheets sit over it; Close feedback and Esc already return to `home`.
  - Retire `HomeScreen.tsx` and its test. Move its unit tests' intent to the new component.
- **Tests:**
  - **Vitest:**
    - `greetingFollowsTheClock`;
    - `boloLineUsesTheLastTopic`;
    - `weekCardShowsRestAndRun`;
    - `welcomeBackHasNoGuilt`.
  - **e2e:** update `home.spec.ts` and every spec that used "Start talking" to the new Talk disc (`button "Talk"`). New checks:
    - the greeting and the line;
    - Next up opens the feedback;
    - Space starts a talk, but not from Me's DELETE input;
    - the sidebar foot shows the counter;
    - 390 px with no sideways scroll and Talk in view;
    - Close feedback lands on Home.
- **Commit:** `feat(web): the dashboard Home`.

### Task 3: Ship
- Full suites, then `scripts/ship.sh` (CI-gated deploy, smoke, probes).
- RUNBOOK "Home".
- Commit `docs: home`.
