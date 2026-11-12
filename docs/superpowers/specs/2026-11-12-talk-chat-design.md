# The talk as a live chat: design

Founder request, 2026-11-12. Bolo is a conversation, so it should read like one:
- turns appear one by one, in real time;
- what the learner is saying shows live;
- earlier turns stay on screen;
- there is a countdown and a mic level meter.

The PRD's TLK-8 and TLK-16 were changed the same day to match.

## Why
- **Today the learner sees only Bolo's current line.** Their own words never appear, so it's unclear whether Bolo heard them. The previous turn also vanishes, so the talk has no visible thread.
- **A chat makes the conversation feel real and checkable.** Words appearing live show that Bolo hears you. The thread lets you glance back at what Bolo asked. The countdown and level meter answer "how long do I have" and "is my mic working" without guesswork.

## Goals
- **The chat sits in the sky above the companion scene** (the founder chose "B in the sky"). The scene itself is unchanged: same sky, sun, hills and figure, same art for every scene.
  - Bolo's turns are on the left; the learner's are on the right.
  - The newest line is always at the bottom and in view. Older turns fade out toward the top.
  - The whole talk can be scrolled back.
- **Bolo's turn** appears word by word as it speaks (today's caption reveal), in its bubble, with Repeat on its latest turn.
- **The learner's turn** appears live while they speak, exactly as the recogniser hears it, in a dashed "speaking" bubble. When the turn ends it becomes a solid bubble holding the recogniser's final text.
- **A countdown** of the time left in numbers ("3:42 left") sits in the top bar. It reads "1 minute left" during the last minute: no colour change, no sound. It stops while paused.
- **A mic level meter** sits next to the mic: a few bars that move with the learner's voice on their turn and rest while Bolo speaks or thinks.
- **Settings → Talk view: "Chat" (default) or "Classic".** Classic is today's screen: Bolo's caption only, the learner's words not shown, the time arc with no numbers. The choice is stored with the learner's other settings, so it follows them across devices.
- **Both phone and desktop,** from the same components.

## Non-goals
- No change to the scene art, the scene picker or the companion figure.
- No scores, marks or corrections in the chat during the talk. Feedback stays in the debrief.
- No editing of the learner's words, and no typing: it's a speaking app.
- No change to how turns are taken (Done, Space, silence), to hints, to crisis care, or to the end-of-talk and debrief flow.

## Anti-goals (failures even if it technically works)
- **The chat covers the sun or the figure,** or the scene shifts to make room.
- **The learner's live words flicker** or jump around so much that they're hard to read, or reflow the whole chat on every partial.
- **The chat yanks the learner back to the bottom** while they've scrolled up to read an earlier turn.
- **The countdown turns red,** beeps, or otherwise adds pressure.
- **Classic view loses anything** it has today.

## Constraints
- **Design system** (Neon Dusk):
  - bubbles sit on `scene-plate` with blur, like today's caption;
  - Bolo's edge is `rose`, the learner's edge is `sun`;
  - text is in `on-scene`;
  - Anek Bangla;
  - Bolo's line keeps the caption type size on desktop and stays at least 18px on a phone; the learner's bubbles may be one step smaller.
  - No red anywhere in the talk.
- **Accessibility:**
  - The chat is a log region (`role="log"`, `aria-live="polite"`), so screen readers hear each settled turn once, not every partial.
  - The countdown is announced only at "1 minute left".
  - The meter is decorative (`aria-hidden`), because the mic state already has a label.
  - Reduced motion: no fade animation, but the reveal still happens.
- **Keys unchanged:** Space (Done), P (Pause), R (Repeat), Esc (End). CC (captions) toggles the whole chat in Chat view, and the caption in Classic.
- **Privacy:** no new data is stored. The learner's turn text is already saved for the debrief and the full chat. The live words are only sent to the learner's own screen.
- **The server already receives partial recognition** (`TranscriptListener.onPartial`, used for speculative replies). It will send the learner's words to the client as they come. They are throttled so the screen gets at most a few updates a second.

## Decided
- **Layout:** the chat in the empty sky above the sun ("B in the sky").
  - On desktop it's a centred column about 62% wide (at most 760px of reading width) that starts below the top bar and ends above the sun.
  - On a phone it's the full width minus the side gutters, from below the top bar to just above the sun.
- **What the learner sees:** exactly what the recogniser hears, mistakes included (founder).
- **The Talk view setting:** "Chat" or "Classic", with Chat as the default *(my call: the founder asked for both, and the chat is the new main experience)*.
- **CC in Chat view** hides the whole chat for listening-only practice *(my call)*.
- **The countdown** counts in whole seconds from the server's time limit. Only "1 minute left" and, after time is up, "Wrapping up" change the wording.
- **Scrolling:** auto-scroll follows the newest line unless the learner has scrolled up. A small "Latest" button brings them back down *(my call)*.

## Settled after the builder check
These are all my calls; the founder can change any of them.
1. **The text that settles into the learner's bubble** is the text saved for the turn: the last recognised words, which the debrief and the full chat already use. There's no second rewrite with the later punctuated text, so the chat, the debrief and the full chat always agree.
2. **Live words that never become a turn** (Bangla or unclear speech, a pause mid-turn, R mid-speech): the dashed bubble disappears. The server says so with an explicit "drop" message, so the screen never guesses.
   - **Repeat (R)** replays the reveal in Bolo's existing latest bubble. It never adds a second copy.
3. **After time is up,** the countdown reads "Wrapping up" while Bolo says goodbye and any grace time runs. It never shows a negative time or freezes on "1 minute left".
4. **Captions:** the existing Captions setting (SET-2) and its keyboard shortcut now control the whole chat in Chat view, and Bolo's caption in Classic, as before.
   - A learner who had captions off keeps them off; that's what they chose.
   - The Captions switch stays in Settings, next to Talk view.
5. **The cards:** the design system already has the phone talk card (ScreenConversation), the desktop one (DesktopTalk) and the Settings card. All three are updated, and new cards are added: TalkChat, DesktopTalkClassic and ScreenConversationClassic.
   - **No product code before the founder approves**: neither the server's live words nor the timer, nothing.
6. **The other on-screen items in Chat view:**
   - "Bolo is thinking…", "Paused. Tap to continue", connecting, error and end lines show as a plain status line under the last turn, not as bubbles.
   - The hint card and "Keep Bolo slower?" stay where they are and sit over the chat while shown.
   - Classic keeps everything exactly as today.
7. **Talk view applies to guests too.** Guests are learners with their own server-side settings, like Captions today. An account keeps its choice across devices.

## Left to the builder
- The new client message carrying the learner's live words, and how often it may send. It must never block the reply pipeline.
- How the meter reads the level: from the mic stream already open in the browser. A reasonable choice is an analyser node at about 20 updates a second, smoothed.
- How the countdown stays in step with the server: start, pause and resume. It must agree with the server's own time limit to within a second.
- Exact bubble spacing, fade length and phone breakpoints, within the design system.

## How it will be shown before building
- The design system gets an updated **DesktopTalk** card and **ScreenConversation** card in the new Chat view. The Classic look stays documented.
- **BuddyCaption** and **MicRing** get their guideline changes: the learner's words are now allowed in Chat view; numbers and a level meter are now allowed.
- The founder approves those cards before any code changes.
