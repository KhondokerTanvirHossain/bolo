# Bolo's voice and mood (P3c) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In Settings, the learner picks Bolo's voice (Female or Male; British, American or Indian) with a "Hear it" sample for each, and Bolo's mood (Friendly, Funny, Professional, Calm). Both apply from the next talk.

**Architecture:**
- **Settings.** The three new settings are columns on `learner_settings`. They travel to the talk on `TalkView` (a voice key such as `"american-male"`, and a mood).
- **Voice.** The speech module maps the voice key to an Azure voice from config, through a new `SynthesizeUseCase` overload.
- **Mood.** It adds one style block in `BuddyPrompt`.
- **Samples.** They are a speech-module endpoint, cached in memory and rate-limited.
- **Web.** Two pickers live in Me → Speaking: rows that open sub-screens on a phone, and the pickers in place on desktop (DesktopVoiceMood).

**Tech Stack:** Spring Boot 4 / Java 21, JdbcClient, Flyway; Azure Speech SDK; Next.js 15 / React 19, Vitest, Playwright.

**Spec:** `docs/superpowers/specs/2026-11-13-voice-and-mood-design.md`. Design system v38: ScreenVoicePicker, ScreenMoodPicker, ScreenSettings, DesktopVoiceMood.

## Global Constraints
- **Allowed values:**
  - `voiceGender`: `female` | `male`;
  - `voiceAccent`: `british` | `american` | `indian`;
  - `mood`: `friendly` | `funny` | `professional` | `calm`.
  - The defaults are `female`, `british` and `friendly`.
  - The voice key is `accent + "-" + gender`, for example `british-female`.
- **Azure voices, set in config `bolo.speech.azure.buddy-voices.<key>`:**
  - british-female `en-GB-SoniaNeural`, british-male `en-GB-RyanNeural`;
  - american-female `en-US-AvaNeural`, american-male `en-US-GuyNeural`;
  - indian-female `en-IN-NeerjaNeural`, indian-male `en-IN-PrabhatNeural`.
  - The Coach stays `en-US-AndrewNeural`, and no key maps to it.
- **The sample line** is exactly "Hi, I'm Bolo. What would you like to talk about today?", spoken at Normal speed (the configured buddy rate).
- **Mood is words only.** It never changes the voice or the rate. Every mood keeps all of `BuddyPrompt`'s rules. The style block goes after the core rules and before the brief.
- **Changes apply from the next talk.** A live talk keeps the voice and mood it started with.
- **Settings errors:** a bad value is refused with 422 and the code `voice-gender`, `voice-accent` or `mood`. A null field keeps the current value, as `talkView` does.
- **Copy (design system v38), verbatim:**
  - voice picker intro: "Pick who Bolo sounds like. You'll hear it from your next talk."
  - voice picker footnote: "Your Coach keeps its own voice. How Bolo understands you doesn't change."
  - mood picker intro: "How Bolo talks with you. It works with every voice, from your next talk."
  - mood descriptions and examples exactly as in ScreenMoodPicker.

## Review Focus
1. **An old client saves settings without the new fields** (the slower prompt's `keepSlower`, the current Settings page). The voice and mood stay as they were. Owned by Task 1: `oldClientsKeepVoiceAndMood`.
2. **A learner changes voice during a live talk.** The talk keeps its voice, and the next talk uses the new one. Owned by Task 2: `aTalkKeepsTheVoiceItStartedWith`.
3. **Hear it is tapped repeatedly, or for an unknown key.** The second tap is served from cache (one TTS call), more than 20 taps a minute get 429, and an unknown key gets 422. Owned by Task 3: `samplesAreCached`, `tooManySamplesIs429` and `anUnknownVoiceIs422`.
4. **The Slower setting with a non-default voice.** Bolo is slower in the chosen voice, not in Sonia's. Owned by Task 2: `slowerKeepsTheChosenVoice`.
5. **Playing a sample, then another, or leaving the screen.** Only one sample plays at a time, and leaving stops it. Owned by Task 4: e2e `one sample at a time`.

---

### Task 1: Settings carry voice and mood

**Files:**
- Create: `api/src/main/resources/db/migration/V27__voice_and_mood.sql`
- Modify: `api/src/main/java/app/bolo/learner/domain/Settings.java`, `.../application/port/in/UpdateSettingsUseCase.java`, `.../application/service/SettingsService.java`, `.../adapter/in/web/SettingsController.java`, `.../adapter/out/persistence/JdbcSettingsAdapter.java`, `.../application/port/in/LearnerView.java` (SettingsView), and wherever `SettingsView` is built (grep `new LearnerView.SettingsView` / `SettingsView(`)
- Test: `api/src/test/java/app/bolo/learner/SettingsAcceptanceTest.java`

**Interfaces:**
- Produces:
  - `Settings(String buddySpeed, boolean captions, String talkView, String voiceGender, String voiceAccent, String mood)`, with `DEFAULTS = ("normal", true, "chat", "female", "british", "friendly")` and the static validators `validGender`, `validAccent` and `validMood`.
  - `UpdateSettingsUseCase.update(LearnerId, String buddySpeed, boolean captions, String talkView, String voiceGender, String voiceAccent, String mood)`, where a null keeps the current value.
  - `LearnerView.SettingsView(String buddySpeed, boolean captions, String talkView, String voiceGender, String voiceAccent, String mood)`.
  - `PUT /api/me/settings` accepts `voiceGender`, `voiceAccent` and `mood`. `GET /api/me` returns them under `settings`.
  - **V27:** add the columns `voice_gender text not null default 'female' check (...)`, `voice_accent text not null default 'british' check (...)` and `mood text not null default 'friendly' check (...)`.

- [ ] **Step 1: Write the failing tests** in `SettingsAcceptanceTest`:
  - `voiceAndMoodDefault`: `$.settings.voiceGender` is "female", voiceAccent "british", mood "friendly".
  - `voiceAndMoodSave`: PUT with male, american and funny returns 204, and /api/me shows them.
  - `oldClientsKeepVoiceAndMood`: after saving male/indian/calm, a PUT of only `{"buddySpeed":"slower","captions":true}` returns 204, and voice and mood are unchanged.
  - `badVoiceOrMoodIs422`: gender "robot" gives code `voice-gender`, accent "martian" gives `voice-accent`, mood "romantic" gives `mood`.
- [ ] **Step 2: Run them.** `cd api && ./gradlew test --tests 'app.bolo.learner.SettingsAcceptanceTest'`. Expected: FAIL (the fields are missing).
- [ ] **Step 3: Implement** V27, the domain, the service (validate each non-null value, then merge with the stored values or the defaults), the controller record, the adapter, and SettingsView.
- [ ] **Step 4: Run them, plus `--tests 'app.bolo.learner.*'`.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat(api): settings carry Bolo's voice and mood (P3c)`

### Task 2: A talk speaks in the chosen voice and mood

**Files:**
- Modify:
  - `api/src/main/java/app/bolo/talk/application/port/in/TalkView.java`: add `String voice` (key) and `String mood`;
  - `TalkSessionService.toView`;
  - `TalkConversation` (pass the voice key to synthesize, and the mood to `BuddyPrompt.system`);
  - `TalkRelayService` (nothing new, unless the constructor needs it);
  - `talk/domain/BuddyPrompt.java` (a mood parameter and `moodStyle(String mood)`);
  - `speech/application/port/in/SynthesizeUseCase.java` (overload);
  - `speech/application/service/SpeechService.java`;
  - `speech/application/port/out/TextToSpeechPort.java` (overload with a voice key);
  - `speech/adapter/out/azure/AzureTextToSpeech.java` (the key → name map);
  - `speech/adapter/out/fake/FakeTextToSpeech.java` (records keys);
  - `speech/config/SpeechConfiguration.java` (reads `bolo.speech.azure.buddy-voices`);
  - `api/src/main/resources/application.yml` (the 6-voice map, with Global Constraints' values).
- Test: `api/src/test/java/app/bolo/talk/domain/BuddyPromptTest.java`, `api/src/test/java/app/bolo/talk/VoiceAndMoodTalkTest.java`

**Interfaces:**
- Consumes: Task 1's SettingsView fields.
- Produces:
  - `SynthesizeUseCase.synthesize(TalkContext ctx, Voice voice, String buddyVoice, String text, Consumer<byte[]> pcm)`. `buddyVoice` is the key, or null for the default. It's ignored for `Voice.COACH`.
  - `TextToSpeechPort.synthesize(Voice voice, String buddyVoice, String text, Consumer<byte[]>)`, by default delegating to the existing method.
  - `FakeTextToSpeech.buddyVoices()`: `List<String>`, the keys in order; null is recorded as "default".
  - `AzureTextToSpeech`: an unknown or null key uses the default `buddy-voice`. `BUDDY_SLOW` uses the chosen voice at -25%.
  - `BuddyPrompt.system(goal, fearedMoment, minutes, brief, opening, mood)`. The existing 5-argument version means mood "friendly".
  - `BuddyPrompt.moodStyle(String mood)`:
    - friendly gives "" (today's prompt is the friendly one);
    - funny: "- Your mood is funny: playful, with light jokes, gentle teasing about the topic and the odd pun. Laugh with them, never at them, and never about their English, accent or country. No jokes about sensitive subjects.";
    - professional: "- Your mood is professional: like a friendly colleague. A little more formal, clear and polite, and questions lean toward their work and their goal. It is never a test and never a role-play of an interview.";
    - calm: "- Your mood is calm: gentle and very patient. Shorter sentences, simpler words, more encouragement. Never rush them.";
    - an unknown value gives "".

- [ ] **Step 1: Write the failing tests:**
  - `BuddyPromptTest`:
    - `eachMoodAddsItsStyleAndKeepsTheRules`: for funny, professional and calm, the system prompt contains its style line, "Never correct" and "one open question", and doesn't contain "wrong", "error" or "fail".
    - `friendlyIsTodaysPrompt`: the 6-argument version with "friendly" equals the 5-argument version.
  - `VoiceAndMoodTalkTest` (set up like `SlowerDefaultTest`, with fake speech and LLM):
    - `theTalkSpeaksInTheChosenVoice`: settings american/male, then start a talk, and the opening is synthesised with key "american-male".
    - `theMoodReachesBolo`: mood funny, and `llm.lastReplyRequest()` contains "Your mood is funny".
    - `slowerKeepsTheChosenVoice`: buddySpeed slower with indian/female, so the voices include BUDDY_SLOW and the keys include "indian-female".
    - `aTalkKeepsTheVoiceItStartedWith`: start a talk as british-female, change settings to male mid-talk, and the turn after still uses "british-female".
  - `AzureTextToSpeechTest` (unit, if the class is testable without a key, using `ssml(...)`): `voiceFor("american-male")` is "en-US-GuyNeural", unknown keys fall back to the default, and the COACH voice is never in the map.
- [ ] **Step 2: Run them.** `./gradlew test --tests 'app.bolo.talk.domain.BuddyPromptTest' --tests 'app.bolo.talk.VoiceAndMoodTalkTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them, plus `--tests 'app.bolo.talk.*' --tests 'app.bolo.speech.*' --tests 'app.bolo.architecture.*'`.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat(api): Bolo speaks in the chosen voice and mood (P3c)`

### Task 3: Hear it (voice samples)

**Files:**
- Create: `api/src/main/java/app/bolo/speech/application/port/in/VoiceSampleUseCase.java`, `api/src/main/java/app/bolo/speech/application/service/VoiceSampleService.java`, `api/src/main/java/app/bolo/speech/adapter/in/web/VoiceSampleController.java`
- Test: `api/src/test/java/app/bolo/speech/VoiceSampleAcceptanceTest.java`

**Interfaces:**
- Produces:
  - `VoiceSampleUseCase.sample(LearnerId learner, String voiceKey) → byte[]`, a WAV (24 kHz mono 16-bit, via `common.audio.Wav`).
    - It throws `RefusedException("voice")` for a key outside the 6.
    - It throws `TooManyRequestsException` after 20 calls a minute per learner.
    - It synthesises once per key, through `SynthesizeUseCase` with `TalkContext(learner, null)` so usage is recorded, then caches the result in memory.
  - `GET /api/voices/{key}/sample` returns 200 with `audio/wav` and `Cache-Control: private, max-age=86400`. It returns 401 without a session.
  - `VoiceSampleService.SAMPLE = "Hi, I'm Bolo. What would you like to talk about today?"`.

- [ ] **Step 1: Write the failing tests:**
  - `aSampleIsAWav`: 200, content type audio/wav, the body starts with "RIFF", and the fake TTS was asked for key "british-male".
  - `samplesAreCached`: two GETs for the same key make one more `buddyVoices()` entry, not two.
  - `tooManySamplesIs429`: 21 GETs in a row give 429 on the 21st.
  - `anUnknownVoiceIs422`: `/api/voices/klingon-female/sample` gives 422 with code voice.
  - `signedOutIs401`.
- [ ] **Step 2: Run them.** `./gradlew test --tests 'app.bolo.speech.VoiceSampleAcceptanceTest'`. Expected: FAIL (404).
- [ ] **Step 3: Implement.** Check how `Wav` builds a header from PCM, and whether `UsageRecorder` accepts a null session. If it doesn't, rule on it and ledger it.
- [ ] **Step 4: Run them, plus `LayerRulesTest`.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat(api): hear each of Bolo's voices before choosing (P3c)`

### Task 4: The pickers (web)

**Files:**
- Modify: `web/src/settings/settings.ts` (types and defaults), `web/src/me/SpeakingSettings.tsx` (two rows on top; desktop pickers in place)
- Create: `web/src/voice/voices.ts` (`VOICE_LABEL`, `MOODS`, `voiceKey(s)`, `voiceSummary(s) → "British · Female"`, `moodLabel`), `web/src/voice/VoicePicker.tsx`, `web/src/voice/MoodPicker.tsx`, `web/src/voice/voices.test.ts`
- Modify: `web/src/onboarding/screens/FirstTalkScreen.tsx` (View kinds `"voice"` and `"mood"`, routed like `"memory"`), `web/src/me/MeScreen.tsx` (passes `onVoice` and `onMood` to SpeakingSettings), `web/src/styles/bolo.css` (copy the v38 `bo-voice*` and `bo-mood*` rules from design-system `components/bundle.css`)
- Test: `web/e2e/voice-mood.spec.ts`

**Interfaces:**
- Consumes: Task 1's settings fields and Task 3's `GET /api/voices/{key}/sample`.
- Produces:
  - `Settings` gains `voiceGender?: "female" | "male"`, `voiceAccent?: "british" | "american" | "indian"` and `mood?: "friendly" | "funny" | "professional" | "calm"`.
  - `VoicePicker({ settings, onChange })` and `MoodPicker({ settings, onChange })` are pure pickers, with no fetch inside except the sample audio. Both save through `saveSettings` with the full settings object.
  - **Hear it:** one `HTMLAudioElement` at a time, playing `/api/voices/<key>/sample`. While it plays, the button has `aria-pressed=true` and the text "Playing". Pressing it again, or unmounting, stops it.
  - **Layout:**
    - **Phone (< 900px):** the Speaking group shows the rows "Bolo's voice" (value from `voiceSummary`) and "Bolo's mood" (value from `moodLabel`), which open the `voice` and `mood` views. Each has a back button, "Settings".
    - **Desktop (≥ 900px):** the pickers show in place, side by side above the other Speaking rows.
    - Both are rendered, and CSS shows one: `.bo-speak__rows` and `.bo-speak__inline`.

- [ ] **Step 1: Write the failing tests:**
  - `voices.test.ts`: `voiceSummary` of british/female is "British · Female", `voiceKey` of american/male is "american-male", and MOODS has 4 entries with the exact v38 descriptions.
  - `voice-mood.spec.ts` (stubbed `/api/me`, settings PUT and sample audio):
    - `phone: Settings rows show the current voice and mood`;
    - `choosing Male then American saves voiceGender male and voiceAccent american`;
    - `Hear it plays the sample for that voice and shows Playing`;
    - `one sample at a time`: Hear it on British, then on Indian, so only Indian shows Playing;
    - `choosing Funny saves mood funny`;
    - `desktop: both pickers are on the Me page`;
    - `fits at 390 px`.
- [ ] **Step 2: Run them.** `cd web && npx vitest run src/voice && npx playwright test e2e/voice-mood.spec.ts --project=firefox`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run `npx tsc --noEmit`, `npx vitest run`, and `npx playwright test e2e/voice-mood.spec.ts e2e/me.spec.ts e2e/talk.spec.ts --project=firefox`.** Expected: PASS.
- [ ] **Step 5: Commit.** `feat(web): Settings → Bolo's voice and mood (P3c)`

### Task 5: Docs and ship checks

**Files:**
- Modify:
  - `Bolo-PRD-MVP.md`: add **SET-5** (voice: female/male; British, American, Indian; Hear it; from the next talk) and **SET-6** (mood: Friendly, Funny, Professional, Calm; words only; every rule kept), both "Added 2026-11-13 (founder)". Update the Buddy row in the scope table ("1 Buddy persona" becomes 6 voices and 4 moods).
  - `docs/RUNBOOK.md`: a "Bolo's voice and mood" section. It covers the voice map and where to change it, why Guy and Ava were picked (Guy sounds least like the Coach's Andrew), the sample cache and rate limit, and how to test: Me → Speaking → Hear it on each of the 6, choose American · Male and Funny, start a talk.
  - `scripts/smoke.sh`: `GET /api/voices/british-female/sample` without a cookie returns 401.
- [ ] **Step 1:** Edit the three files.
- [ ] **Step 2:** `bash -n scripts/smoke.sh`. Expected: no output.
- [ ] **Step 3:** Commit: `docs: PRD SET-5/SET-6, RUNBOOK Bolo's voice and mood (P3c)`.
