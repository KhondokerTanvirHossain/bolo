# DesktopTalk

The talk fills the window as a live chat (TLK-8 and TLK-16, changed 2026-11-12). The companion scene is unchanged: the conversation sits in the empty sky above the sun, so Bolo and the hill stay in view. Learners who prefer the earlier screen choose Settings → Talk view → Classic (DesktopTalkClassic).

- **Chat** (TalkChat): a centred column up to 760px wide, from below the top bar to above the sun (about 35% of the window).
  - Bolo's turns are on the left at 22/30, revealed word by word as it speaks; Repeat sits on its latest turn.
  - Your words appear live at 19/28 in a dashed bubble while you speak, and settle when your turn ends.
  - The newest line is always in view and older turns fade at the top. Scroll up to read the whole talk; it stops following until you tap Latest.
- **Top bar:** the scene button, then the time left in numbers ("Free talk 3:42 left"). This becomes "1 minute left" near the end, then "Wrapping up" once time is up, with no colour change. End sits on the right.
- **Dock:**
  - a level meter that moves with your voice;
  - the mic ring, with no time arc in this view;
  - Pause and Done.

  The keys are written underneath: Space (done), P (pause), R (repeat), Esc (end) and C (chat on or off; the same as Captions in Settings). Switching tabs pauses the talk and closes the mic.
- Canvas: 1440 × 900 (the preview scales it down to fit). Classes: `dk-*`, plus `bo-chat*`, `bo-timeleft` and `bo-level` in `bundle.css`.
