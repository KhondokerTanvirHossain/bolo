# P2e bKash Payments, Option A (Manual Send Money + TrxID) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal (founder, 2026-11-11, "Option A for now"):** learners in Bangladesh pay for a plan by bKash **Send Money** to the founder's personal number and type in the TrxID. The founder approves it in the admin portal's **Payments** tab, and the plan runs 30 days. Auto-verification through the founder's own SMS forwarder comes later (P2f).

**Architecture:**
- **billing module:**
  - `payment` table (V22);
  - `PaymentUseCase.submit / mine / pending / approve / reject`;
  - approval grants the plan through `PlansUseCase.grant(learner, plan, 30, "bkash", trxId)`, which chains same-plan months.
- **The bKash number** is an admin setting (`app_setting bkash_number`). It is never in code. Until the founder sets it, the ৳ pricing keeps the P2c waitlist.
- **web:**
  - the ৳ pricing button opens `BkashSheet`: steps, number, amount, TrxID and payer number, then Submit;
  - the payment status on /pricing and on Me's plan row;
  - the admin **Payments** tab: Approve, or Reject with a reason;
  - admin Settings gets a "bKash number" field.

**Spec:**
- memory bolo-go-public (Option A);
- PRD US-8.8 (payment sheet: renewal and cancel lines before paying; a calm failure line);
- the Refund policy page (monthly, no automatic renewal, refunds to the paying bKash number);
- design system ScreenPlanPay.

## Global Constraints
- **The bKash number:** 11 digits matching `01[3-9]\d{8}`, set in admin Settings. Empty means bKash is off (the waitlist as in P2c).
- **The sheet (৳, account, bKash on):** title "Pay with bKash". The steps are numbered:
  1. "Open bKash and choose **Send Money**."
  2. "Send **৳{price}** to **{number}**" (with a Copy button for the number).
  3. "Type the Transaction ID (TrxID) from bKash's message, and the bKash number you paid from."
- **Fields:**
  - TrxID: letters and digits only, 8–12 characters, uppercased on the server;
  - payer number: `01[3-9]\d{8}`.
- **Before paying, the sheet says:** "Basic for 30 days from approval. It doesn't renew by itself. Refunds: see the refund policy." (with a link).
- **After submitting:** "Thanks. We're checking your payment, usually within a few hours. Your plan starts when it's confirmed."
- **Status:** shown on /pricing and on Me's plan row.
  - Pending: "Payment being checked (TrxID {id})".
  - Rejected: "We couldn't confirm your payment ({reason}). Check the TrxID and try again, or report a problem." Shown once on Me, and on /pricing until a new submission.
  - Approved: the plan appears as usual ("Premium plan · until …").
- **Rules:**
  - one **pending** payment per learner at a time (a second gets 422 `payment-pending`);
  - a TrxID is used once, ever (422 `trx-used`);
  - the amount is the plan's ৳ price at submit time;
  - at most 5 submissions a day per learner (429).
- **Admin Payments tab:**
  - pending first: when, email, plan, amount, TrxID, payer number; **Approve**, and **Reject** with reasons "TrxID not found", "Amount doesn't match" or "Other";
  - below that, the last 50 decided payments.
  - Approve and Reject only act on a pending payment. Two clicks at once grant once (conditional update).
- **Privacy:** "If you pay by bKash: the TrxID, the number you paid from, the amount and the plan." Delete everything removes them.
- **Logs:** ids only, never the payer number.

## Review Focus
1. Two Approve clicks at once grant one month (`update … where status = 'pending'` returns 1 only once). Test: Task 1.
2. A TrxID used by another learner, or already rejected, can't be submitted again. Test: Task 1.
3. A guest can't pay (422 sign-in); with no number set, `POST /api/payments` returns 422 `bkash-off`. Test: Task 1.
4. An approved payment for a plan the learner already holds starts when that plan ends (chained grant). Test: Task 1.
5. The sheet fits at 390 px, keeps focus, and Esc closes it; numbers are typed with `inputmode="numeric"`. Test: Task 2.

---

### Task 1: Payments (api)
- **Create:**
  - `V22__payment.sql`: `payment(id bigserial, learner_id → learner on delete cascade, plan, amount_bdt, trx_id unique, payer_number, status pending|approved|rejected, reason, created_at, decided_at, decided_by)`;
  - billing `PaymentUseCase`, port, JDBC adapter, `PaymentController`:
    - `POST /api/payments {plan, trxId, payerNumber}`;
    - `GET /api/payments/me` → latest `{status, plan, trxId, reason}` or 204;
    - `GET /api/payments/bkash` → `{number}` or 204 (public);
  - admin:
    - `GET /api/admin/payments`;
    - `POST /api/admin/payments/{id}/approve`;
    - `POST /api/admin/payments/{id}/reject {reason}`;
    - the Settings `bkashNumber`.
- **Tests (PaymentAcceptanceTest):**
  - `aLearnerPaysAndTheAdminApproves` (the plan is active for 30 days);
  - `rejectShowsTheReason`;
  - `oneTrxIdOnce`;
  - `onePendingAtATime`;
  - `badTrxIdOrNumberIs422`;
  - `aGuestCantPay`;
  - `bkashOffWithoutANumber`;
  - `twoApprovesGrantOnce`;
  - `approvingTheSamePlanChains`;
  - `onlyTheAdminDecides`;
  - `sixInADayIs429`.
- **Commit:** `feat(api): bKash payments, checked by hand`.

### Task 2: Web — bKash sheet, status, admin Payments
- **Create:** `web/src/plan/BkashSheet.tsx`; `payments.ts`.
- **Modify:**
  - PricingScreen (৳ with bKash on: "Pay with bKash · ৳300" opens the sheet; status line);
  - MeScreen's plan row (status);
  - AdminApp (Payments tab; the Settings bKash number);
  - the privacy page.
- **Tests:**
  - **Vitest:** `trxIdAndNumberChecks`.
  - **e2e:**
    - the sheet shows the number and amount, Copy copies, and Submit sends and thanks;
    - pending shows on /pricing and Me;
    - a rejection shows its reason;
    - bKash off keeps the waitlist;
    - the admin approves, and the row moves to decided;
    - Esc closes it, and it fits at 390 px.
- **Commit:** `feat(web): pay with bKash`.

### Task 3: Ship
- RUNBOOK section "bKash payments (manual)", including the founder's daily routine: open /admin → Payments, check each TrxID in the bKash app's statement, then Approve or Reject.
- `scripts/ship.sh`.
