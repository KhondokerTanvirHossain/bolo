# Bolo's voice and mood: design

Founder request, 2026-11-12, confirmed 2026-11-13 ("okay sounds good, continue"). This is project 2 in the agreed order (1 live chat → 3 + 4 Bolo knows you → 2 voice and mood).

## Why
- **Learners can't choose who they talk to.** Bolo has one voice (British, female: Azure `en-GB-SoniaNeural`, chosen by the founder on 2026-11-10) and one personality (a warm, curious friend).
- **Learners want to practise with the English they'll actually meet,** and to talk with someone they like. In the founder's words: "I need user to choose male or female, accent British, US, Indian."
- **The mood of the talk matters too.** Some people warm up with jokes, some want it to feel like work, some need it gentle.

## Goals

### A. Voice: who Bolo sounds like
- **The learner picks a voice:** Female or Male, and British, American or Indian English. That makes 6 voices.
- **The default** is British, female: today's voice, unchanged for everyone who never opens the setting.
- **Each voice can be heard before choosing.** A "Hear it" button plays a short sample line in that voice: "Hi, I'm Bolo. What would you like to talk about today?"
- **The voice is Bolo's only.** The Coach keeps its own voice (`en-US-AndrewNeural`), and no Bolo voice is the Coach's voice.
- **The speed setting (Normal / Slower) works with every voice.**
- **What the voice doesn't change:** how the learner's speech is recognised and scored, Bolo's words, and the topics.

### B. Mood: how Bolo talks
Four moods. A mood changes Bolo's words and manner, not the voice, and works with all 6 voices:
- **Friendly** (default): warm and curious, as today.
- **Funny:** playful, with light jokes, gentle teasing and the odd pun. It laughs with the learner, never at them, and never at their English.
- **Professional:** like a friendly colleague or interviewer. A bit more formal, with more work-style questions. It suits job interviews and meetings.
- **Calm:** gentle and very patient, with shorter sentences, simpler words and more encouragement. It suits learners who freeze.

Each mood shows a one-line description and an example of how Bolo might start.

### C. Where the choices live
- **Me → Settings → Speaking** gets two rows:
  - **Bolo's voice**, showing for example "British · Female";
  - **Bolo's mood**, showing for example "Friendly".
- **Each row opens its picker:**
  - the voice picker: Female/Male, then the three accents, each with Hear it;
  - the mood picker: the four moods with their descriptions and examples.
- **When a change applies:** at once in Settings, with no Save button, as for the other settings. It takes effect from the next talk. A talk in progress keeps its voice and mood.
- **They follow the learner across devices,** stored with their other settings.
- **Both phone and desktop,** using the same components.

## Non-goals
- **No romantic mood.** The founder will decide on it later (2026-11-12). Adding a fifth mood later must be a small change.
- **No voice or mood question during onboarding.** The defaults apply until the learner changes them.
- **No mood sensing.** Bolo doesn't detect the learner's mood.
- **No change** to the Coach, the debrief, scoring, safety or crisis care, or "Bolo knows you".
- **No other accents yet** (Australian, Nigerian and the rest). Adding one later must be a small change.

## Anti-goals (failures even if it technically works)
- **The voice changes mid-talk,** or Bolo's voice sounds like the Coach.
- **Funny becomes mean:** it mocks the learner's English, accent or country, or jokes about sensitive topics.
- **Professional becomes a test:** it corrects mistakes, scores answers or feels like an exam.
- **A mood loosens the rules.** No mood may relax the safety rules, the never-correct rule or the no-invented-life rule.
- **Hear it is slow or costly:** it takes more than a couple of seconds, or every tap calls the voice service.
- **The chosen voice can't be heard on a slow phone connection** because samples are large.

## Constraints
- **Voices are Azure Neural voices,** synthesised with the same settings as today (24 kHz PCM, SSML rate for Slower). Candidates:

  | | Female | Male |
  |---|---|---|
  | British | `en-GB-SoniaNeural` (today's default) | `en-GB-RyanNeural` |
  | American | `en-US-JennyNeural` or `en-US-AvaNeural` | `en-US-BrianNeural` or `en-US-GuyNeural` (never Andrew, the Coach) |
  | Indian | `en-IN-NeerjaNeural` | `en-IN-PrabhatNeural` |

  The builder checks each candidate on the production key before choosing (verified by HTTP status, as with every key check).
- **Every mood keeps all of today's rules:**
  - never correct;
  - one open question;
  - at most 35 words;
  - safety and crisis behaviour;
  - "Bolo knows you" (personal openers, interesting talks).
  - A mood only adds a short style block to Bolo's instructions.
- **Stored values:** `voice_gender` (female, male), `voice_accent` (british, american, indian) and `mood` (friendly, funny, professional, calm), stored with the learner's settings. Settings validation refuses unknown values with 422.
- **Samples are made once per voice on the server and cached,** so a Hear-it tap never waits on Azure after the first time. Sample requests are rate-limited per learner.

## Decided by the founder
- Male or female, and British, American or Indian; "start with this" (2026-11-13).
- The four moods, Friendly, Funny, Professional and Calm, with romantic left out for now ("okay sounds good", 2026-11-13).

## My calls (founder to check)
- **The default stays British female and Friendly,** so nothing changes for anyone until they choose.
- **Mood changes words, not the voice.** Azure's emotional "styles" exist only for a few voices, so tying mood to voice would make some combinations impossible. Calm doesn't slow the voice either; the Slower setting does that.
- **There's a Hear it sample for each voice.**
- **The settings live under Me → Settings, not before each talk.** A quick picker on the Talk screen can come later if people want it.
- **The American male voice is never the Coach's voice (Andrew).**
- **The American male voice should sound clearly different from the Coach's,** not just be a different voice. Of the candidates, the builder picks the one that sounds least like Andrew by listening on the production key, and notes the choice in the RUNBOOK. The Coach doesn't change.
- **Moods set tone only.**
  - **They never change the topic plan.** The learner's goal, "Bolo knows you" (the opener, the topic ideas) and "follow them fully" all come first.
  - **Professional is tone,** not a role-play. Bolo doesn't pretend to be an interviewer at a made-up company. Its questions lean toward work only within the learner's own goal and what Bolo knows about them.
  - **Each mood is one short style block,** added after the core rules. It can't override them.
- **Hear it always plays at Normal speed:** 6 samples in all, one per voice. Slower is a separate setting the learner already knows.
- **The pickers are designed in the design system first** (v38: ScreenVoicePicker, ScreenMoodPicker, ScreenSettings, DesktopVoiceMood). The founder approves them before the build.

## Left to the builder
- The exact Azure voice for each slot, from the candidates above, after checking them on the production key.
- How the chosen voice reaches the speech module for a talk.
- The wording of each mood's style block, within the rules above.
- How and where samples are cached.
- Copy for the picker's descriptions and examples, following design-system voice rules.
