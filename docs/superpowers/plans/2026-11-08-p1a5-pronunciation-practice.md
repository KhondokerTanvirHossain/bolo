# P1a-5 Pronunciation Practice ("Try it") Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:**
- **Practice page:** the learner picks a sound (the v sound, th, z, sh, w, and the s at the start, as in "school"), sees a sentence and says it. Within ~2 s they get "Nailed it", "Closer" or "Let's try once more", with the words to work on highlighted and a mouth tip.
- **"Try it" on the Coach's cards:** the same check runs on sound fixes and sentence upgrades (US-4.3/4.4). After 3 tries: "This one takes practice."
- **Data for the accuracy gate:** every attempt is stored with its scores. Its audio is kept only when the learner said yes to keeping recordings.

**Architecture:**
- **Speech:** a new port, `AssessPronunciationUseCase`, runs Azure's **scripted** assessment through the REST short-audio endpoint. A known reference gives miscue detection (insertions and omissions), which is how "ischool" shows up. Verified live: an Insertion "i" before "school" and before "station".
- **Assessment:** a pure `AttemptScorer` turns scored words into a result and focus words.
- **API:** `POST /api/attempts` (multipart WAV) and `GET /api/practice`, the deck of sentences.
- **Web:** records 16 kHz PCM through the existing worklet and downsampler into a WAV and posts it.

**Spec:** `Bolo-PRD-MVP.md` ASM-2, US-3.2, US-4.3, US-4.4 ("Try it"), NFR-3 (attempt → result ≤ 2 s p90); Architecture §3.1 (`assessment` AttemptController), §5 ("Try it" scripted mode). Fixtures: `pa-scripted-ischool.json`, `pa-scripted-very.json`.

## Global Constraints
- Results are words, never scores (design-system copy): "Nailed it" (green), "Closer" (amber), "Let's try once more" (neutral, never red).
- **Judge clarity, not accent (US-3.2).** A word is flagged only when:
  - a pattern sound inside it scores < 60; or
  - it was omitted; or
  - it carries the s-cluster insertion.
- Levels:
  - **Nailed:** no issues and a mean word accuracy of at least 85;
  - **Closer:** at most 1 issue and a mean of at least 70;
  - **Once more:** anything else.
- Clips: up to 10 s, WAV 16 kHz mono, at most 640 KB. Rate limit: 20 attempts per learner per minute (`slow-down`).
- If the vendor is busy (F0's one live recognition is in use, HTTP 429), the learner sees "Bolo is busy for a moment. Try again in a few seconds."
- The attempt's audio is stored (encrypted) only when the learner's consent is keepRecordings=true; the retention sweep removes it after 90 days.

## Review Focus
1. Silence or noise sent as an attempt: Azure returns NoMatch → "Let's try once more", with a "We couldn't hear that" note, not an error.
2. A clip that is too long or too big, or isn't a WAV: 422, with no vendor call.
3. Another learner can't read attempts (there is no read API in v1).
4. Try it on a Coach card for a sentence over 25 words: refused (reference ≤ 30 words).
5. Pressing Try it repeatedly while a request is in flight: one request.

---

### Task 1: Scripted assessment (speech)
- `speech/application/port/in/AssessPronunciationUseCase.java`: `PronunciationResult assess(TalkContext ctx, byte[] wav16k, String reference)`. `record PronunciationResult(boolean heard, double accuracy, List<ScoredWord> words)`.
- `speech/adapter/out/azure/AzureScriptedAssessment.java`: REST, Pronunciation-Assessment header (EnableMiscue true, IPA, Phoneme granularity), 8 s timeout. NoMatch → `heard=false`. 429 → `SpeechBusyException`.
- `FakeScriptedAssessment`: a settable next result.
- Usage: vendor azure, kind `pronunciation`, `audio_ms`.
- `AzureScoreParser` also exposes `accuracy(json)` (NBest[0].AccuracyScore) and `status(json)`.
- Tests: the fixtures parse; the ischool fixture has 2 Insertion words.

### Task 2: AttemptScorer (assessment domain)
- `SoundPattern.S_CLUSTER_VOWEL`: "The s at the start", "s as in school", tip "Start straight on the s: school, not ischool." It is detected by insertion, not by IPA.
- `AttemptScorer.score(String reference, List<SpokenWord> words)` → `AttemptResult(Level level, List<Focus> focus)`. `Focus(word, pattern|null, kind: sound|missing|extra_vowel, tip)`.
- `PracticeDeck`: 3 sentences for each of the 6 patterns.
- Tests (from the fixtures):
  - ischool → CLOSER or ONCE_MORE, with focus words "school" and "station" (S_CLUSTER_VOWEL);
  - the clean sentence → NAILED;
  - a synthetic low `v` in "very" → that word is in focus with V_BH;
  - an omission → missing;
  - no words → ONCE_MORE.

### Task 3: API
- `assessment/adapter/in/web/AttemptController`:
  - `POST /api/attempts` (multipart `audio` + `reference` + optional `pattern`) → `{level, focus[], heard}`;
  - `GET /api/practice` → the deck.
- `AttemptService`: validates (RIFF/WAVE, 16 kHz mono, ≤ 10 s, reference ≤ 30 words), rate-limits, calls the speech port, scores, and stores the `attempt` row (V7). Audio is kept only with consent.
- Tests (acceptance, fake vendor): a nailed attempt; an ischool-like fake result → extra_vowel focus; a bad WAV → 422; the 21st attempt in a minute → 429 `slow-down`; vendor busy → 503 `busy`; the row is stored, and audio only with consent yes.

### Task 4: Web
- `web/src/audio/recordPcm.ts`: `startPcmRecording(deviceId, maxMs)` → `{ stop(): Promise<Blob /* WAV 16 kHz */>, cancel() }`, built on openMic, the worklet and the downsampler.
- `web/src/practice/PracticeScreen.tsx`:
  - pattern cards from `/api/practice`;
  - the sentence shown large;
  - Record/Stop (Space);
  - the result pill (`bo-result`), with focus words highlighted and the tip;
  - Next sentence.
- Entry: a "Practise sounds" button on the first-talk screen.
- `TryIt.tsx` on the Coach's sound and upgrade cards: the same recording and pill. After 3 tries: "This one takes practice."
- Tests:
  - Vitest: the WAV header builder.
  - Playwright (stubbed API): practice flow → pill "Closer" with "school" highlighted; Try it on a card → "Nailed it"; a double tap → one request.

### Task 5: Ship
- Deploy; smoke.
- `LivePracticeProbe` posts the ischool fixture → the focus includes school/station with extra_vowel, and posts the clean fixture → NAILED.
- RUNBOOK: attempts and the accuracy-gate data.
