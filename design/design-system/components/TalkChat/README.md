# TalkChat

The talk as a live chat in the sky above the companion (TLK-8 and TLK-16, changed 2026-11-12), with the time left and a mic level meter. DesktopTalk and ScreenConversation use it in Chat view, the default in Settings → Talk view.

**Needs:**
- the settled turns, each with who said it (Bolo or you) and the text;
- Bolo's current line with word timings, for the reveal;
- the learner's live recognised text while they speak;
- the seconds left;
- the live input level (0–1).

**Use**
- **Bolo's turn** (`bo-chat__turn--bolo`): on the left, with a rose edge on `scene-plate`. Words not yet spoken wait at 55% and come up as the voice reaches them. Repeat sits on Bolo's latest turn only, and repeating replays that bubble's reveal (no second bubble).
- **Your turn** (`bo-chat__turn--you`):
  - On the right, with a sun edge.
  - While you speak, the bubble is dashed (`--live`), with a caret, and shows exactly what Bolo hears, mistakes included.
  - When the turn ends, it becomes solid and keeps the text saved for your feedback.
  - It updates at most a few times a second and never reflows the turns above it.
  - If the turn isn't kept (Bangla or unclear speech, a pause mid-turn), the dashed bubble disappears and Bolo's next line follows.
- **Status line** (`bo-chat__status`): "Bolo is thinking…", "Paused. Tap to continue", connecting and error lines. It sits under the last turn as plain text, not as a bubble.
- **Following the talk:** the newest line is always in view, and older turns fade out at the top (`mask-image`). If the learner scrolls up, the chat stops following and shows **Latest**; tapping it scrolls down and follows again.
- **Time left** (`bo-timeleft`): numbers ("3:42 left"). The last minute reads "1 minute left", and after time is up "Wrapping up" while Bolo says goodbye. Same colours throughout, no sound, and each change is announced once. It stops while paused.
- **Level meter** (`bo-talklevel`): five bars that move with your voice on your turn, and rest (`--rest`) while Bolo speaks or thinks. It is decorative (`aria-hidden`), because the mic ring carries the state.
- **Accessibility:** the chat is `role="log"` with `aria-live="polite"`, so a screen reader hears each settled turn once and never the live words. The Captions setting (C on desktop) hides the whole chat in Chat view, and the caption in Classic.
- **Other overlays:** the hint card and the "Keep Bolo slower?" question stay where they are and sit over the chat while shown.

**Don't:**
- show scores or corrections in the chat (they belong in the debrief);
- turn the time red or beep;
- cover the sun or the figure;
- jump the learner back down while they're reading.
