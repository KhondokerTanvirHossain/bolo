# BuddyCaption

The live caption of the Buddy's line on `scene-plate`, top of the conversation.

**Needs:** the current line's text, with word timings so spoken words can be revealed, and a `replay` handler.

**Use**
- Set it in `buddy-line` (22/30, ≥ 18sp always) in `on-scene`. Words not yet spoken wait at 55% opacity and come up as the voice reaches them.
- The replay button (`replay`, 40dp, 48dp hit area) repeats the last line, the same as saying "Sorry?".
- The caption stays until the learner's turn ends (US-9.3), then cross-fades to the next line.
- Toggle it in settings. Captions are on by default.

**Since 2026-11-12:** this caption is the **Classic** talk view (Settings → Talk view). The default **Chat** view shows Bolo's lines and the learner's own words as a live chat (TalkChat).

**Don't (Classic):** caption the learner's own words, or stack more than one Buddy line.
