# P2a Plans, Grants, the Daily Cap and the Admin Portal (core) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal (founder, 2026-11-11, "absolute minimum to go public"):**
- **Paid plans exist as entitlements:** Basic, Premium and Ultimate, each a monthly talk count. They can be given by the admin now, and by referrals and bKash payments next.
- **A global daily cap on talks** protects the bill; when it's reached, people see "Bolo is resting".
- **An admin portal** for the founder only:
  - **Users:** search; plan and usage; unlimited on or off; give a friend a plan for N days.
  - **Usage:** per day, with an estimated cost and today against the cap.
  - **Settings:** the daily cap, and each plan's talks and prices, with no deploy needed.

**Architecture:**
- **talk module** (owner of the free plan):
  - tables `plan_setting`, `subscription` and `app_setting` (V17);
  - `PlanAllowance` extends the free plan: an active subscription gives its plan's monthly talks for the current Dhaka month;
  - `TalkSessionService.start` also refuses `daily-cap` when today's talks across everyone (except unlimited learners) reach the cap.
- **New `admin` module:**
  - `AdminGuard` (the signed-in account's email is in `bolo.admin.emails`);
  - read-only reporting queries over the tables (an admin read model);
  - grants and settings through the talk and learner ports in.
- **web:**
  - `/admin`, a separate page with three tabs;
  - the app's plan counter ("12 of 20 Premium talks left this month");
  - a "Bolo is resting" sheet.

**Spec:** PRD ACC-5 (plans differ only by talk count; never "Unlimited"; prices Basic ৳300, Premium ৳500, Ultimate ৳1000), US-8.7, feedback-log item 6 (admin portal), memory bolo-go-public.

## Global Constraints
- **Plans, seeded and editable in Settings:**

  | Plan | Talks a month | ৳ a month | $ a month |
  |---|---|---|---|
  | Basic | 8 | 300 | 3 |
  | Premium | 20 | 500 | 5 |
  | Ultimate | 45 | 1000 | 10 |

  The free plan stays as it is: 7 talks in the first week, then 2 a month.
- **Subscription:** `{learner, plan, starts_at, ends_at, source: admin|referral|bkash, note}`.
  - Active when `starts_at ≤ now < ends_at`. If several are active, the one with the most talks wins.
  - A paid month counts the Dhaka calendar month's talks started since the subscription began.
  - The counted-talk rule is the same as the free plan's.
- **Daily cap:**
  - `app_setting daily_talk_cap`, default 300;
  - today counts every learner's talk starts on the Dhaka date, except unlimited learners;
  - at the cap a start is refused with 422 `daily-cap`;
  - the web sheet says: "Bolo is resting. So many people talked today that Bolo needs a break. Back tomorrow." with OK;
  - a talk in progress always finishes.
- **Admin:**
  - `BOLO_ADMIN_EMAILS` (comma-separated; compose sets the founder's);
  - an admin is a signed-in **account** whose email is on the list;
  - everyone else gets **404** on `/api/admin/*` (don't reveal that it exists);
  - each admin write is logged at INFO with the admin's and target's ids (no emails in logs).
- **Usage cost estimate:** from the `usage` ledger with these prices, editable in code; an estimate, labelled so:
  - Azure speech recognition $1.00/audio hour;
  - Azure TTS $16 per million characters;
  - Claude: input $3 and output $15 per million tokens.
- **/admin** for anyone else shows "Not found".

## Review Focus
1. A non-admin, a guest, or an admin email used by a guest (not an account) gets 404 on every admin endpoint. Test: Task 2.
2. A granted plan that ended returns the learner to the free plan; overlapping grants take the bigger one. Test: Task 1.
3. Unlimited learners and the live probes never meet the daily cap. Test: Task 1.
4. A Premium learner on their 21st talk this month is refused `free-limit` with the plan's counter. Test: Task 1.
5. On the admin Settings screen, lowering a plan's talks below what someone has used shows 0 left, never negative. Test: Task 1.

---

### Task 1: Plans, subscriptions and the daily cap (api)
- **Create:**
  - `V17__plans.sql` (`plan_setting` seeded, `subscription`, `app_setting` with `daily_talk_cap=300`);
  - `talk/domain/PlanAllowance.java` (pure: free allowance or the plan's month);
  - `talk/application/port/in/PlansUseCase.java` (`grant(learner, plan, days, source, note)`, `active(learner)`, `plans()`, `updatePlan`, `dailyCap()`, `setDailyCap`, `todayCount()`);
  - persistence.
- **Modify:**
  - `FreeTalks` gains `plan` ("free"|"basic"|"premium"|"ultimate");
  - `TalkSessionService.start` uses PlanAllowance and the daily cap.
- **Tests:**
  - **PlanAllowanceTest:**
    - `anActivePlanGivesItsMonthlyTalks`;
    - `anEndedPlanFallsBackToFree`;
    - `theBiggerOfTwoActivePlansWins`;
    - `usedMoreThanTheNewLimitShowsZero`.
  - **FreePlanAcceptanceTest:**
    - `aGrantedPremiumLearnerHas20ThisMonth`;
    - `theTwentyFirstPremiumTalkIsRefused`;
    - `theDailyCapRestsEveryoneButUnlimited`.
- **Commit:** `feat(api): plans as monthly talk counts, grants, and a daily cap`.

### Task 2: Admin API (api)
- **Create** the `admin` module:
  - `AdminGuard`;
  - `AdminController` with:
    - `GET /api/admin/users?q=` (up to 50: id, email, name, kind, plan, plan ends, talks this month, last talk, unlimited);
    - `POST /api/admin/users/{id}/unlimited {on}`;
    - `POST /api/admin/users/{id}/grant {plan, days, note}`;
    - `GET /api/admin/usage?days=14` (per Dhaka day: talks, minutes spoken, new accounts, estimated cost; plus now: live talks, today/cap);
    - `GET /api/admin/settings`, `PUT /api/admin/settings {dailyTalkCap, plans[{plan, monthlyTalks, priceBdt, priceUsd}]}`.
  - `JdbcAdminReports` for the read-only queries.
  - `LearnerAdminUseCase` (learner port in: set unlimited; find by id).
- **Tests (AdminAcceptanceTest):**
  - `aNonAdminGets404`;
  - `aGuestWithTheAdminEmailGets404`;
  - `adminFindsUsersByEmail`;
  - `adminGivesAFriendPremium`;
  - `adminTurnsUnlimitedOn`;
  - `usageCountsTodaysTalks`;
  - `settingsChangeTheCapAndPlans`.
- **Commit:** `feat(api): the admin portal API`.

### Task 3: Web: /admin, the plan counter, "Bolo is resting"
- **Create:**
  - `web/src/app/admin/page.tsx`;
  - `web/src/admin/AdminApp.tsx` (tabs Users, Usage, Settings; plain tables; the existing tokens);
  - `admin.ts`.
- **Modify:**
  - `freeTalks.ts`: `counterLine` for a plan, e.g. "12 of 20 Premium talks left this month";
  - Me's plan row ("Premium · until 12 December");
  - `useTalk` refusal `daily-cap` → phase `resting`, a sheet over Home.
- **Tests:**
  - **Vitest:** `planCounterNamesThePlan`.
  - **e2e:**
    - a non-admin sees Not found;
    - the admin searches, gives Premium (30 days) and sees it in the list;
    - Settings saves the cap;
    - the daily-cap sheet;
    - 390 px admin users list fits.
- **Commit:** `feat(web): the admin portal, plan counter and Bolo is resting`.

### Task 4: Ship
- Compose `BOLO_ADMIN_EMAILS`.
- RUNBOOK "Admin portal and plans".
- `scripts/ship.sh`.
