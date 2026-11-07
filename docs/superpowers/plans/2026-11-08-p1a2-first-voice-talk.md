# P1a-2 First Voice Talk Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A learner on https://bolo.eliooo.org taps "Start talking" and has a real spoken conversation with Bolo. Bolo speaks first, listens, decides when the learner's turn is over, and replies in its voice with captions. When the talk ends, the screen shows how fast Bolo replied (p50/p90): the Phase 0 latency evidence.

**Architecture:** This is the Phase 0 latency spike, built as the real Phase 1 talk loop (Architecture §4).
- Browser: an AudioWorklet sends 16 kHz PCM frames over `/ws/talk/{id}`.
- Spring MVC WebSocket relay:
  - streams the frames into Azure continuous recognition;
  - runs a pure `TurnTaker` (energy VAD plus the TLK-4 silence rules) that starts a **speculative** Claude Haiku reply at 1.0 s of silence and commits the turn at 2.5 s, or 4.0 s when the sentence sounds unfinished;
  - splits the streamed reply into sentences, synthesises each one with Azure TTS (24 kHz PCM), and sends audio plus captions back.
- Vendors sit behind `speech` and `llm` ports, with fake adapters for tests and local development.

**Tech Stack:**
- Spring Boot 4.1.2 `spring-boot-starter-websocket`
- `com.microsoft.cognitiveservices.speech:client-sdk:1.53.0`
- `com.anthropic:anthropic-java:3.6.0`
- Next.js 15 client with AudioWorklet and Web Audio
- Playwright `page.routeWebSocket`

**Spec:** `Bolo-Architecture-MVP.md` §2 (rules), §3.1–3.5, §4.1–4.4, §10 Phase 0 "Latency spike"; `Bolo-PRD-MVP.md` TLK-1/2/3/4/8/9/16, NFR-1. Vendor decisions: Azure Speech F0 in `centralindia` for development; Anthropic Bolo key; both are already in `~/bolo/bolo.env` as `BOLO_ANTHROPIC_API_KEY`, `BOLO_AZURE_SPEECH_KEY` and `BOLO_AZURE_SPEECH_REGION`.

## Global Constraints

**Rules and privacy**
- Java owns every rule. The browser renders the `state` the relay sends and derives nothing (no client-side turn ending except sending `done`).
- Logs never contain transcript text or audio. Log ids, sizes and durations at INFO.
- Provider selection is by property (`bolo.speech.vendor`, `bolo.llm.vendor`: `azure`/`anthropic`/`fake`). Nothing above `adapter.out` imports a vendor SDK type. ArchUnit: only `app.bolo.speech..` may import `com.microsoft.cognitiveservices..`, and only `app.bolo.llm..` may import `com.anthropic..`.
- Consent before audio (NFR-16): `POST /api/talk` and the WebSocket handshake both refuse a learner without a consent row for the current `bolo.policy-version`.
- Every turn-taking threshold is a config property under `bolo.talk.*`.
- Never put a key in the repo, a log or a test fixture. Vendor tests read keys from the environment and skip when they're absent.

**Conversation and copy**
- TLK-3: the Buddy never corrects pronunciation or grammar during the conversation.
- TLK-2: the Buddy speaks first and asks one open question at a time.
- Copy (design-system 1-voice-and-copy.md): no emoji; none of the banned words (wrong, bad, error, mistake, fail, failed, incorrect, poor…). UI copy is second person and under 12 words.

**Wire format**
- Audio up: PCM s16le, mono, 16 kHz, 20 ms frames (640 bytes).
- Audio down: PCM s16le, mono, 24 kHz chunks. Opus (NFR-6) is deferred; see the ruling in Task 6.

## Review Focus

1. **Laptop speakers, no headphones.** Bolo's own voice leaks into the mic while it speaks. The relay must not transcribe it as the learner: frames are replaced with silence until the client reports `played`. Test in Task 5.
2. **A learner who says nothing.** Silence alone with an empty transcript never commits a turn, and `done` with an empty transcript is ignored. The talk still ends at its time limit. Tests in Tasks 2 and 5.
3. **A vendor fails mid-talk** (Azure or Anthropic error or timeout). The learner gets `ended(reason "voice_unavailable")` and a kind message, not a frozen screen; the session row is closed. Test in Task 5.
4. **The tab closes or the socket drops mid-reply.** The STT stream and LLM stream are closed and the live-session slot is freed, so the next "Start talking" works. Test in Task 5.
5. **A second talk while the free tier allows only one** (`bolo.talk.max-live-sessions: 1`). The learner sees "Bolo is in another talk right now. Try again in a few minutes.", not a broken page. Tests in Tasks 1 and 6.

---

### Task 1: Talk sessions, the usage ledger and the WebSocket door

**Files:**
- Create: `api/src/main/resources/db/migration/V3__talk.sql`
- Create: the `app/bolo/talk/` module:
  - `domain/TalkSession.java`, `domain/EndReason.java`
  - `application/port/in/StartTalkUseCase.java`, `application/port/in/TalkView.java`
  - `application/port/out/TalkSessionPort.java`
  - `application/service/TalkSessionService.java`
  - `adapter/in/web/TalkController.java`
  - `adapter/in/ws/TalkHandshakeInterceptor.java`, `adapter/in/ws/TalkWebSocketConfig.java`
  - `adapter/out/persistence/JdbcTalkSessionAdapter.java`
- Create: `app/bolo/common/usage/UsageRecorder.java` (interface) and `common/usage/JdbcUsageRecorder.java`
- Modify: `api/build.gradle` (add `spring-boot-starter-websocket`), `application.yml`, `LayerRulesTest.java` (vendor-SDK rules)
- Test: `api/src/test/java/app/bolo/talk/TalkSessionAcceptanceTest.java`

**Interfaces:**
- Consumes: `CurrentLearnerUseCase.byToken(String)` and `.view(LearnerId)` → `LearnerView(consent, profile)`; `SessionCookie.read(HttpServletRequest)`; `bolo.policy-version`; `bolo.public-origin`.
- Produces:
  - `record TalkSessionId(UUID value)` with `@JsonValue`, in `app.bolo.common.id`.
  - `StartTalkUseCase`:
    - `TalkView start(LearnerId)`: throws `RefusedException("no-consent")` or `RefusedException("busy")`.
    - `Optional<TalkView> openFor(LearnerId, TalkSessionId)`: present only for that learner's own session with `ended_at is null`.
    - `void end(TalkSessionId, EndReason)`: idempotent; frees the live slot.
  - `record TalkView(TalkSessionId id, int minutes, String goal, String fearedMoment)`. Minutes come from the profile; 5 when there's no profile.
  - `enum EndReason { TIME_UP, LEARNER_ENDED, DISCONNECTED, VOICE_UNAVAILABLE }` with `dbValue()` in snake_case.
  - `UsageRecorder.record(LearnerId, TalkSessionId, String vendor, String kind, long units, String unitType)`, where `unitType` is one of `tokens_in`, `tokens_out`, `audio_ms`, `chars`.
  - Handshake attributes `"learnerId"` (`LearnerId`) and `"talk"` (`TalkView`).
  - `POST /api/talk` → 201 `{"id":"…","minutes":5}`.

**Schema (V3):**
- `talk_session(id uuid pk, learner_id uuid fk → learner on delete cascade, minutes int check (minutes in (5,10)), started_at timestamptz, ended_at timestamptz null, end_reason text null)`
- `talk_turn(id bigserial pk, session_id uuid fk on delete cascade, idx int, speaker text check (speaker in ('buddy','learner')), text text not null, server_ms int null, heard_ms int null, at timestamptz, unique(session_id, idx))`
- `usage(id bigserial pk, learner_id uuid fk on delete cascade, session_id uuid null fk on delete cascade, vendor text, kind text, units bigint, unit_type text, at timestamptz)`

- [ ] **Step 1: Write the failing acceptance tests** in `TalkSessionAcceptanceTest`. Use the helpers of `LearnerAcceptanceTest`: a guest with consent and a profile with `talkMinutes 10`.
  - `startsATalkForAConsentedLearner`: `POST /api/talk` → 201, `$.minutes == 10`, and a `talk_session` row exists.
  - `refusesATalkWithoutConsent`: a guest with no consent row → 422 `{"code":"no-consent"}`.
  - `refusesASecondLiveTalkWhenTheCapIsReached`: `bolo.talk.max-live-sessions=1`; two learners start; the second gets 422 `{"code":"busy"}`. After `end(first, LEARNER_ENDED)`, the second learner succeeds.
  - `handshakeNeedsCookieOriginAndOwnSession`: use `StandardWebSocketClient` against the random port.
    - No cookie → the handshake fails with 403.
    - Origin `https://evil.example` → 403.
    - Another learner's session id → 403.
    - An ended session → 403.
    - Own open session with `Origin: bolo.public-origin` → connected.
  - `postTalkIsWriteGuarded`: no `X-Bolo` header → 403 (the existing `WriteGuardFilter`).
- [ ] **Step 2: Run them to see them fail.** Run: `cd api && ./gradlew test --tests '*TalkSessionAcceptanceTest*'`. Expected: FAIL (404 / no bean).
- [ ] **Step 3: Implement.**
  - The live-slot count is `select count(*) from talk_session where ended_at is null and started_at > now() - interval '30 minutes'`, so a crashed relay can't hold a slot forever.
  - `TalkHandshakeInterceptor`:
    - resolves the cookie via `SessionCookie.read` and `CurrentLearnerUseCase.byToken`;
    - checks `Origin` equals `bolo.public-origin`;
    - checks consent's policyVersion equals `bolo.policy-version`, then `openFor`;
    - on any failure, sets status 403 and returns false.
  - Register the handler at `/ws/talk/{id}`. The handler class itself comes in Task 5, so register a placeholder `TextWebSocketHandler` that closes the socket with `CloseStatus.NOT_ACCEPTABLE`; Task 5 replaces it.
  - Properties: `bolo.talk.max-live-sessions: ${BOLO_TALK_MAX_LIVE:1}` (Azure F0 allows one live recognition).
  - Add the two ArchUnit vendor-SDK rules.
- [ ] **Step 4: Run the whole API suite.** Run: `cd api && ./gradlew test`. Expected: all green (37 earlier tests + 5 new).
- [ ] **Step 5: Commit** `feat(api): talk sessions, usage ledger and the /ws/talk door`

### Task 2: Turn-taking policy (pure domain)

**Files:**
- Create: `app/bolo/talk/domain/EnergyVad.java`, `domain/TurnTaker.java`, `domain/TurnPolicy.java` (record of thresholds), `domain/SentenceSplitter.java`
- Test: `api/src/test/java/app/bolo/talk/domain/TurnTakerTest.java`, `EnergyVadTest.java`, `SentenceSplitterTest.java`

**Interfaces:**
- Produces:
  - `EnergyVad`:
    - `boolean isSpeech(short[] frame)`: RMS in dBFS, compared against `max(speechFloorDb, noiseFloorDb + marginDb)`;
    - the noise floor is a slow-tracking minimum (rises 0.05 dB per frame, drops immediately);
    - defaults: `speechFloorDb -50`, `marginDb 12`.
  - `record TurnPolicy(long speculateAfterMs, long endAfterMs, long unfinishedEndAfterMs, Set<String> unfinishedWords)`, bound from `bolo.talk.*`:
    - defaults `1000`, `2500`, `4000`;
    - `unfinishedWords` = and, but, because, so, or, then, like, um, uh, the, a, an, to, my, i.
  - `TurnTaker`:
    - `TurnTaker(TurnPolicy)`;
    - `Decision onFrame(boolean speech, long nowMs)`;
    - `void onTranscript(String textSoFar)`;
    - `Decision onDone(long nowMs)`;
    - `void reset()`;
    - `enum Decision { NONE, SPECULATE, RESUMED, COMMIT }`.
  - `SentenceSplitter`:
    - `List<String> push(String delta)` returns completed sentences;
    - `Optional<String> flush()`;
    - a sentence ends at `.`, `!` or `?` followed by whitespace;
    - pieces shorter than 20 characters are joined to the next one, so there are fewer TTS calls (F0 allows about 20 per minute).

- [ ] **Step 1: Write the failing tests.**
  - `TurnTakerTest`:
    - `commitsAfter2500msOfSilenceOnceSomethingWasSaid`: speech frames 0–1000 ms, transcript "I work in a bank", silence frames → `SPECULATE` at 2000 ms (1000 after the last speech), `COMMIT` at 3500 ms. Exactly one of each.
    - `waitsLongerWhenTheSentenceSoundsUnfinished`: transcript "I went there because" → no `COMMIT` at 3500 ms, `COMMIT` at 5000 ms.
    - `speechAfterSpeculationReportsResumed`: `SPECULATE` then a speech frame → `RESUMED`; the next silence speculates again.
    - `silenceAloneNeverCommits`: 60 s of silence frames, empty transcript → only `NONE`.
    - `doneCommitsAtOnceButNotWhenNothingWasSaid`: `onDone` with transcript "Hello there" → `COMMIT`; after `reset()` with an empty transcript → `NONE`.
    - `resetStartsAFreshTurn`.
  - `EnergyVadTest`:
    - a 440 Hz tone at −20 dBFS → speech;
    - digital silence → not speech;
    - −45 dBFS steady hiss, after 100 frames → not speech; a −20 dBFS tone over it → speech.
  - `SentenceSplitterTest`:
    - `"Oh nice. What was the bug doing? Tell me"` pushed in 3-character deltas → `["Oh nice. What was the bug doing?"]`, then `flush()` → `"Tell me"`;
    - `"Mr. Rahman"` mid-stream is acceptable to split; no test pins abbreviations.
- [ ] **Step 2: Run to fail.** Run: `cd api && ./gradlew test --tests 'app.bolo.talk.domain.*'`. Expected: FAIL (classes missing).
- [ ] **Step 3: Implement the three classes** (pure Java, no Spring). For `unfinishedWords`, compare the last word of the lower-cased transcript with trailing punctuation stripped.
- [ ] **Step 4: Run to pass.** Same command. Expected: PASS.
- [ ] **Step 5: Commit** `feat(api): turn-taking policy, energy VAD and sentence splitter`

### Task 3: Speech module: ports, Azure adapters, fakes

**Files:**
- Create: the `app/bolo/speech/` module:
  - `application/port/in/StreamTranscriptionUseCase.java`, `application/port/in/SynthesizeUseCase.java`
  - `application/port/out/SpeechToTextPort.java`, `application/port/out/TextToSpeechPort.java`
  - `application/service/SpeechService.java` (delegates and records usage)
  - `adapter/out/azure/AzureSpeechToText.java`, `adapter/out/azure/AzureTextToSpeech.java`, `adapter/out/azure/AzureSpeechProperties.java`
  - `adapter/out/fake/FakeSpeechToText.java`, `adapter/out/fake/FakeTextToSpeech.java`
  - `config/SpeechConfiguration.java`
- Modify: `api/build.gradle` (`implementation 'com.microsoft.cognitiveservices.speech:client-sdk:1.53.0'`), `api/Dockerfile` (`apt-get install -y --no-install-recommends libasound2t64 ca-certificates && rm -rf /var/lib/apt/lists/*`; Azure's Linux SDK needs ALSA and OpenSSL)
- Test: `speech/FakeSpeechTest.java`; `speech/AzureSpeechVendorTest.java` (`@EnabledIfEnvironmentVariable(named="BOLO_AZURE_SPEECH_KEY", matches=".+")`)
- Fixture: `api/src/test/resources/audio/job-interview-16k.wav`. Make it with `say -v Rishi -o /tmp/j.aiff "I want to practise English for my job interview next week." && afconvert -f WAVE -d LEI16@16000 -c 1 /tmp/j.aiff api/src/test/resources/audio/job-interview-16k.wav`. If Rishi isn't installed, use `say` with any en_IN or en_GB voice listed by `say -v '?'`.

**Interfaces:**
- Produces:
  - `StreamTranscriptionUseCase.open(TalkContext ctx, TranscriptListener l)` → `TranscriptionStream`:
    - `TranscriptionStream`: `void write(byte[] pcm16k)`, `void close()`;
    - `TranscriptListener`: `onPartial(String textSoFar)`, `onFailure(Exception)`;
    - `textSoFar` = every final segment since the stream opened + the current partial, space-joined.
  - `SynthesizeUseCase.synthesize(TalkContext ctx, String text, Consumer<byte[]> pcm24kChunk)`: blocking until done; throws `SpeechUnavailableException` (unchecked) on vendor failure.
  - `record TalkContext(LearnerId learnerId, TalkSessionId sessionId)`, in `app.bolo.common.id`.
  - Usage rows:
    - STT: vendor `azure`, kind `stt`, unitType `audio_ms`, written on `close()`;
    - TTS: kind `tts`, unitType `chars`.
- Properties:
  - `bolo.speech.vendor: ${BOLO_SPEECH_VENDOR:azure}`
  - `bolo.speech.azure.key: ${BOLO_AZURE_SPEECH_KEY:}`, `region: ${BOLO_AZURE_SPEECH_REGION:centralindia}`
  - `stt-language: en-IN`
  - `buddy-voice: en-IN-NeerjaNeural`
  - `buddy-rate: "-5%"`
  - Tests set `bolo.speech.vendor=fake`.
  - `vendor=azure` with an empty key fails startup with a clear message.

- [ ] **Step 1: Write the failing tests.**
  - `FakeSpeechTest`:
    - the fake STT emits `onPartial("hello bolo")` after it has received 300 ms of speech-level frames;
    - the fake TTS emits 24 kHz PCM chunks totalling `text.length() * 1200` bytes (50 ms of audio per character), so relay tests get believable timing.
  - `AzureSpeechVendorTest` (skips without a key):
    - stream the fixture in 640-byte frames, then 1.5 s of zeros → `textSoFar` contains "job interview" (case-insensitive) within 10 s;
    - `synthesize("Hi, I'm Bolo.")` yields at least 24,000 bytes.
- [ ] **Step 2: Run to fail.** Run: `cd api && ./gradlew test --tests 'app.bolo.speech.*'`. Expected: FAIL (classes missing); the vendor test is SKIPPED.
- [ ] **Step 3: Implement.**
  - STT:
    - `SpeechConfig.fromSubscription`, `setSpeechRecognitionLanguage(stt-language)`;
    - `AudioConfig.fromStreamInput(PushAudioInputStream)` with `AudioStreamFormat.getWaveFormatPCM(16000, 16, 1)`;
    - `startContinuousRecognitionAsync`; `recognizing` gives partials, `recognized` appends finals, `canceled` with an error goes to `onFailure`.
  - TTS:
    - output format `Raw24Khz16BitMonoPcm`;
    - SSML `<speak><voice name=…><prosody rate=…>…</prosody></voice></speak>`, with the text XML-escaped;
    - stream chunks from the `Synthesizing` event.
  - Release every SDK object (`close()`) in `finally` blocks.
- [ ] **Step 4: Run to pass.** Same command. Expected: PASS; the vendor test SKIPPED without a key. Then, if a key is in your shell, run `BOLO_AZURE_SPEECH_KEY=… ./gradlew test --tests '*AzureSpeechVendorTest*'`. Otherwise this is verified by Task 7's live probe.
- [ ] **Step 5: Commit** `feat(api): speech module with Azure streaming STT/TTS and fakes`

### Task 4: LLM module and the Buddy brain

**Files:**
- Create: the `app/bolo/llm/` module:
  - `application/port/in/LlmClient.java`, `application/port/in/LlmRequest.java`, `application/port/in/LlmStream.java`
  - `adapter/out/anthropic/AnthropicLlmClient.java`, `adapter/out/fake/FakeLlmClient.java`
  - `config/LlmConfiguration.java`
- Create: `app/bolo/talk/domain/BuddyPrompt.java`, `app/bolo/talk/domain/Exchange.java` (record `speaker`, `text`)
- Modify: `api/build.gradle` (`implementation 'com.anthropic:anthropic-java:3.6.0'`)
- Test: `talk/domain/BuddyPromptTest.java`, `llm/FakeLlmClientTest.java`, `llm/AnthropicVendorTest.java` (`@EnabledIfEnvironmentVariable(named="BOLO_ANTHROPIC_API_KEY", matches=".+")`)

**Interfaces:**
- Produces:
  - `LlmClient.stream(TalkContext ctx, LlmRequest req, Consumer<String> onDelta)` → `LlmStream`:
    - `LlmStream`: `void cancel()`, `CompletableFuture<String> done()` (completes with the full text, or exceptionally with `LlmUnavailableException`);
    - `record LlmRequest(String model, String system, List<Exchange> messages, int maxTokens)`;
    - usage rows: kind `llm`, unitType `tokens_in` / `tokens_out`, written when the stream finishes or is cancelled (whatever usage the stream reported).
  - `BuddyPrompt`:
    - `static String system(String goal, String fearedMoment, int minutes)`;
    - `static List<Exchange> opening()`: one learner-side instruction asking Bolo to open the talk;
    - `static List<Exchange> wrapUp(List<Exchange> history)`.
- Properties:
  - `bolo.llm.vendor: ${BOLO_LLM_VENDOR:anthropic}`, `bolo.llm.anthropic.key: ${BOLO_ANTHROPIC_API_KEY:}`
  - `bolo.llm.buddy-model`: the current Haiku model id, copied from the `claude-api` skill (load that skill before writing this adapter); `max-tokens: 160`
  - Tests use `fake`.

**The system prompt must say:**
- You are Bolo, a curious friend on the bus (design-system voice table). The learner is a Bangladeshi adult practising spoken English.
- Reply in 1–2 short spoken sentences, at most 35 words. Ask one open question at a time and follow up on what they said (TLK-2).
- Never correct pronunciation, grammar or word choice, and never mention mistakes (TLK-3).
- Plain spoken text only: no markdown, lists, emoji or stage directions.
- Their goal is {goal}; the moment they fear is {fearedMoment}. Steer gently toward everyday talk that helps with it.
- Refuse harmful topics kindly and change the subject (a placeholder until TLK-14's classifier).
- The talk lasts {minutes} minutes.

- [ ] **Step 1: Write the failing tests.**
  - `BuddyPromptTest`:
    - `system("job_interviews","talking_to_my_boss",5)` contains "Never correct", "one open question", "job interviews" (the key humanised), "5 minutes";
    - it contains none of the banned copy words in learner-facing instructions; check "mistake" appears only in the "never mention mistakes" rule.
  - `FakeLlmClientTest`:
    - the fake streams `"That sounds great. What do you do at work?"` in 4-character deltas, about 30 ms apart;
    - `cancel()` stops further deltas and completes `done()` exceptionally with `CancellationException`.
  - `AnthropicVendorTest` (skips without a key): `stream` with the Buddy system prompt and the learner line "I work in a bank." → `done()` completes within 10 s with 1–3 sentences ending in "?".
- [ ] **Step 2: Run to fail.** Run: `cd api && ./gradlew test --tests 'app.bolo.llm.*' --tests '*BuddyPromptTest*'`. Expected: FAIL; the vendor test SKIPPED.
- [ ] **Step 3: Implement.** The Anthropic adapter uses the SDK's streaming Messages API on a virtual thread; `cancel()` closes the stream.
- [ ] **Step 4: Run to pass.** Same command. Expected: PASS.
- [ ] **Step 5: Commit** `feat(api): llm module with Anthropic streaming and the Buddy prompt`

### Task 5: The talk relay

**Files:**
- Create:
  - `app/bolo/talk/application/port/in/TalkRelayUseCase.java`
  - `app/bolo/talk/application/service/TalkConversation.java` (per-session state machine)
  - `app/bolo/talk/application/service/TalkRelayService.java`
  - `app/bolo/talk/application/port/out/TurnPort.java`, `app/bolo/talk/adapter/out/persistence/JdbcTurnAdapter.java`
  - `app/bolo/talk/adapter/in/ws/TalkSocketHandler.java`
- Modify: `TalkWebSocketConfig` (replace the placeholder handler)
- Test: `api/src/test/java/app/bolo/talk/TalkRelayAcceptanceTest.java` (fakes, real WebSocket on the random port; `bolo.talk.*` thresholds shrunk to 100/300/500 ms so the test is fast)

**Interfaces:**
- Consumes: Task 1 `StartTalkUseCase`, `TalkView`, `UsageRecorder`; Task 2 `EnergyVad`, `TurnTaker`, `SentenceSplitter`; Task 3 `StreamTranscriptionUseCase`, `SynthesizeUseCase`; Task 4 `LlmClient`, `BuddyPrompt`.
- Produces: the wire protocol below. Task 6 implements the client side of exactly this.

| Direction | Message |
| --- | --- |
| C → S binary | PCM s16le 16 kHz mono, any length that is a multiple of 2 |
| C → S text | `{"type":"done"}` · `{"type":"played","turn":n}` · `{"type":"end"}` · `{"type":"heard","turn":n,"ms":m}` |
| S → C text | `{"type":"state","value":"buddy_speaking"\|"your_turn"\|"thinking"}` · `{"type":"caption","turn":n,"text":"…"}` (one per sentence, appended by the client) · `{"type":"turn_end","turn":n}` · `{"type":"buddy_done","turn":n}` · `{"type":"ended","reason":"time_up"\|"learner_ended"\|"voice_unavailable","turns":k,"p50Ms":x,"p90Ms":y}` |
| S → C binary | PCM s16le 24 kHz mono chunk of the current Buddy turn |

**Behaviour (`TalkConversation`):**
- **On open:** send `state buddy_speaking` and the Buddy's opening turn (index 0), via LLM → splitter → TTS per sentence. Send a `caption` before each sentence's audio, then `buddy_done`.
- **While buddy_speaking:** incoming frames go to STT as **zeros**, so the stream stays alive and the echo is never heard (Review Focus 1). `played` for the current Buddy turn → `state your_turn`, `TurnTaker.reset()`.
- **While your_turn:** frames go to STT and to `EnergyVad` → `TurnTaker`.
  - `SPECULATE` starts an LLM stream on the transcript at that moment and buffers its text without sending it.
  - `RESUMED` cancels the speculation.
  - `COMMIT`, or `done` with non-empty text:
    - send `turn_end` and `state thinking`;
    - persist the learner turn;
    - if the speculation's transcript equals the committed transcript, reuse its text (finished or still streaming); otherwise cancel it and start a new stream;
    - stream sentences → TTS → audio.
    - `server_ms` = commit → first audio chunk sent, stored on the Buddy turn.
- **heard:** store `heard_ms` on that Buddy turn.
- **Time limit:**
  - when `minutes` have passed since open, the next commit is answered with `BuddyPrompt.wrapUp` instead of a normal reply;
  - after its `played`, send `ended time_up`;
  - if the learner is mid-turn at the limit + 60 s, wrap up anyway.
- **end:** send `ended learner_ended`, close the socket, `StartTalkUseCase.end`.
- **Vendor failure** (any `SpeechUnavailableException`, `LlmUnavailableException` or STT `onFailure`): send `ended voice_unavailable`, close with status 1011, and end the session (Review Focus 3).
- **Socket closed by the client or the network:** close STT, cancel the LLM, `end(DISCONNECTED)` (Review Focus 4).
- **The `ended` latency summary:** p50 and p90 over this session's `heard_ms`; fall back to `server_ms` when no `heard` arrived.
- **Threads:** all sends go through `ConcurrentWebSocketSessionDecorator(session, 5000, 1 << 20)`. Use one virtual thread per Buddy turn, and serialise state changes on the conversation's lock.

- [ ] **Step 1: Write the failing acceptance tests** (Java `StandardWebSocketClient`, a consented guest's cookie and Origin; the "speech" frames are a −20 dBFS 440 Hz tone).
  - `buddySpeaksFirstWithCaptionAndAudio`: on connect → `state buddy_speaking`, ≥ 1 `caption` for turn 0, ≥ 1 binary message, `buddy_done turn 0`.
  - `aTurnIsCommittedAfterSilenceAndAnswered`: `played 0`, 400 ms of tone, 600 ms of zeros → `turn_end 1`, then `caption turn 2` and binary audio. `talk_turn` has rows idx 0 (buddy), 1 (learner, "hello bolo"), 2 (buddy, `server_ms` not null).
  - `doneEndsTheTurnAtOnce`: tone, then `done` immediately → `turn_end` within 200 ms.
  - `framesWhileBuddySpeaksAreNotHeard`: before `played 0`, send 1 s of tone → the fake STT received only zeros, and no `turn_end` arrives within 1 s.
  - `silenceOnlyNeverEndsTheTurn`: `played 0`, 2 s of zeros → no `turn_end`.
  - `endClosesTheSessionAndFreesTheSlot`: `end` → `ended learner_ended` with `turns`; `talk_session.ended_at` is set; a new `POST /api/talk` → 201.
  - `droppedSocketFreesTheSlot`: close the client socket abruptly mid-reply → within 2 s `end_reason = 'disconnected'` and a new `POST /api/talk` → 201.
  - `vendorFailureEndsKindly`: `FakeLlmClient` set to fail → `ended voice_unavailable`, `end_reason = 'voice_unavailable'`.
  - `timeLimitWrapsUp`: `bolo.talk.minute-ms=1000` (production default 60000, so tests can shorten a minute); after 1 s a committed turn gets the wrap-up reply, then `played` → `ended time_up`.
  - `heardIsStored`: `heard turn 2 ms 840` → `talk_turn.heard_ms = 840`, and `ended.p50Ms == 840`.
- [ ] **Step 2: Run to fail.** Run: `cd api && ./gradlew test --tests '*TalkRelayAcceptanceTest*'`. Expected: FAIL (placeholder handler closes with NOT_ACCEPTABLE).
- [ ] **Step 3: Implement** `TalkConversation`, `TalkRelayService` and `TalkSocketHandler` as specified. Set the WebSocket container's max binary message size to 64 KB.
- [ ] **Step 4: Run the whole API suite.** Run: `cd api && ./gradlew test`. Expected: all green.
- [ ] **Step 5: Commit** `feat(api): talk relay with speculative replies, turn persistence and latency`

### Task 6: The talk screen

**Files:**
- Create:
  - `web/public/worklets/pcm-capture.js` (AudioWorkletProcessor: downsample to 16 kHz, post 320-sample Int16 frames)
  - `web/src/talk/pcm.ts` (pure: `downsampleTo16k(input: Float32Array, inRate: number, carry: Downsampler): Int16Array[]`, `int16ToFloat32(buf: ArrayBuffer): Float32Array`)
  - `web/src/talk/player.ts` (`createPlayer(ctx: AudioContext)` → `{ enqueue(pcm24k: ArrayBuffer): void; onStart(cb: () => void): void; onDrained(cb: () => void): void; stop(): void }`, scheduling buffers back to back on `ctx.currentTime`)
  - `web/src/talk/talkState.ts` (reducer: `TalkState { phase: "connecting"|"buddy_speaking"|"your_turn"|"thinking"|"ended"|"busy"|"no_voice"; caption: string; turn: number; summary?: {turns:number;p50Ms:number;p90Ms:number} }`, actions = the server messages plus `socket_closed`)
  - `web/src/talk/useTalk.ts` (opens `POST /api/talk`, then the socket at `${location.protocol==="https:"?"wss":"ws"}://${location.host}/ws/talk/${id}` or `NEXT_PUBLIC_BOLO_WS` in development; wires mic → worklet → socket and socket → player; sends `played` on drain and `heard` on the first audio start after `turn_end`)
  - `web/src/talk/TalkScreen.tsx`
- Modify:
  - `web/src/onboarding/screens/FirstTalkScreen.tsx`: primary **"Start talking"** opens the talk (TLK-1); "Play my Day 1 clip" becomes secondary.
  - `web/src/onboarding/steps.ts` if a `talk` sub-state is needed.
  - `web/src/api/browser.ts`: refused code `busy`.
- Test:
  - `web/src/talk/pcm.test.ts`, `player.test.ts` (fake AudioContext with a controllable `currentTime`), `talkState.test.ts`
  - `web/e2e/talk.spec.ts` (`page.routeWebSocket('**/ws/talk/**', …)` plays the server; the API is stubbed as in `onboarding.spec.ts`)

**Screen (prototype `data-screen="talk"`, TLK-16):**
- **Top:** "Free talk · {minutes} min", and End.
- **Caption:** the current Buddy turn's sentences appended (TLK-8, on by default; the toggle is deferred).
- **Dock:** the mic ring (`bo-mic--turn` on your_turn, `bo-mic--thinking` on thinking/buddy_speaking), and **Done** (Space).
- **Keys:** Space = Done, Esc = End sheet.
- **End sheet:**
  - title "End this talk?", buttons "End" and "Keep talking";
  - Esc keeps talking;
  - ruling: not the prototype's "End and get feedback?", because the debrief doesn't exist yet.
- **Ended:** caption "Nice talk. You took {turns} turns." Footnote "Bolo answered in about {p50} s (slowest {p90} s)." Buttons **Talk again** and **Done** (back to the first-talk screen).
- **Busy:** "Bolo is in another talk right now. Try again in a few minutes."
- **voice_unavailable:** "Bolo lost its voice for a moment. Your talk so far is saved." with **Try again**.
- Pause and Repeat are not rendered, so there are no dead controls. Ruling: TLK-10 and TLK-13 come in P1a-3.

**Mic:** use the device remembered from onboarding (`openMic(deviceId)`) with echoCancellation, noiseSuppression and autoGainControl on, and reuse the onboarding mic-error copy if it fails.

**Ruling — PCM on the wire:**
- Ruling: PCM, not Opus.
- Why: Opus needs a WASM encoder and a server-side decoder. PCM is about 32 KB/s up and 48 KB/s down, which is fine on broadband and 4G for Phase 0.
- Cost if wrong: about 19 MB per 10 minutes, against NFR-6's 5 MB, so Opus must land before the mobile-web release.

- [ ] **Step 1: Write the failing tests.**
  - `pcm.test.ts`:
    - 48 kHz → 16 kHz yields exactly 320 samples per 960 input samples;
    - a carry across calls loses no samples;
    - clipping at ±1.0 maps to ±32767.
  - `player.test.ts`:
    - three enqueued 100 ms buffers start at t, t+0.1 and t+0.2;
    - `onStart` fires once per turn;
    - `onDrained` fires after the last buffer ends;
    - `stop()` cancels the queued buffers.
  - `talkState.test.ts`:
    - `caption` messages for the same turn append and a new turn replaces;
    - `ended` stores the summary;
    - `socket_closed` before `ended` → `no_voice`.
  - `talk.spec.ts` (Firefox and Chromium projects as in `onboarding.spec.ts`):
    - **"Start talking"**: the fake server sends `state buddy_speaking`, `caption 0 "Hi, I'm Bolo. What did you do today?"`, 0.3 s of 24 kHz PCM and `buddy_done 0`.
      - the caption is visible;
      - the client sends `played 0`, then binary frames with a byte length that is a multiple of 640 or arrives as 640-byte frames;
      - on `state your_turn` the Done button is enabled;
      - pressing Space sends `{"type":"done"}`;
      - the server sends `ended {turns:3,p50Ms:900,p90Ms:1400}` → "You took 3 turns." and "about 0.9 s (slowest 1.4 s)".
    - **Busy**: `POST /api/talk` returns 422 `busy` → the busy copy is shown, no socket opens.
    - **Dropped socket**: the server closes with 1011 → the "lost its voice" copy and **Try again**.
    - **End sheet**: Esc opens "End this talk?"; Esc again keeps talking; End sends `{"type":"end"}`.
- [ ] **Step 2: Run to fail.** Run: `cd web && pnpm test && pnpm e2e --project=firefox talk.spec.ts`. Expected: FAIL (modules missing).
- [ ] **Step 3: Implement** the files above. Load the worklet with `ctx.audioWorklet.addModule("/worklets/pcm-capture.js")`. Resume the AudioContext on the "Start talking" click, which is the user gesture.
- [ ] **Step 4: Run to pass.** Run: `cd web && pnpm lint && pnpm typecheck && pnpm test && pnpm e2e`. Expected: all green, the earlier 14 e2e tests included.
- [ ] **Step 5: Commit** `feat(web): talk screen with live capture, Bolo's voice, captions and latency summary`

### Task 7: Ship it and probe it live

**Files:**
- Create: `api/src/test/java/app/bolo/talk/LiveTalkProbe.java` (`@Tag("live")`, `@EnabledIfEnvironmentVariable(named="BOLO_LIVE_URL", matches="https://.+")`; uses `java.net.http.HttpClient` and `java.net.http.WebSocket` only)
- Create: `scripts/talk-probe.sh` (`BOLO_LIVE_URL=$BOLO_URL ./gradlew liveProbe -q`)
- Modify:
  - `api/build.gradle`: `test { useJUnitPlatform { excludeTags 'live' } }` and a `liveProbe` Test task with `includeTags 'live'`
  - `scripts/smoke.sh`: add the check "talk socket refuses a visitor without a session" → expect 403 from `curl -s -o /dev/null -w '%{http_code}' -H 'Connection: Upgrade' -H 'Upgrade: websocket' -H 'Sec-WebSocket-Version: 13' -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' -H "Origin: $BOLO_URL" "$BOLO_URL/ws/talk/00000000-0000-0000-0000-000000000000"`
  - `docs/RUNBOOK.md`: the talk section, keys, the probe, and the latency SQL `select percentile_cont(array[0.5,0.9]) within group (order by heard_ms) from talk_turn where heard_ms is not null and at > now() - interval '1 day'`

**The probe:**
1. Guest → consent → `POST /api/talk`.
2. Open the socket with `Cookie` and `Origin`.
3. Read until `buddy_done 0`, then send `played 0`.
4. Stream the fixture WAV in real time (640-byte frames every 20 ms), then 3 s of zero frames.
5. Expect `turn_end 1`, then binary audio. Measure turn_end → first binary.
6. Send `played 2` and `heard` with the measured ms, then `end`.
7. Print `opening caption`, `turn 1 → first audio: N ms`, and `ended {…}`.
8. Fail if no audio arrives within 5 s of `turn_end`.

- [ ] **Step 1: Verify the voice exists on Azure** without printing the key: from the box, `curl …/cognitiveservices/voices/list | grep -c '"ShortName": *"en-IN-NeerjaNeural"'` → `1`. If it returns 0, rule on another `en-IN-*Neural` (non-HD) voice and set `buddy-voice`.
- [ ] **Step 2: Run the full suites.** Run: `cd api && ./gradlew test && cd ../web && pnpm lint && pnpm typecheck && pnpm test && pnpm e2e`. Expected: all green.
- [ ] **Step 3: Check the image starts with the native SDK.** Run: `scripts/deploy.sh`. Expected:
  - the deploy succeeds;
  - smoke is 11/11, including the 403 socket check;
  - "box memory available" is ≥ 1 GB;
  - `docker stats --no-stream bolo-api` is under 450 MB after one probe.
- [ ] **Step 4: Probe the live loop.** Run: `scripts/talk-probe.sh`. Expected:
  - an opening caption is printed;
  - `turn 1 → first audio` < 2500 ms;
  - `ended` has `turns ≥ 3`.
  - Run it 5 times and record the five numbers in the ledger: the first p50/p90 from the box. The Dhaka 4G measurement is the user's, on their phone or laptop.
- [ ] **Step 5: Commit** `feat: live talk probe, smoke check and runbook for the voice loop`, and push.

## Deferred to P1a-3 (listed so the reviewer doesn't flag them as missing)

- TLK-5 short-answer nudges, TLK-6 silence prompts (8/15 s), TLK-7 Bangla detection
- TLK-10 pause/resume, TLK-11 story memory, TLK-13 repeat/slower, TLK-15 barge-in with headphones
- TLK-14 safety classifier and the approved crisis script. **Must ship before any pilot learner other than the team uses the talk.**
- TLK-8 caption toggle
- Opus on the wire (NFR-6); `seq`/`ack` resend after drops (NFR-9)
- Turn audio upload and scoring jobs (§4.1 step to J), the job table
- Usage prices (`usd_micros`); units only for now
