# P3a The Talk as a Live Chat: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The talk is shown as a live chat in the sky above the unchanged scene:
- Bolo's turns, and the learner's words live while they speak;
- earlier turns kept and auto-scrolled;
- the time left in numbers and a mic level meter.

Settings → Talk view keeps the Classic screen.

**Architecture:**
- **The server relay (`TalkConversation`) sends four new things over the existing WebSocket:**
  - the learner's live words (`you`, throttled);
  - an explicit drop when a live turn isn't kept (`you_drop`);
  - the saved text on `turn_end`;
  - a `repeatOf` mark on a repeated Bolo line, and `leftMs` on `state` / `paused` / `resumed`.
- **The web reducer keeps a chat model** (settled turns plus the live bubble) and a server-synced clock.
- **`TalkScreen` renders `TalkChat` or the Classic caption,** depending on a new learner setting, `talkView`. The level meter reads the browser's capture frames.

**Tech stack:** Spring Boot 4 / Java 21 (talk and learner modules), Flyway, Next.js 15 / React 19, Vitest, Playwright.

**Spec:**
- `docs/superpowers/specs/2026-11-12-talk-chat-design.md` (approved 2026-11-12);
- PRD TLK-8 and TLK-16 (changed 2026-11-12);
- design system v33 cards TalkChat, DesktopTalk, ScreenConversation, DesktopTalkClassic, ScreenConversationClassic and ScreenSettings (local copies in `design/design-system/components/`).

## Global Constraints
- **The scene art is unchanged.** On desktop the chat column sits from 92px below the top to 35% of the window height, at `min(760px, calc(100% - 64px))` wide; on a phone it runs the full width minus the gutters, from below the top bar to above the sun. It must never cover the sun or the figure.
- **Bubbles:**
  - Bolo is on the left with a rose edge: 22/30 on desktop, 18/26 on a phone. Repeat sits on Bolo's latest turn only.
  - The learner is on the right with a sun edge: 19/28 on desktop, 17/24 on a phone. While speaking the bubble is dashed with a caret; when settled it is solid.
  - Labels: "Bolo", "You", "You · speaking".
- **Time left, copy exact:**
  - "M:SS left" while more than 60 s are left;
  - "1 minute left" from 60 s down to 1 s;
  - "Wrapping up" at 0 or below.
  - No colour change and no sound. It stops while paused.
- **Status line** (plain text under the last turn), with the existing copy: "Bolo is thinking…", "Bolo is getting ready…", "Paused. Tap to continue." and the existing busy, no-voice and no-mic lines.
- **Auto-scroll** follows the newest line unless the learner has scrolled more than 40px up. Then a "Latest" button shows; tapping it scrolls to the bottom and follows again.
- **Accessibility:**
  - The chat is `role="log"` with `aria-live="polite"`.
  - The live bubble is `aria-hidden="true"`; only settled turns are announced.
  - The meter is `aria-hidden`.
  - The timer is `role="timer"`, and announces only "1 minute left" and "Wrapping up".
- **Captions (SET-2):** in Chat view, Captions off hides the whole chat; in Classic it hides the caption, as today. The CC button and the C key toggle it.
- **Talk view:** `talkView` is `"chat"` (the default) or `"classic"`, stored in `learner_settings`. Classic renders today's screen exactly.
- **Live words:** sent at most every 200 ms, only during the learner's turn, and never block the reply path.
- **No new stored data:** live words are never written anywhere.

## Review Focus
1. **A late partial after `turn_end`** must not reopen a live bubble or duplicate the settled one. Test: Task 3 `aLateYouAfterTurnEndIsIgnored`.
2. **A very long turn** (a 60-second speech) keeps the chat scrolled to the newest line, and the turns above don't reflow. Test: Task 5 e2e "a long live turn stays in view".
3. **Pause, then resume:** the countdown stops, then continues from the server's `leftMs`, not the browser's clock. Test: Task 3 `clockStopsWhilePaused` and Task 5 e2e "pause stops the clock".
4. **A screen reader never reads partials:** the live bubble is `aria-hidden`. Test: Task 5 e2e "the live bubble is hidden from screen readers".
5. **Classic with captions off** looks exactly as today: no chat, no numbers. Test: Task 5 e2e "Classic view keeps the caption and the arc".

---

### Task 1: Live words, drop, repeat mark, saved text and time left on the socket (api)
**Files:**
- Modify: `api/src/main/java/app/bolo/talk/application/service/TalkConversation.java`
- Test: `api/src/test/java/app/bolo/talk/TalkRelayAcceptanceTest.java`

**Interfaces (server → client, new or extended):**
- `{"type":"you","turn":<n>,"text":"<words so far>"}`. Here `n` is the index the turn will get on commit (`nextIdx` at that moment). It is sent from `Listener.onPartial` only while `phase == YOUR_TURN`, and only when the text changed and at least 200 ms have passed since the last `you`. The last text is always caught up by `turn_end`.
- `{"type":"you_drop","turn":<n>}`: sent when a turn that had any `you` leaves `YOUR_TURN` without a commit (the `UNCLEAR` cue, `pause`, `repeatOnRequest`).
- `turn_end` gains `"text":"<saved text>"` (the `said` that `commit()` saves).
- `caption` gains `"repeatOf":<earlier Bolo idx>` for the turn `repeatOnRequest` creates.
- `state`, `paused` and `resumed` gain `"leftMs":<limitMs() - elapsedMs()>` (a long, which may be negative).

- [ ] **Step 1: Write the failing tests** in `TalkRelayAcceptanceTest`:
  - `theLearnersWordsAreSentLive`: `yourTurn()`, `tone(400)`, then `s.await(2000, "\"you\"", "\"turn\":1", "\"text\":\"hello bolo\"")`.
  - `turnEndCarriesTheSavedText`: the existing silence commit; `s.await(2000, "\"turn_end\"", "\"turn\":1", "\"text\":\"hello bolo\"")`.
  - `liveWordsComeAtMostFiveASecond`: a 1500 ms tone, then count the `"you"` messages received. Assert ≤ 9.
  - `aPauseMidTurnDropsTheLiveWords`: `tone(400)`, await `you`, send `{"type":"pause"}`, then `s.await(2000, "\"you_drop\"", "\"turn\":1")`.
  - `aRepeatIsMarkedAsTheEarlierLine`: `yourTurn()`, send `{"type":"repeat"}`, then `s.await(3000, "\"caption\"", "\"repeatOf\":0")`.
  - `statesCarryTimeLeft`: on the `your_turn` state message, `leftMs` is > 0 and ≤ 5 × `minuteMs`. On `paused` it's present.
- [ ] **Step 2:** Run `cd api && ./gradlew test --tests 'app.bolo.talk.TalkRelayAcceptanceTest'`. Expected: the six new tests FAIL.
- [ ] **Step 3: Implement in `TalkConversation`:**
  - a `lastYouMs` field and a `youSent` flag;
  - `leftMs()`;
  - the extra fields on the existing `message(...)` calls;
  - a helper `dropLive()` that sends `you_drop` when `youSent`, called in `cue` (only when it comes from `UNCLEAR` mid-turn), `pause` (when `phase` was `YOUR_TURN`) and `repeatOnRequest`;
  - `youSent` reset in `played` and `commit`;
  - `repeatOf`, by remembering the previous `buddyTurn` in `repeatOnRequest` and adding it in `say(...)` for that turn index.
- [ ] **Step 4:** Run the same command. Expected: all PASS. Then run the whole `app.bolo.talk.*` package: no regressions.
- [ ] **Step 5: Commit:** `feat(api): live words, drop, repeat mark and time left on the talk socket`.

### Task 2: The Talk view setting (api)
**Files:**
- Create: `api/src/main/resources/db/migration/V25__talk_view.sql`. It adds `talk_view text not null default 'chat' check (talk_view in ('chat','classic'))` to `learner_settings`.
- Modify:
  - `learner/domain/Settings.java` (adds `talkView`; `DEFAULTS` = normal, captions on, chat);
  - `LearnerView.SettingsView`;
  - `UpdateSettingsUseCase`;
  - `SettingsService`;
  - `JdbcSettingsAdapter`;
  - `SettingsController` (`SettingsRequest(String buddySpeed, boolean captions, String talkView)`; a null `talkView` keeps the current one);
  - `GuestService`;
  - `TalkSessionService`, if it builds `Settings`.
- Test: `api/src/test/java/app/bolo/learner/SettingsAcceptanceTest.java`

**Interfaces:**
- `GET /api/me` → `settings.talkView` is `"chat"` or `"classic"`.
- `PUT /api/me/settings {buddySpeed, captions, talkView?}`: a bad `talkView` gives 422 `bad-settings` (the existing refusal code for a bad `buddySpeed`).

- [ ] **Step 1: Write the failing tests:**
  - `talkViewDefaultsToChat`: a new guest's `/api/me` has `$.settings.talkView == "chat"`.
  - `talkViewSavesAndSurvivesOtherChanges`: PUT classic, then PUT without `talkView`; it stays classic.
  - `aBadTalkViewIs422`.
- [ ] **Step 2:** Run `./gradlew test --tests 'app.bolo.learner.SettingsAcceptanceTest'`. Expected: FAIL.
- [ ] **Step 3:** Implement the column and carry it through the files above.
- [ ] **Step 4:** Run the same command, then `./gradlew test`. Expected: all PASS.
- [ ] **Step 5: Commit:** `feat(api): the Talk view setting (chat or classic)`.

### Task 3: The chat model and clock (web reducer)
**Files:**
- Modify: `web/src/talk/talkState.ts` and `web/src/talk/useTalk.ts` (dispatch the new messages)
- Test: `web/src/talk/talkState.test.ts`

**Interfaces:**
- `export type ChatTurn = { turn: number; who: "bolo" | "you"; text: string };`
- `TalkState` gains:
  - `chat: ChatTurn[]`;
  - `live: { turn: number; text: string } | null`;
  - `clock: { leftMs: number; at: number; paused: boolean } | null`;
  - `replay: number | null`: the Bolo turn being re-revealed by a repeat.
- `ServerMessage` gains `you`, `you_drop`, `turn_end.text`, `caption.repeatOf`, and `leftMs` on `state` / `paused` / `resumed`.
- `export function timeLeftLabel(clock: TalkState["clock"], now: number): string | null`
- `export function leftNow(clock, now): number` (ms; frozen while paused).
- Caption sentences for the same Bolo turn join into one `bolo` chat turn. A caption with `repeatOf` doesn't add a turn: it sets `replay = repeatOf` (the reveal runs again on that bubble). The Classic `caption` and `captionTurn` keep working exactly as today.

- [ ] **Step 1: Write the failing tests:**
  - `liveWordsBuildTheDashedBubble`: `you` turn 1 "hello" then "hello bolo" → `live` = `{turn:1, text:"hello bolo"}`, and `chat` is unchanged.
  - `turnEndSettlesTheBubbleWithTheSavedText`: → `chat` ends with `{turn:1, who:"you", text:"Hello Bolo."}`, and `live` is null.
  - `aLateYouAfterTurnEndIsIgnored`: `you` turn 1 after `turn_end` turn 1 → `live` stays null.
  - `youDropRemovesTheLiveBubble`.
  - `captionSentencesJoinOneBoloBubble`: two captions with turn 2 → one chat turn, "A. B.".
  - `repeatReplaysTheExistingBoloBubble`: a caption turn 4 with `repeatOf` 2 → `chat` length unchanged, `replay` = 2.
  - `timeLeftLabels`:
    - 222000 → "3:42 left";
    - 61000 → "1:01 left";
    - 60000 → "1 minute left";
    - 1000 → "1 minute left";
    - 0 → "Wrapping up";
    - −5000 → "Wrapping up";
    - a null clock → null.
  - `clockStopsWhilePaused`: `paused` with `leftMs` 100000 at t, then `leftNow` at t + 30 s = 100000. After `resumed` with `leftMs` 100000 at t2, `leftNow` at t2 + 10 s = 90000.
- [ ] **Step 2:** Run `cd web && npx vitest run src/talk/talkState.test.ts`. Expected: FAIL.
- [ ] **Step 3:** Implement the reducer cases, the two helpers and the `useTalk` dispatch of `you`, `you_drop` and the extended fields. Stamp `at` with `Date.now()` when dispatching.
- [ ] **Step 4:** Run the same command, then `pnpm -s test`. Expected: PASS.
- [ ] **Step 5: Commit:** `feat(web): the talk's chat model and server-synced clock`.

### Task 4: The mic level (web)
**Files:**
- Create: `web/src/talk/level.ts` (`export function levelOf(samples: Float32Array): number`: RMS scaled to 0–1 with `min(1, sqrt(rms) * 2)` and 0 for silence).
- Modify: `web/src/talk/useTalk.ts`. In the worklet's `onmessage`, compute `levelOf(ev.data)` and publish it at most every 50 ms through a `level` state that `useTalk` returns (`level: number`).
- Test: `web/src/talk/level.test.ts`

- [ ] **Step 1: Failing tests:**
  - `silenceIsZero`: a zero buffer → 0.
  - `aLoudToneIsNearOne`: a 0.5-amplitude sine → ≥ 0.8.
  - `aQuietToneIsLow`: 0.01 amplitude → < 0.3.
- [ ] **Step 2:** Run `npx vitest run src/talk/level.test.ts`. Expected: FAIL.
- [ ] **Step 3:** Implement `levelOf` and the throttled publish in `useTalk`.
- [ ] **Step 4:** Run it again. Expected: PASS.
- [ ] **Step 5: Commit:** `feat(web): mic level from the capture frames`.

### Task 5: TalkChat on the talk screen (web)
**Files:**
- Create: `web/src/talk/TalkChat.tsx` (`TalkChat({ chat, live, replayTurn, revealWords, status, onRepeat })`) and `web/src/talk/TimeLeft.tsx` (`TimeLeft({ clock })`, ticking once a second).
- Modify:
  - `web/src/talk/TalkScreen.tsx` (props gain `talkView: "chat" | "classic"` and `level: number`). The chat layout follows the DesktopTalk and ScreenConversation cards. Classic is today's markup, unchanged.
  - `web/src/styles/bolo.css`: port the `bo-chat*`, `bo-timeleft*`, `bo-level*`, `bo-mic--noarc` and `dk-talk__chat` rules from `design/design-system/components/bundle.css` (the "Talk chat" section), plus a phone rule (`@media (max-width: 720px)`) placing the chat from 104px below the top to above the sun.
  - `web/src/onboarding/screens/FirstTalkScreen.tsx`: passes `talkView={me?.settings?.talkView ?? "chat"}` and `level`.
- Test: `web/e2e/talk-chat.spec.ts`. It reuses `talk.spec.ts`'s fake socket pattern: the fake server sends the messages.

- [ ] **Step 1: Write the failing e2e tests:**
  - "the talk shows as a chat: Bolo's line, my live words, then my settled turn":
    - send caption turn 0 "So, what did you work on today?", `buddy_done` 0, and `state your_turn leftMs 222000`;
    - the log shows Bolo's bubble, and "3:42 left" is visible;
    - send `you` turn 1 "I fixed" then "I fixed a bug" → the dashed bubble (`.bo-chat__turn--live`) reads "I fixed a bug";
    - send `turn_end` turn 1 "I fixed a bug." → a solid "You" bubble, and no live bubble.
  - "time left reads 1 minute left, then Wrapping up": `state` with `leftMs` 30000 → "1 minute left"; with −1000 → "Wrapping up".
  - "pause stops the clock": `paused leftMs 100000`; after 2 s it still reads "1:40 left".
  - "a dropped turn disappears": `you`, then `you_drop` → no live bubble.
  - "repeat replays Bolo's bubble, no duplicate": a caption with `repeatOf` → still exactly one bubble with that text.
  - "the live bubble is hidden from screen readers": `aria-hidden="true"` on `.bo-chat__turn--live`, and the log has `aria-live="polite"`.
  - "a long live turn stays in view": 40 `you` updates growing to about 600 characters → the live bubble is in the viewport.
  - "scrolled up, the chat stops following and Latest brings it back" (390×844): eight turns, scroll the log to the top, send a new caption → "Latest" is visible; click it → the newest bubble is in view.
  - "the meter moves on my turn and rests while Bolo speaks": on `your_turn`, `.bo-level` has no `bo-level--rest`; on `buddy_speaking` it does.
  - "Classic view keeps the caption and the arc": `/api/me` with `settings.talkView` "classic" → no `role=log`, the caption is visible, and the `.arc` is visible.
  - "Captions off hides the chat": click CC → no log.
  - "fits at 390 px": no sideways scroll.
- [ ] **Step 2:** Run `npx playwright test e2e/talk-chat.spec.ts --project=firefox`. Expected: FAIL.
- [ ] **Step 3:** Implement `TalkChat`, `TimeLeft`, the chat layout in `TalkScreen`, the CSS, and the `FirstTalkScreen` props.
  - Bolo's latest turn reuses today's word reveal (`.w` / `.w.on`).
  - The status line replaces today's caption-borne status text in Chat view.
  - The hint card and "Keep Bolo slower?" keep their positions.
- [ ] **Step 4:** Run the same command, then `npx playwright test e2e/talk.spec.ts --project=firefox` (the existing talk tests, run in Classic or Chat as they already expect: if they assert the caption, they set Classic in their `/api/me` stub). Expected: all PASS.
- [ ] **Step 5: Commit:** `feat(web): the talk as a live chat, with time left and a level meter`.

### Task 6: Settings → Talk view (web)
**Files:**
- Modify: `web/src/me/SpeakingSettings.tsx` (a "Talk view" row with chips "Chat" and "Classic", saving at once like Captions), the `LearnerView.settings` type in `web/src/api/browser.ts`, and the settings client.
- Test: `web/e2e/me.spec.ts`.

- [ ] **Step 1: Failing e2e test:** "Talk view switches between Chat and Classic". Me → Classic → the PUT body has `talkView: "classic"`, and the Classic chip is selected.
- [ ] **Step 2:** Run it. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run `pnpm -s typecheck && pnpm -s lint && pnpm -s test`, then all Firefox e2e. Expected: PASS.
- [ ] **Step 5: Commit:** `feat(web): Settings, Talk view`.

### Task 7: Ship
- [ ] Add a RUNBOOK section, "Talk view: the live chat": what the learner sees, the new socket messages, Classic, and Captions.
- [ ] Run `scripts/ship.sh`. The deploy smoke and the four live probes must pass. The LiveTalkProbe must still read Bolo's caption.
