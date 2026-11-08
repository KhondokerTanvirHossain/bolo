# Audit, 9 November 2026: the build against the PRD, the prototypes and the design system

Two independent read-only audits of `p0-foundations` at b18c57d:
- `prd.md`: every PRD requirement ID, with evidence.
- `design.md`: every screen in the design system and the desktop prototype. Each was rendered at 1280, 900 and 390 px.

Both files hold the full tables with file:line evidence. This page is the combined gap list.

**Already fixed the same night (1cbff19 and the icon fix after it):**
- the public /privacy page couldn't scroll (design P0-1);
- the debrief had no way out on phones (design P1-1);
- a failed "Delete recording" closed silently (design P1-9);
- "My patterns" showed before 3 talks (PRD P2-18 / C17).

## Needs a founder decision (blocks a credible closed beta)

| # | What | Why it matters | Options |
| --- | --- | --- | --- |
| D1 | **Speech capacity:** Azure F0 allows one live talk at a time (`max-live-sessions: 1`) | A second learner is turned away. F0's free hours won't cover 50–100 learners. | Move to Azure S0 (paid), raise the cap, load-test. |
| D2 | **Crisis wording and helplines** (NFR-21) | Approved for the pilot only. The PRD needs a qualified local partner before the beta. | Choose and contact a partner. |
| D3 | **Sound-fix accuracy (Gate 1)** is unproven on Bangla-accented speech | Strangers will see sound fixes in most debriefs. | (a) teacher labels in about 5 days, then decide; (b) hide sound fixes in the debrief until then, keeping practice. |
| D4 | **Vendor no-training terms** (NFR-15) for Azure, Anthropic and Firebase | A must-pass in the architecture. Nobody has checked or recorded them. | Confirm the terms, then add a "never used to train AI" line to the privacy page. |
| D5 | **Orphaned guest data** | A guest who loses their cookie leaves transcripts on the server forever and can't delete them. | Purge guests idle for N days (90 suggested). |
| D6 | **Day 1 clip retention** | Kept until "Delete everything", even after Keep is turned off. The PRD default is 90 days. | Keep the exception (it's disclosed), or apply 90 days and delete on opt-out. |
| D7 | **Reminders** (PRG-5/6/7) | Missing entirely. Day-7 retention is a Gate 2 metric. | Confirm they are not part of feedback-log item 5 (push/gamification, "not yet"). |
| D8 | **Standup Rehearsal** (SCN-1/2) | The primary persona's feared moment. | In the beta, or after. |
| D9 | **Bolo's reply time on the Skills card** ("Bolo answered in about 1.4 s") | Useful to you while testing, but it's debug output to a learner. | Hide it, or show it only to you. |

## Engineering gaps, no decision needed (in suggested order)

**P0 (ops)**
- Nightly database backup to S3, plus error alerting. The RUNBOOK already says "before the closed beta".
- A red-team pass on Bolo's prompts, plus a "friend, never romantic" rule (TLK-14, US-P4).

**P1 (core loop)**
1. Day 7 voice comparison (PRG-2). The Day 1 screen already promises it, and the first cohort reaches Day 7 a week after it starts.
2. Session rating (DBF-8), so satisfaction can be measured.
3. Dialogs: trap focus, close on Esc, return focus (Delete everything, Delete recording, Guest limit, Save progress).
4. Small design drift:
   - the end sheet should say "End and get feedback?";
   - Practice has two filled buttons;
   - Home has no way to reach Progress.
5. Skill arrows against the last session (DBF-5).
6. Reconnect: a closed tab or a dropped socket ends the talk; there's no "Continue your chat" (TLK-10).
7. Failed debrief: a retry later and "Notify me when ready" (US-3.7).
8. Mic-check verdict: noisy audio should mean "some feedback was skipped" (US-1.5).
9. "More time to answer" and "I stammer" (AX-7/8).
10. Disputes: keep the clip for review, and suppress the issue for that word (DBF-4).
11. Drills beyond reading sentences: minimal pairs, shadowing, say it better (DRL-1…7). Size L.
12. Gate 2 metric queries: Day-7 retention, dispute rate, loop completion.
13. Delete everything should also remove the Firebase user.

**Needs art:** presence (the companion's states, ambient sound, choosing a companion), PRS-1/3/4/6/7.

**Deliberately not built (feedback log):** story memory (TLK-11), restating Bangla (item 3), gamification and push (item 5). The accent and persona discussion is still open.
