# P2b Help, Terms and Refund Policy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal (founder: "what happens if people get stuck", and the go-public minimum):**
- A learner who is stuck can **Report a problem** from anywhere in the app shell and from Me → Help. The report reaches the admin portal's new **Help** tab with its context.
- Public **Terms of Use** and **Refund policy** pages exist (a payment gateway asks for them), linked from the sign-in sheet, Privacy and Me.

**Architecture:**
- **help**, a new small module:
  - table `help_request` (V18);
  - `POST /api/help` (signed in or guest, at most 5 an hour per learner);
  - `GET /api/admin/help` and `POST /api/admin/help/{id}/done`, through the admin module (admin read model plus a port in).
- **web:**
  - `HelpDialog` (DialogCard, textarea, Send);
  - a sidebar foot "Need help?" card and a Me "Help" row;
  - the admin portal's Help tab;
  - `/terms` and `/refund` pages.

**Spec:** memory bolo-go-public; design system 1-voice-and-copy (plain words); PRD R7 (never a dead end).

## Global Constraints
- **Report a problem:**
  - one textarea, "What went wrong?", 1–1000 characters;
  - automatic context, shown to the learner as "We'll include: the screen you're on and your last talk": the view name, the last talk id, the browser (user agent), and the time;
  - no audio, and no transcript text.
  - Afterwards: "Thanks. We read every one." and the dialog closes.
  - 6 reports in an hour → 429, with the dialog saying "That's a lot of reports. Please try again in a while."
- **Admin Help tab:** open reports newest first (email or "guest", message, context, time) with **Done**; done ones are hidden.
- **Terms of Use (plain words):**
  - who can use Bolo (13+; under 18 with a parent's okay);
  - what Bolo is (an English speaking coach; not therapy, medical, legal or immigration advice);
  - fair use: one account per person, no abuse, no attempts to get around the limits;
  - free and paid plans: talk counts, which can change with notice;
  - Bolo can make mistakes about pronunciation or grammar;
  - your content and privacy (link);
  - stopping: delete everything at any time;
  - contact: k.tanvir.hossain@gmail.com.
  - Dated 11 November 2026.
- **Refund policy:**
  - paid plans are monthly, with no automatic renewal while payments are manual (bKash);
  - a full refund within 3 days of paying if you've used at most 1 talk of the paid month;
  - after that, no refunds for the month already started;
  - a technical problem on our side gets extra talks or a refund;
  - refunds go back to the bKash number you paid from, within 7 days;
  - to ask, use the contact email or Report a problem.
- **Links:**
  - the sign-in sheet shows a footnote: "By continuing you agree to the Terms and the Privacy page.";
  - Me has Terms and Privacy links;
  - /privacy links /terms and /refund.

## Review Focus
1. A guest can report (its learner id is kept); a signed-out visitor (no cookie) gets 401. Test: Task 1.
2. A message of over 1000 characters or empty → 422. Test: Task 1.
3. Context is limited to the known keys, with values capped at 200 characters; anything else is dropped. Test: Task 1.
4. A non-admin can't list or close reports (404). Test: Task 1.
5. The help dialog keeps focus, Esc closes it, and it fits at 390 px. Test: Task 2.

---

### Task 1: Help (api)
- **Create:**
  - `V18__help.sql`;
  - the `help` module (`HelpUseCase.report(learner, message, context)`, `open()`, `done(id)`; JDBC; `HelpController POST /api/help`);
  - admin endpoints `GET /api/admin/help` and `POST /api/admin/help/{id}/done`.
- **Tests (HelpAcceptanceTest):**
  - `aLearnerReportsAProblem`;
  - `aGuestCanReport`;
  - `signedOutIs401`;
  - `emptyOrTooLongIs422`;
  - `onlyKnownContextIsKept`;
  - `sixInAnHourIs429`;
  - `onlyTheAdminSeesAndClosesReports`.
- **Commit:** `feat(api): report a problem, and the admin's help inbox`.

### Task 2: Web — Help, Terms, Refund
- **Create:**
  - `web/src/help/HelpDialog.tsx`;
  - `help.ts`;
  - `web/src/app/terms/page.tsx`;
  - `web/src/app/refund/page.tsx`;
  - the admin Help tab.
- **Modify:**
  - SideCards ("Need help? Report a problem");
  - MeScreen (Help row, Terms and Privacy links);
  - GuestLimit (the terms footnote);
  - the privacy page (links).
- **Tests:**
  - **e2e:**
    - Report a problem sends the message with its context and thanks;
    - 429 shows the message;
    - Esc closes it, and it fits at 390 px;
    - /terms and /refund scroll to their last section and show the contact;
    - the admin Help tab lists a report and Done removes it;
    - the sign-in sheet links the Terms.
- **Commit:** `feat(web): help, terms and refund policy`.

### Task 3: Ship
- RUNBOOK "Help, Terms and Refund policy".
- `scripts/ship.sh`.
