# P1a-4 Pilot-Safe Talk Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The talk becomes safe and kind enough to hand to pilot learners:
- a crisis is met with care and local help;
- silence gets gentle help (8 s), then starters (15 s), then a pause (45 s);
- short answers get drawn out;
- Bangla, or speech Bolo can't make out, gets a kind nudge back to English;
- "Sorry?" and "Slower please" work, along with a replay icon;
- Pause/Resume and a caption toggle are on screen;
- "Bolo is thinking…" appears after 4 s.

**Architecture:**
- **Java decides** every one of these, and the LLM writes the words (Architecture §4.2).
- **Pure domain additions:**
  - `TalkIntent`: repeat or slower, from the learner's words;
  - `CrisisScreen`: a keyword pre-filter;
  - `QuietClock`: silence stages;
  - `TurnTaker.UNCLEAR`: speech heard but no words.
- **New services:**
  - `SafetyGuard`: a Haiku structured call, run in parallel on each committed turn;
  - `Starters`: Haiku structured output with 2 sentence starters.
- **`TalkConversation`** gains PAUSED and CRISIS handling. Pausing closes the recogniser (freeing the F0 slot); resuming reopens it.
- **The web** renders the new messages: `hint`, `paused`, `crisis`.

**Tech Stack:** as P1a-2; Haiku structured output.

**Spec:** `Bolo-PRD-MVP.md` TLK-5/6/7/8/10/13/14, US-2.4–2.8, US-2.10, US-2.12 ("thinking…"), US-2.13; `Bolo-Architecture-MVP.md` §4.2. Helplines (verified 2026-11-08, to be approved by the founder before any pilot learner): national emergency **999**; Kaan Pete Roi **09612-119911** (3 PM–3 AM).

## Global Constraints
- **The crisis script is fixed content, never generated** (Architecture §4.2). It lives in `talk/domain/CrisisScript.java` and is flagged in the morning report for approval.
- A crisis ends the practice (no debrief) and leaves the help card on screen.
- Latency: the safety check never delays a normal reply.
  - A turn that trips the keyword screen waits for the classifier before Bolo speaks.
  - Any other turn's check runs in parallel; if it flags a crisis, Bolo stops mid-reply and the crisis script plays.
- Silence stages count only the learner's turn time and carry across Bolo's re-prompt:
  - 8 s → an encouraging re-prompt (LLM, with a quiet cue);
  - 15 s → a hint card with 2 starters;
  - 45 s → pause ("Paused. Tap to continue.").
- Short answers (< 5 words) add a follow-up hint to Bolo's prompt. 3 in a row also send a starter card.
- Repeat ("sorry", "pardon", "repeat please", "say that again", "come again", "what did you say") and slower ("slower please", "speak slowly", "slow down") are matched in Java with no LLM call.
  - Repeat replays Bolo's last line.
  - Slower switches Bolo to a slower voice rate for the rest of the talk, and re-says the last line.
- Pause:
  - the talk clock and the no-audio watchdog stop;
  - the recogniser closes;
  - after 30 minutes paused, the talk ends;
  - resume reopens the recogniser and Bolo welcomes the learner back and repeats its last question.
- Bolo's voice and persona are unchanged (founder discussion pending), except the slower rate when asked.

## Review Focus
1. A crisis phrase said while Bolo's reply is already speculated and ready: the reply must not be spoken.
2. Pause, then the tab is closed: the talk still ends (`disconnected`) and the slot frees.
3. Silence stages after "Sorry?": the replay is a Bolo turn and must not reset the 45 s stage counting wrongly into an instant pause.
4. Bangla with no recognised words (`UNCLEAR`) on every turn: Bolo keeps nudging, without an endless loop of LLM calls or a stuck turn.
5. Pause while Bolo is speaking: no "played" ever comes, and resume must still restore a sane state.

---

### Task 1: Domain — intents, crisis screen, quiet clock, unclear turns
- `talk/domain/TalkIntent.java`: `enum { NONE, REPEAT, SLOWER }` with `static TalkIntent of(String text)`. Only short utterances (≤ 6 words) match.
- `talk/domain/CrisisScreen.java`: `static boolean mayBeCrisis(String text)`. English keywords plus common Bangla transliterations: kill myself, suicide, want to die, end my life, hurt myself, self harm, no reason to live, morte chai, atmohotta, bachte chai na.
- `talk/domain/CrisisScript.java`: `LINES` (spoken), `TITLE`, `HELP` (list of {name, number, hours}).
- `talk/domain/QuietClock.java`:
  - `Stage onQuiet(long ms)` returns `NONE`, `ENCOURAGE` (at 8 s), `HINT` (at 15 s) or `PAUSE` (at 45 s), each once;
  - `void onSpeech()` resets it;
  - thresholds come from `TurnPolicy`.
- `TurnTaker`: new `Decision.UNCLEAR`, when speech was heard and silence ≥ endAfter but the transcript is blank.
- Tests:
  - `TalkIntentTest`: "Sorry?" → REPEAT; "Slower please" → SLOWER; "I am sorry for being late today" → NONE.
  - `CrisisScreenTest`.
  - `QuietClockTest`: stages fire once each, at 8 / 15 / 45 s; speech resets.
  - `TurnTakerTest.speechWithoutWordsIsUnclear`.

### Task 2: Relay behaviour
- **`TalkConversation`:**
  - quiet stages (ENCOURAGE: an LLM reply with the quiet cue, the history getting a "(stays quiet)" learner line; HINT: `{type:"hint", starters:[…]}`; PAUSE: `pause("quiet")`);
  - short-answer hint and streak;
  - UNCLEAR → a Bangla/unclear cue reply;
  - repeat and slower;
  - `pause`/`resume` client messages;
  - crisis.
- `SafetyGuard` (`talk/application/service`): `CompletableFuture<Boolean> isCrisis(ctx, text)`, a Haiku structured `{crisis:boolean}` call with a 3 s timeout (a timeout counts as false unless the keyword screen matched).
- `Starters`: `List<String> starters(ctx, lastBuddyLine)`. Haiku structured `{starters:[2]}`, falling back to fixed starters on any failure.
- `SynthesizeUseCase` `Voice.BUDDY_SLOW` (rate −25%).
- `EndReason.CRISIS` + `V6__talk_crisis.sql` (end_reason check). TalkEnded with reason crisis → no debrief.
- Protocol additions:
  - C→S `pause`, `resume`, `repeat`;
  - S→C `hint{starters}`, `paused{reason}`, `resumed`, `crisis{title, lines, help[]}`.
- Tests (relay acceptance, fakes, thresholds shrunk):
  - **quiet stages:** encourage, then a hint, then paused;
  - **Sorry?** → the same caption replayed, with no LLM call (FakeLlm call count);
  - **Slower** → the following TTS uses the slow voice (FakeTextToSpeech records voices);
  - **crisis keyword** → no Buddy audio for that turn, a `crisis` message, `ended crisis`, no debrief row;
  - **parallel crisis verdict** (no keyword, the fake classifier says crisis) → crisis sent;
  - **pause** → the recogniser is closed and nothing is ended after the no-audio timeout; resume → a welcome-back caption, then your_turn;
  - **UNCLEAR** → a nudge reply;
  - **short answer** → the LLM request carries the follow-up hint.

### Task 3: Web
- Messages:
  - `hint` → a hint card above the dock (`bo-hint`) with 2 starters, dismissed on the next your_turn;
  - `paused` → "Paused. Tap to continue." with a Continue button (sends `resume`, from a click so audio may play);
  - `crisis` → a help card: the title, the lines, tappable `tel:` numbers, and buttons **I'm okay** (ends) / **Close**. No debrief after a crisis.
- Controls:
  - a Pause button and the P key; auto-pause when the tab is hidden (the mic stops sending);
  - a replay icon on the caption and the R key → `repeat`;
  - a CC toggle (remembered in localStorage) hides the caption;
  - "Bolo is thinking…" when `thinking` lasts more than 4 s.
- Tests:
  - Vitest: reducer cases for hint, paused, crisis and the caption toggle;
  - Playwright (fake socket): a hint card appears; pause → the paused copy → Continue sends resume; crisis → the help card with 999 and 09612-119911; R sends repeat; CC hides the caption.

### Task 4: Ship
- Deploy, smoke, then run the talk and coach probes.
- A live probe for the crisis path: speak a fixture "Sometimes I want to kill myself" → expect `crisis`. It uses `say`. The fixture content is test-only.
- RUNBOOK: crisis content and the approval note.
