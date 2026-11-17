# Founder feedback 2026-11-18 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** The teacher teaches more (word/grammar/phrase/idiom, opener "anything you'd like to learn today?"); the free
talk opens with a three-way choice; the Coach adds Grammar and Vocabulary cards; Bolo understands Bangla turns and shows
them in Bangla script.

**Architecture:** Pure domain rules (`Teaching` caps by minutes, `OpeningChoice`, `DebriefPlan` 5 cards,
`BanglaGuess`) with thin service wiring; one new speech port `BanglaRecognitionPort` (Azure REST bn-IN; fake for tests).

**Tech Stack:** Spring Boot 4 / Java 21, Next.js 15, Vitest, Playwright Firefox.

**Spec:** `docs/superpowers/specs/2026-11-18-founder-feedback-design.md`. DS v46: ScreenTeacherTalk (kind label),
DebriefGrammarCard, DebriefVocabularyCard, ScreenBanglaTurn.

## Global Constraints

- Teach: gap 2, max 6 (5 min) / 10 (10 min), never turn 1; kinds grammar|word|phrase|idiom; lines per spec §1.
- Opener: exactly three options; fixed per talk (seed = talk id); free talk only.
- Debrief: at most 5 cards; Grammar ≤ 1, Vocabulary ≤ 1; Say it better 2, or 1 when Grammar or Vocabulary shows.
- Bangla: English confidence < 0.5 or wordless speech ≥ 800 ms → bn-IN REST (≤ 60 s audio, 3 s timeout); accept when
  Bangla script and confidence ≥ 0.5. Bolo always replies in English. Bangla turns never scored.
- Never "mistake/wrong/error/incorrect" in learner copy. Stage `api/src` only; leave the strays alone.

## Review Focus

1. A slow or failing Bangla recognition → the turn goes on as today (English text or the unclear nudge), never silence.
2. A confident English turn never calls the Bangla recogniser (no misdetection, no added delay).
3. A pause, crisis or end during the Bangla check → nothing late is spoken (same guards as the teacher check).
4. A Bangla turn's English-recogniser words never reach pronunciation scoring or the Coach's sound fixes.
5. Old debrief bodies without grammar/vocabulary still render; the Coach reply without them still validates.

---

### Task 1: The teacher teaches more
Files: `Teaching` (max by minutes, GAP 2, `line(kind, better)` with end punctuation, `Kind`), `TeacherCheck` (kind in
schema, prompt widened, `wish` passed), `BuddyPrompt.teacher` opener, `TalkRelayService` (teacher withOpener false),
`TalkConversation` (wish = first learner turn; `teach` message carries `kind`), web talkState/TalkScreen card label.
- [ ] Tests: TeachingTest (gap 2, caps 6/10, kind lines, punctuation), TeacherCheck parse kind (unknown → grammar),
  TeacherTalkTest (teach after the try, `kind` in message, wish in the check request), BuddyPromptTest (opener line),
  vitest talkState kind.

### Task 2: The opener offers a choice
Files: `memory/domain/OpeningChoice` (last/other/curious from note + `TopicBank.CURIOUS`), `MemoryPrompt.brief`
choice line, `MemoryService.brief` (Random seeded by talk id).
- [ ] Tests: OpeningChoiceTest (thread first, else newest recent; other ≠ last; no history → empty; same seed same
  choice), MemoryPromptTest choice line, MemoryService acceptance (brief carries the offer, followUp true).

### Task 3: Coach Grammar and Vocabulary cards
Files: `CoachPrompt` (grammar, vocabulary in schema/prompt), `CoachReply`, `FixChoice.Grammar/Vocabulary`,
`DebriefPlan.choose(..., grammar, vocabulary, standupTip)`, `AssessmentService.plan`, `CoachService` (validate, fixes
`grammar` / `vocabulary`), web `debrief.ts`, `CoachFlow` cards, e2e.
- [ ] Tests: DebriefPlanTest (5 max, upgrades 1 when grammar/vocab), acceptance (cards appear with FakeLlm reply; an
  invalid quote is dropped; old reply still works), vitest/e2e card rendering.

### Task 4: Bangla
Files: `ScoredSegment.confidence`, `AzureScoreParser`, `speech` port `BanglaRecognitionPort` + `AzureBanglaRecognition`
(REST) + fake, `talk/domain/BanglaGuess`, `TalkConversation` (check off the audio thread at commit/unclear; Bangla turn
path; cue; teacher translation), `BuddyPrompt.BANGLA_CUE`, Coach transcript marks Bangla turns, web caption `lang="bn"`.
- [ ] Tests: BanglaGuessTest, AzureScoreParser confidence, BanglaTalkTest (low confidence → Bangla text in turn_end and
  saved, reply cue; confident English → no call; slow/failed → as today; wordless → Bangla; pause during check → silent),
  teacher Bangla → teach kind english, Coach prompt marks Bangla.

### Task 5: Docs, review, ship
PRD (TLK teacher numbers, TLK-7 Bangla, TLK-11 opener, DBF cards), RUNBOOK, final opus review, fix pass, ship.
Ship after Tasks 1+2, 3 and 4 too, so each part is live as soon as it's green.
