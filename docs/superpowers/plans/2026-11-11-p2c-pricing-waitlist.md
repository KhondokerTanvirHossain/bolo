# P2c Pricing Page, Waitlist and Country Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal (founder, 2026-11-11):** a live pricing page, priced by country: ৳ in Bangladesh, $ elsewhere. Card payments abroad aren't possible yet, so people join a waitlist, and the founder sees it in the admin portal. bKash payments come in P2e; until then, Bangladesh also joins the list.

**Architecture:**
- A new `billing` module. It owns the waitlist now and bKash payments in P2e.
  - `waitlist` table (V20, one row per learner, the latest choice wins).
  - `GET /api/plans` is public and reads `PlansUseCase.plans()` through the talk port in.
  - `POST /api/waitlist` and `GET /api/waitlist/me` are for accounts only.
  - `GET /api/admin/waitlist` goes through the admin module.
  - On `LearnerDeleting` the row is deleted; on `LearnerMerged` it moves.
- **web:**
  - a `/pricing` route (`PricingScreen`), usable signed out;
  - the region guessed from the browser's time zone, with a visible switch;
  - "See plans" links from FreeLimit, Me's plan row and SideCards;
  - an admin **Waitlist** tab.

**Spec:**
- PRD US-8.7 ("See plans" on the free-limit sheet) and US-8.8 (plan screen copy and rules);
- the design system's ScreenPlans and DesktopPlans;
- memory bolo-go-public.

## Global Constraints
- **Plans shown:**
  - Free: "7 talks in your first week, then 2 a month", ৳0 / $0, marked "Your plan" for free learners;
  - then Basic, Premium and Ultimate from `GET /api/plans`: "{n} talks a month", and "৳300 a month" or "$3 a month".
  - Prices in whole numbers when they are whole; otherwise two decimals.
  - No "Unlimited", no "Most popular", no crossed-out prices, no timers.
  - **Basic is preselected**, never the most expensive.
- **Copy:**
  - Title: "Choose a plan".
  - Subtitle: "Each talk is up to 5 minutes, then your coach's feedback."
  - Under the plans: "Every plan has every feature: talks, your coach's feedback, practice and your full history."
- **Region:**
  - `bd` when `Intl…timeZone === "Asia/Dhaka"`, else `intl`.
  - A link switches it: "Not in Bangladesh? See prices in $" / "In Bangladesh? See prices in ৳". The choice is kept in localStorage `bolo.region`.
- **Main button:**
  - `intl`: "Join the waitlist for Basic", with the line "Card payments are coming soon. Join the waitlist and we'll email you when they open."
  - `bd`: "Join the list for Basic · ৳300 a month", with the line "bKash payments open in a few days. Join the list and we'll email you first." (P2e replaces this with the bKash flow.)
- **Joining:**
  - An account sends `{plan, region, timeZone}`. The page then says "You're on the list for Basic. We'll email you at {email}." and the button becomes "Change to Premium" if another plan is picked.
  - A guest or signed-out visitor sees the same button with the footnote "Sign in with Google to join the list."; pressing it signs in with Google (the existing sign-in), then joins.
  - If sign-in is off or fails, the page says "Sign-in didn't work. Try again." and keeps the choice.
- **API:**
  - `POST /api/waitlist`: plan ∈ basic|premium|ultimate, region ∈ bd|intl, timeZone ≤ 64 characters; otherwise 422 `bad-waitlist`.
  - A guest gets 422 `{code:"sign-in"}` (the existing refusal); no cookie gets 401.
  - No write limit: one upserted row per learner, nothing to protect.
- **Admin Waitlist tab:**
  - counts by plan and region ("Basic: 4 (৳ 3, $ 1)");
  - entries newest first: email, plan, region, time zone, when.
- **Privacy:** "Delete everything" removes the waitlist row. The privacy page's list of what we keep gains "your plan waitlist choice".

## Review Focus
1. A visitor with no cookie can read `GET /api/plans` (200) and the page renders; a write still needs the X-Bolo header. Test: Task 1, plus the Task 2 e2e.
2. Joining twice keeps one row (the latest plan); merging a guest into an account keeps the account's choice. Test: Task 1.
3. Delete everything removes the row. Test: Task 1.
4. The region switch survives a reload, and the time zone fallback never throws (`Intl` missing → `intl`). Test: Task 2 (Vitest).
5. At 390 px the four plans and the button fit with no sideways scroll; keyboard: the plans are a radio group (arrow keys), and Enter on the button joins. Test: Task 2 (e2e).

---

### Task 1: Waitlist and public plans (api)
- **Create:**
  - `V20__waitlist.sql`: `waitlist(learner_id uuid primary key references learner on delete cascade, plan text not null, region text not null, time_zone text, created_at timestamptz not null, updated_at timestamptz not null)`;
  - the `billing` module: `WaitlistUseCase.join(learner, plan, region, timeZone)`, `mine(learner)`, `all()`; JDBC adapter; `BillingController` (`GET /api/plans`, `POST /api/waitlist`, `GET /api/waitlist/me` → 200 `{plan, region}` or 204);
  - a `LearnerDeleting` / `LearnerMerged` listener;
  - admin `GET /api/admin/waitlist` (`{counts:[{plan, bd, intl}], entries:[{email, plan, region, timeZone, at}]}`).
- **Tests (WaitlistAcceptanceTest):**
  - `plansArePublic` (no cookie → 200 with 3 plans and their prices);
  - `anAccountJoinsAndChangesPlan` (one row, the latest plan);
  - `aGuestIsAskedToSignIn` (422 sign-in);
  - `badPlanOrRegionIs422`;
  - `deleteEverythingRemovesTheRow`;
  - `onlyTheAdminSeesTheWaitlist` (404 for others; counts and entries for the admin).
- **Commit:** `feat(api): public plans and the waitlist`.

### Task 2: Web — /pricing, links, admin Waitlist tab
- **Create:**
  - `web/src/app/pricing/page.tsx`;
  - `web/src/plan/PricingScreen.tsx`;
  - `web/src/plan/pricing.ts`: `regionGuess(): "bd"|"intl"`, `priceLabel(plan, region)`, `fetchPlans`, `joinWaitlist`, `myWaitlist`.
- **Modify:**
  - `FreeLimit` (adds "See plans", a link to `/pricing`, above "Wait for …");
  - MeScreen's plan row ("See plans");
  - SideCards' plan card ("See plans");
  - `AdminApp` (Waitlist tab);
  - the privacy page line.
- **Tests:**
  - **Vitest:**
    - `regionGuessReadsTheTimeZone` (Dhaka → bd; London → intl; throws → intl);
    - `priceLabelUsesTakaOrDollars` ("৳300 a month", "$3 a month", "$4.50 a month").
  - **e2e:**
    - a signed-out visitor sees four plans with Basic preselected and ৳ (time zone Asia/Dhaka) or $ (Europe/London);
    - the switch flips the currency and survives a reload;
    - an account joins Premium and sees "You're on the list for Premium. We'll email you at …";
    - a guest sees "Sign in with Google to join";
    - FreeLimit's "See plans" opens /pricing;
    - the admin Waitlist tab shows counts and an entry;
    - fits at 390 px; the arrow keys move between plans.
- **Commit:** `feat(web): the pricing page and waitlist`.

### Task 3: Ship
- RUNBOOK section "Pricing and waitlist".
- `scripts/ship.sh`.
