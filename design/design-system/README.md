Bolo is a voice-first speaking buddy for Bangla speakers who know English on paper but freeze when they speak. The whole system serves one feeling: **sitting beside a friend on a hill at dusk, as the first stars come out, talking with no one judging you.** Everything else (the Coach, the scores, the drills) arrives later, quietly, in the learner's own voice.

The visual direction is **Neon Dusk**: an indigo night, a mustard sun going down, violet hills and a soft rose glow, painted flat in a mid-century animation spirit. The mood borrows from the colour world of adult animation's Los Angeles twilights; it borrows no character, typography or motif.

When two design options compete, pick the one that keeps the learner talking without fear.

**Platforms.** Bolo ships in the browser first: the desktop browser (closed beta), then the mobile browser, then the Android app. The tokens, components and rules here apply everywhere; what changes on a computer and in a browser is in *Desktop and web*. The two clickable prototypes (group Prototypes) are the locked v1 baseline.

## Design principles

1. **Calm is the feature.** Anxiety is the enemy, so the interface never hurries, flashes, buzzes or counts down. There are no red states, no numbers while you talk, no broken-streak graphics. Motion is slow to start, quick to settle, and only tiny things (two stars) ever loop.
2. **One thing at a time.** Each screen has one filled button (`sun`), one card at most, and one fix on screen at once. Use the PRD's "max 3 of anything" rule as a layout rule: 3 tabs, 3 fixes, 3 skill levels.
3. **Your voice is the sun.** The Talk button and the your-turn mic ring are the same mustard as the setting sun in the scene. Audio comes before text on every feedback surface, and the learner's clip is drawn in neutral `clip-you`: their voice is never coloured good or bad.
4. **Three characters, three lights.** Mustard `sun` means "act". Violet `hills` is the Coach, after the talk. Dusty `rose` belongs to the Buddy, during it. A learner should know whose moment it is from colour alone.
5. **Built for the bus.** On a phone, design at 360dp on a 2 GB Android phone on 3G, held in one hand; on a computer, see *Desktop and web*. Primary actions sit in the bottom third; nothing below 48dp is tappable; blur and animation degrade to solid fills and still scenes.
6. **Defer, don't decorate** (Apple's deference). Chrome recedes so the scene and the voice lead. Gradients exist only in the sky, there is no emoji, and no illustration that isn't the companion, the scene or a mouth tip.

## Colour

Two themes follow the system: **Day** (lavender paper) and **Night** (indigo). Scene colours stay the same in both, because the scene is always dusk.

- **Surfaces.** Put every screen on `canvas`, raised cards on `surface` with `shadow-card`, and recessed fills (clip wells, secondary buttons, tracks) on `surface-sunken`, which stays neutral grey by day so it never blends with violet tints. Floating chrome uses `material-bar`; modal sheets dim the screen with `scrim`.
- **Text.** Use `ink` for primary text and `ink-secondary` for supporting text. Keep `ink-tertiary` for disabled text only. Hairlines are `separator`; borders that carry meaning (chips, checkbox, toggle off) are `line-strong`.
- **The mustard sun is the only action colour.** `sun` fills the Talk button, the one primary button per screen, the your-turn mic ring, streak suns and toggles. Text and glyphs on it are always `on-sun` (dark brown), never white. By day, mustard almost disappears into lavender paper, so every sun-filled control carries a 1.5px `sun-edge` outline; at night `sun-edge` equals `sun` and the fill simply glows. For mustard text, use `sun-ink`.
- **Violet hills belong to the Coach.** The model-voice waveform, the Coach mark, skill meters, the mouth tip and the strength card use `hills` / `hills-soft` / `hills-ink`. At night `hills` lightens to a twilight violet so it stays legible on indigo.
- **Dusty rose belongs to the Buddy.** `rose` is the horizon glow and the Buddy mark; `rose-ink` on `rose-soft` is the streak pill and story hooks. It was softened from neon on purpose; never push it back to full saturation, and never use it for a result.
- Use the aliases `buddy` and `coach` in code, not the raw hues. If Phase 0 chooses one character with two modes, repoint `coach` and nothing else changes.
- **Results are never red.** Nailed it = `nailed` on `nailed-soft` with a check. Closer = `closer` on `closer-soft` with an up-right arrow. Once more = `once-more` on `surface-sunken` with a replay arrow. Because `closer` sits near mustard in hue, the icon and the word are mandatory, never optional (AX-5).
- **`critical` is quarantined** to Me → Privacy (delete recording, delete everything). It never appears in the core loop.
- **Focus** is a 2px `focus-ring` outline, 3px outside the control, ≥ 7.6:1 on canvas in both themes.

Every text pair named in a token's usage note passes WCAG AA (4.5:1) in both themes; control borders, the sun edge and the focus ring pass 3:1.

## Typography

**Anek Bangla sets everything, in both scripts.** It is one family drawn for Bangla and English together, so a Bangla helper line under an English sentence reads as the same voice, and switching languages never changes the tone. It is a variable font (weight 100–800, width 75–125), so one file serves every size; bundle it in the app rather than downloading it, and fall back to Noto Sans Bengali, Android's own Bangla face.

- Build hierarchy from weight and size together, never size alone. In practice there are three weights: 400–500 for reading, 650 for emphasis and titles, 700–750 for the few display moments and big numbers.
- Track English display sizes slightly tighter (`display` −0.02em → `body` 0). **Never track Bangla**: letter-spacing breaks its conjuncts.
- Anek runs slightly narrow. When a line is too long at 360dp, reduce the width axis (to about 90) before reducing the size.
- Use `buddy-line` (22/30) for live captions; it never drops below 18sp. Use `sentence` (19/28) for any sentence the learner said or will say.
- Use `micro` for eyebrows ("You said", "Try") in sentence case. Bolo never sets text in all caps.
- Bangla helper text sits **under** the English in `bangla-helper`, in `ink-secondary`, on onboarding and error screens. Its leading is taller (22 on 14) for matras and conjuncts.
- Everything scales with the system font size up to 200% (AX-4). Layouts wrap; they never truncate an instruction.

## Layout and spacing

- Use a 4dp grid. The screen margin is `space-5` (20dp) everywhere.
- Divide the screen into thirds: status and context at the top, content (scene, card) in the middle, the one action in the bottom third. The Talk button, Try it and Done always sit in the bottom third (R11).
- Space cards `space-6` apart and keep card padding at `space-5`. Section gaps are `space-8`.
- Target sizes: `tap-min` 48dp minimum, `button-height` 56dp, `talk-size` 128dp, `mic-ring` 96dp.
- Design at 360dp first, then check 412dp. Content never stretches past 412dp of reading width; on wider screens it centres.

## Shape

- Anything you press is a capsule or a disc (`radius-full`). Anything you read is a soft rectangle: cards use `radius-lg` (20), sheets `radius-xl` (28) at the top, tiles `radius-md` (18).
- Use continuous (squircle) corners wherever the platform allows. On Compose, use a smooth-corner shape at 60% smoothing for cards and sheets; capsules don't need it.
- Nest radii concentrically: inner radius = outer radius − padding.

## Depth and materials

- **Day:** cards float on a soft violet-tinted `shadow-card`. **Night:** shadows vanish on indigo, so cards get a 6% white hairline instead (the same token, per theme).
- **Glow is reserved.** `shadow-glow` belongs only to the Talk button and the your-turn mic ring, because they are the sun.
- **Materials.** The tab bar uses `material-bar`; captions and hints over the scene use `scene-plate`. On Android 12+ add a 16–20dp backdrop blur. On Android 9–11, on low-end devices, or with "Reduce transparency", the fill alone is the material; it was tuned to pass contrast without blur.
- Dim to focus: a modal sheet (End session) gets `scrim`. A hint card is non-blocking, so it gets no scrim.

## The scene

The sky is three flat-blended steps (`scene-sky-high` → `scene-sky-mid` → `scene-sky-low`), never a two-colour jump. Star dust (`scene-star`, about 26 dots at 50–85% opacity) gathers high in the sky and thins toward the horizon. Two bright four-point stars twinkle slowly, out of step; the brightest is the evening star (সন্ধ্যাতারা). The sun carries a soft radial glow (আভা) that fades into the sky, with no rings or outlines. Full art direction is in *Presence: the companion and the scenes*.

## Iconography

- Use **Material Symbols Rounded**: weight 500, grade 0, optical size 24, outlined by default and filled for the selected or active state.
- Core glyphs: `mic` (talk, try it), `graphic_eq` (Talk tab, Buddy speaking), `insights` (Progress), `person` (Me), `play_arrow` / `pause`, `replay` (repeat that), `slow_motion_video` (play slower), `lightbulb` (hint), `check_circle` / `north_east` / `replay` (results), `wb_sunny` (streak day), `bedtime` (rest day), `landscape` (scene).
- Icons are never the only label of a primary action. Pair them with a word, or give them a TalkBack label at minimum.
- The name is **Bolo**, and the wordmark is always in English letters, never বলো. The logo is the talking sun (see Logo): a mustard sun-bubble with a light face over the violet hill. The face belongs to the logo only; it never appears on app screens, inside a conversation or as a spinner.

## States

- **Pressed:** scale to 0.97 (0.95 for the Talk disc) on touch-down, spring back on release.
- **Disabled:** `surface-sunken` fill and `ink-tertiary` label. Explain why nearby ("Tick the box to continue") rather than relying on the grey.
- **Loading:** never a spinner on canvas. Waiting is either "Your Buddy is thinking…" (conversation) or the violet listen-back waveform (scoring).
- **Empty and error:** use the StatePanel: one icon, one headline, one Bangla helper line, one action (R7).

## Using this system

- Tokens are in `tokens.json`; `tokens.css` is the compiled CSS variables. On Android, map colours to a `BoloColors` object with Day and Night instances, type styles to `TextStyle` (px = sp), and spacing, radius and size tokens to `Dp`.
- `components/bundle.css` is the reference implementation of every component as plain HTML/CSS. Read each component's README for what it needs and how it behaves; treat the CSS as the spec for values.
- Motion values are under *Motion, haptics and sound*. Copy rules and the banned-word list are under *Voice and copy*.
