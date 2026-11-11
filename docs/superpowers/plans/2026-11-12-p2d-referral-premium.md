# P2d Invite 3 Friends, Get a Month of Premium Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** the founder's go-public must-have, "if they refer they get premium". A learner whose 3 invited friends each finish a first talk gets 30 days of Premium. This follows PRD ACC-8 / US-8.10, except the founder chose **Premium** where the PRD says Basic.

**Architecture:**
- **billing module:**
  - `referral_code` (one per account) and `referral` (referrer, friend, joined_at), both in V21.
  - The code travels like the invite code: `?ref=CODE` is kept for the browser session and sent with Google sign-in.
  - Sign-in records a referral only when it **creates a new account**.
- **When a friend counts:** the friend has at least one counted talk (the free plan's rule). This is checked lazily when the referrer opens Invite or Home. No events are needed.
- **The reward:** each full set of 3 counted friends grants Premium for 30 days (`source = referral`), under a per-learner advisory lock, so it is granted once.
- **Grant semantics, settled here** (deferred from the P2a review): a grant of the **same plan** extends from the latest end of that learner's active same-plan subscription; a different plan overlaps (the bigger one wins, as now).
- **web:**
  - Me → "Invite friends" (`InviteScreen` in the shell);
  - FreeLimit's "Invite 3 friends, get a free month";
  - a one-time Home note "A month of Premium is yours".

**Spec:** PRD ACC-8, US-8.7 (free-limit sheet), US-8.10; memory bolo-go-public (Premium, not Basic).

## Global Constraints
- **Offer window:** Bolo's first year only, until the end of `bolo.referral.until` (default `2027-11-30`). After that the offer appears nowhere, and friends who joined before still count.
- **Who counts as a friend:**
  - a Google account **created** by a sign-in that carried a valid ref code;
  - not the referrer themself;
  - not someone whose `free_talk_use` key already existed (an account that was deleted and made again);
  - each friend counts once, for one referrer.
- **Reward:**
  - every 3 counted friends gives 30 days of Premium;
  - at most **3 rewards per learner** (9 friends);
  - the grant note is "referral".
- **Invite screen copy:**
  - title: "Invite 3 friends, get a month of Premium";
  - 3 slots, filled by the first names of friends who finished a talk ("Waiting for a friend" when empty); friends who joined but haven't talked are not named;
  - the link with **Copy**, and "Share invite link" (Web Share API; without it, the link is copied and the screen says "Link copied");
  - share text: "I'm practising speaking English with Bolo. Try it: {link}". It is neutral, never mentions a level, and the learner chooses who gets it.
  - After a reward: "A month of Premium is yours" with its end date. After 3 rewards: "You've had all 3 free months. Thank you for sharing Bolo."
- **Link:** `https://bolo.eliooo.org/?ref=CODE`. The code is 8 characters from `[a-z0-9]`, made on first request.
- **FreeLimit sheet:** adds "Invite 3 friends, get a free month" while the offer runs.
- **Home:** shows "A month of Premium is yours. Until {date}." once, after a reward.
- **Privacy:** Bolo never reads contacts or sends anything itself. The privacy page adds: "If you invite friends: who joined through your link."

## Review Focus
1. A friend who signs into an **existing** account, or uses their own link, never counts. Test: Task 1.
2. Two Home loads at the same moment grant one reward, not two (advisory lock). Test: Task 1.
3. A second Premium grant extends from the first's end; it doesn't stack inside the same days. Test: Task 1.
4. After the offer ends, `GET /api/referral` says `offer:false` and the FreeLimit line is gone, but rewards already earned still arrive. Test: Tasks 1 and 2.
5. Share falls back to copying when `navigator.share` is missing or is cancelled; Copy works when clipboard permission is denied (select the text instead). Test: Task 2.

---

### Task 1: Referrals and grant extension (api)
- **Create:**
  - `V21__referral.sql`;
  - billing `ReferralUseCase`:
    - `code(learner)`;
    - `recordNewAccount(friend, refCode)`;
    - `status(learner)` → `{offer, link, friends:[firstName], counted, rewards, premiumUntil, newReward}`;
    - `seen(learner)`;
  - a talk port in, `CountedTalksUseCase.hasCountedTalk(learner)`;
  - endpoints `GET /api/referral` and `POST /api/referral/seen`.
- **Modify:**
  - `SignInService` (pass `ref`; call `recordNewAccount` only when an account was created);
  - `PlansService.grant` (same-plan extension);
  - the Home view (`newReward`).
- **Tests (ReferralAcceptanceTest):**
  - `threeFriendsWhoTalkGiveAMonthOfPremium`;
  - `aFriendWhoHasntTalkedDoesntCount`;
  - `signingIntoAnExistingAccountDoesntCount`;
  - `yourOwnLinkDoesntCount`;
  - `aRecreatedDeletedAccountDoesntCount`;
  - `twoLoadsAtOnceGrantOnce`;
  - `atMostThreeRewards`;
  - `afterTheOfferEndsNoOfferButEarnedRewardsArrive`.
- **Tests (FreePlanAcceptanceTest):** `aSecondPremiumGrantExtendsTheFirst`.
- **Commit:** `feat(api): invite 3 friends, get a month of Premium`.

### Task 2: Web — Invite screen, FreeLimit line, Home note
- **Create:**
  - `web/src/referral/InviteScreen.tsx`;
  - `referral.ts`: `takeRefFromUrl`, `currentRef`, `fetchReferral`, `shareLink`.
- **Modify:**
  - `session.signInWithGoogle` (send `ref`);
  - MeScreen ("Invite friends" row);
  - FreeLimit;
  - HomeDashboard (the note);
  - the privacy page.
- **Tests:**
  - **Vitest:**
    - `takeRefFromUrlKeepsAValidCode`;
    - `shareFallsBackToCopy`.
  - **e2e:**
    - Me → Invite friends shows the title, 3 slots, Copy and Share;
    - a filled slot shows a first name;
    - FreeLimit shows the invite line while the offer runs;
    - the Home note shows once;
    - `?ref=` is sent with sign-in;
    - fits at 390 px.
- **Commit:** `feat(web): invite friends, get a month of Premium`.

### Task 3: Ship
- RUNBOOK section "Invite friends".
- Smoke check: `GET /api/referral` with no cookie returns 401.
- `scripts/ship.sh`.
