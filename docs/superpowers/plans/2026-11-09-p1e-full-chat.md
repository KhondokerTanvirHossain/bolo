# P1e See Full Chat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After a talk's feedback, and from History, the learner can read the whole conversation as chat bubbles (DBF-6, US-4.10). The spots the Coach fixed are underlined, and tapping one opens that fix's card.

**Architecture:**
- **coaching module:**
  - New debriefs store each fix's learner `turn`: sound fixes from the scored word's turn, upgrades from the Coach's turn.
  - `GET /api/talk/{id}/chat` returns the talk's turns (from `TalkQueryUseCase`) and the marks: which turn, which words, and which fix.
  - Older debriefs have no `turn`, so their marks are found by searching the learner's turns for the fix's words.
- **web:**
  - A `ChatScreen` with Bolo's lines on the left, yours on the right, and marks underlined.
  - It opens from a quiet "See full chat" link in the debrief's card list, live and in History.

**Tech Stack:** Spring Boot 4 / Java 21, Jackson 3, JdbcClient; Next.js 15, Vitest, Playwright.

**Spec:**
- `Bolo-PRD-MVP.md` DBF-6 ("Let the learner view the full transcript of the session, with fixed spots highlighted").
- US-4.10 ("tap 'See full chat' … the conversation as chat bubbles, with the spots linked to fixes underlined. Tapping a bubble plays its audio").
- The founder (2026-11-09): "must save conversations … show people the conversations". The live talk screen stays calm (TLK-16); there's no live chat.

## Global Constraints
- **No audio in the chat.** Ruled: full talk audio is deleted once feedback is ready (privacy and disk). The fix cards keep their short clips; a mark leads to its card, where the clip plays.
- **Speakers:**
  - Bolo's lines read "Bolo", left, on the plain surface.
  - The learner's lines read "You", right, on `--hills-soft`.
- **Marks:**
  - A sound fix underlines its word (`saidHighlight`).
  - An upgrade underlines the words the learner said (`said`).
  - A habit fix marks nothing.
  - The underline is 2 px in the Coach's violet. A mark is a button whose label is `"<words>": <fix title>`.
- **Only the owner** gets the chat; anyone else gets 404. A talk with no debrief, or one still scoring, also gets 404 (the chat belongs to the feedback).
- **A deleted recording keeps the chat:** "Feedback and words are still here."
- **Phones (390 px):** bubbles at most 85% wide, no sideways scroll.

## Review Focus
1. The same words said twice: only the turn the fix came from is marked; for an old debrief, the first match. Test: Task 1 `aRepeatedWordIsMarkedOnceInItsTurn`.
2. Marks overlapping in one turn (a sound word inside an upgrade phrase): both shown, never broken text. Test: Task 2 Vitest `overlappingMarksKeepTheText`.
3. Text with HTML characters (`<`, `&`) shows as text. Test: Task 2 Vitest `textIsNeverHtml`.
4. A crisis-ended talk has no debrief and no chat (404). Test: Task 1 `noDebriefNoChat`.
5. Very long talks (10 minutes, about 60 turns) scroll inside the page, and the page header stays. Test: Task 2 e2e at 390 px.

---

### Task 1: The chat (api)
- **Modify** `CoachService.soundFix` / `upgradeFix`: put `turn` (int).
- **Create** `GET /api/talk/{id}/chat` in `DebriefController`, through `DebriefUseCase.chat(LearnerId, TalkSessionId) -> Optional<String>` (JSON):
  ```json
  {"topic": "...",
   "turns": [{"speaker": "bolo|learner", "text": "..."}],
   "marks": [{"turn": 3, "start": 10, "end": 14, "fix": 1, "title": "The v sound"}]}
  ```
  - `turn` is the index into `turns`.
  - `start` and `end` are character offsets in that turn's text.
  - `fix` is the fix's position in the debrief (1-based, as the card list numbers them).
  - Empty turns are left out.
- **Tests (CoachAcceptanceTest):**
  - `theChatHasEveryTurnInOrder`;
  - `fixedSpotsAreMarked`;
  - `aRepeatedWordIsMarkedOnceInItsTurn`;
  - `anOldDebriefFindsItsSpotsByText`;
  - `anotherLearnerGets404`;
  - `noDebriefNoChat`.
- **Commit:** `feat(api): the full chat of a talk, with the fixed spots`.

### Task 2: Web: See full chat
- **Create:**
  - `web/src/coach/chat.ts`: `fetchChat`, and `segments(text, marks)` → plain and marked runs.
  - `ChatScreen.tsx`: header "Your talk" plus the topic, a Back button, and the bubbles. A mark is a button that calls `onOpenFix(n)`.
- **Modify** `CoachFlow`: a quiet "See full chat" in the list footer, shown when the debrief is ready. The chat replaces the pane; a mark returns to card n.
- **Tests:**
  - **Vitest:**
    - `segmentsSplitAtMarks`;
    - `overlappingMarksKeepTheText`;
    - `textIsNeverHtml`.
  - **e2e:**
    - from History, See full chat shows both speakers;
    - a mark opens its fix card;
    - 390 px with no sideways scroll.
- **Commit:** `feat(web): see the full chat of a talk`.

### Task 3: Ship
- Full suites, push, CI (deploy only if green), deploy, smoke, then the 4 live probes.
- RUNBOOK "Full chat".
- Commit `docs: full chat`.
