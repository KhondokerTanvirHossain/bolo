# Hats and 10-minute talks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Learners pick Bolo's hat (Free talk, English teacher, Standup rehearsal, IELTS Speaking practice) and, on Premium/Ultimate, a 10-minute talk length; the Teacher teaches a better sentence mid-talk and judges the retry.

**Architecture:** Scenarios widen to `free|standup|teacher|ielts` (V33) with `learner_settings.talk_minutes`; the start applies plan gating for length. IELTS is a prompt branch with a Relaxed pace floor. The Teacher is a pure `Teaching` domain rule (gates + judging) plus a `TeacherCheck` LLM call (structured, 2.5 s timeout) wired into `TalkConversation.commit()` off the audio thread, and two socket messages (`teach`, `judged`). The web extends the picker, the talk screen (Say it better card) and labels.

**Tech Stack:** Spring Boot 4 / Java 21, Flyway V33, Next.js 15, Vitest, Playwright Firefox.

**Spec:** `docs/superpowers/specs/2026-11-16-hats-and-talk-length-design.md`. DS v45: ScreenScenarioPicker, DesktopScenarioPicker, ScreenTeacherTalk, ScreenIeltsTalk.

## Global Constraints

- Scenarios `free` (default) | `standup` | `teacher` | `ielts`. Length 5 | 10; 10 only for plans `premium`, `ultimate` and unlimited/no-plan learners; others 5. A 10-minute talk counts as one talk.
- Teacher gates: never learner turn 1; next teach only at least 3 learner turns after the last (teach, retry, one ordinary); at most 4 a talk; better version must pass `ReplyCheck.upgradeOk`; check timeout 2500 ms (`bolo.talk.teacher.timeout-ms`), model `bolo.talk.teacher.model` = `claude-haiku-5-5`.
- Judging: LCS of normalized words (lowercase, letters/apostrophes, consecutive repeats collapsed) over the better version's word count: ≥ 0.8 `nailed`, ≥ 0.5 `closer`, else `good_try`. Spoken lines: "Nailed it!", "Closer! Nice.", "Good try."
- Copy: "English teacher" / "Bolo shows a better way to say it, then you try again."; "IELTS Speaking practice" / "An examiner asks Parts 1, 2 and 3. Practice only, no band score."; teach line `A smoother way to say that: "<better>". Try saying it.`; "Talk length"; "10-minute talks come with Premium".
- Never: band score, "mistake/wrong/error/incorrect" in Teacher lines.
- Stage `api/src` only; leave the strays alone.

## Review Focus

1. A teach check that times out or fails → Bolo just replies (no silence) — test in Task 3.
2. A retry turn that is "Sorry?" / crisis → normal paths; the pending retry is dropped, not judged later — test in Task 3.
3. Basic / free learner sending `minutes: 10` → 5-minute talk — test in Task 1.
4. Pause during the teach check → nothing spoken until resume (like the crisis path) — covered by reuse of the `phase == PAUSED` guard; test in Task 3.
5. An old client without `talkMinutes` keeps the saved length — test in Task 1.

---

### Task 1: Scenarios teacher/ielts, talk length setting and plan gating
Files: `V33__hats.sql`, learner `Settings`/`SettingsService`/`SettingsController`/`JdbcSettingsAdapter`/`LearnerView.SettingsView`, `StartTalkUseCase.StartChoice(scenario, difficulty, Integer minutes)`, `TalkSessionService.start`, `CoachService` body `scenario` = the talk's, test `HatsAndLengthAcceptanceTest`.
- [ ] Tests: teacher and ielts are saved and copied; `talkMinutes` 10 saved; a Premium (granted) learner's talk is 10 minutes; a free learner's is 5 even with `{minutes:10}`; Basic is 5; an old client keeps `talkMinutes`; unknown minutes (7) refused `talk-minutes`; history row scenario `teacher`.
- [ ] Red → implement → green → full suite → commit.

### Task 2: IELTS examiner
Files: `BuddyPrompt` (ielts branch: examiner, Parts by minutes, no feedback, never a band), `TalkConversation` (pace floor `relaxed` for ielts), tests in `BuddyPromptTest`, `StandupTalkTest`-style `HatsTalkTest`.
- [ ] Tests: prompt contains "Part 2", "cue card", "never give a band score"; 5-minute version says Parts 1 and 2 only; the talk's opening request carries the examiner prompt.

### Task 3: English teacher
Files: `talk/domain/Teaching.java` (gates, `judge(said, better)`), `talk/application/service/TeacherCheck.java` (LLM, schema `{teach, better, why}`, stammer rule), `BuddyPrompt` (teacher branch, `judgedSystem(system, line)`), `TalkConversation` (teach / judge flow, `teach` and `judged` messages), analytics `teacher_taught`, `teacher_judged {result}`, FakeLlm teacher branch.
- [ ] Unit tests `TeachingTest` (gates, judge thresholds, repeats ignored), acceptance `HatsTalkTest`: turn 2 taught when the check says so (template spoken, `teach` message), turn 3 judged (`judged` nailed, next reply starts with the line), first turn never taught, a failing/slow check → ordinary reply, retry "Sorry?" → TLK-13 path and the retry dropped.

### Task 4: Web
Files: `scenario/*` (4 hats, length chips, plan gate), `HomeDashboard` line (`English teacher · 10 min`), `talkState` (`teach`, `judged`), `TalkScreen` (Say it better card), `TimeLeft`/labels, `SessionRow`, `PricingScreen` copy, `useTalk` start body `minutes`, e2e `hats.spec.ts`, vitest.
- [ ] Tests first (vitest for labels/state, e2e for picker, length gate and teach card), then implement.

### Task 5: Docs, review, ship
PRD (scenarios row, SCN-4/5 new, ONB-3/TLK-9 length note, analytics), RUNBOOK section, final review, ship.
