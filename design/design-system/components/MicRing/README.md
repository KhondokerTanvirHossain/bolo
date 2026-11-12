# MicRing

The conversation's heartbeat: whose turn it is, plus the time left, shown as an arc with no numbers.

**Needs:** a `state` (`buddy` · `turn` · `thinking` · `paused`), the time-remaining fraction (0–1), and the live input level (0–1) during `turn`.

**States**
- `buddy`: translucent core with `graphic_eq`. The Buddy is speaking, so there is nothing to do.
- `turn`: the core turns `sun` with `shadow-glow`; one pulse, a `CLOCK_TICK` haptic and a TalkBack "Your turn". While the learner speaks, the core scales gently with voice level. There is no button to hold.
- `thinking`: a thin arc orbits the core, shown only after 600 ms. Over 4 s, "Your Buddy is thinking…" appears under the caption.
- `paused`: `pause` glyph. Tap anywhere to continue.

**Time arc:** a 3px track around the ring that shrinks linearly. It never shows numbers and never changes colour near the end.

**Since 2026-11-12 (Chat view, the default):** the time left shows as numbers in the top bar (TalkChat `bo-timeleft`), so the ring drops its arc (`bo-mic--noarc`), and a five-bar level meter (`bo-talklevel`) sits beside it. The arc above is the Classic view.

**Don't:** show a level meter with numbers, a red recording dot, or turn anything red near the end.
