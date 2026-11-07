# Presence: the companion and the scenes

The Buddy sits **beside** the learner, not across from them. Talking side by side, looking at the same view, removes the feeling of being watched (E9). This is Bolo's most distinctive surface, and it gets the most care.

## Art direction

- **Flat, layered, mid-century.** Three to five depth planes of solid shapes (sky, sun, far ridge, near ground, figures), separated by value, not outline. No textures, no 3D, and no gradient anywhere except the sky and the sun's glow.
- **Dusk, always.** Use the `scene-*` tokens. The sky blends indigo → violet → dusty rose; the mustard sun sits low and partly set behind violet hills; the companion is rim-lit in `scene-sun` on the side facing it.
- **Stars come out as the light fades.** About 26 dots of star dust, denser high in the sky, thinning toward the horizon, never in front of the sun. Two bright four-point stars, each with a small soft glow; the brighter one is the evening star (সন্ধ্যাতারা). Keep both in open sky, clear of captions and buttons.
- **The sun's আভা.** A soft radial glow fades from the disc into the sky. No rings, outlines or rays.
- **The companion is a silhouette with personality in posture**, not in face. Show them from behind or in three-quarter profile, never face-on while talking. Faces appear only in the debrief "turn to you" frame, as a simple, warm expression.
- **Three companions** (PRS-6): a man, a woman, and one friendly non-human character. All read as friends: relaxed shoulders, open posture, nothing romantic or flirtatious in pose or styling. No borrowed characters, and no horse.
- **Leave the top 40% calm.** Captions sit there on `scene-plate`; star dust may pass behind the plate, but nothing bright sits under text.

## Scenes (MVP)

| Scene | Planes | Ambient | Default |
| --- | --- | --- | --- |
| Hilltop at dusk | Three-step sky with stars, glowing sun, violet far ridge, near hill with both figures | Wind, crickets, a distant bird | Yes, ships in the APK |
| Evening drive | Three-step sky with fewer stars through the windscreen, the low sun on the road ahead, a violet skyline with warm windows, blurred street lights and tail lights, the cabin and a soft dashboard glow; the companion drives | Soft engine hum, indicator ticks | Downloaded once, ≤ 3 MB |

### Evening drive

- **The companion drives; the learner rides beside them.** A driver looks at the road, so the companion never looks at the learner while talking, and Bolo never shows the learner driving while on a phone. Bangladesh drives on the left, so the driver sits on the right, seen three-quarter from behind.
- **Value layers:** the cabin (roof, pillars, dashboard) is `scene-sky-high`, one step lighter than the companion and wheel in `scene-hill-near`, so the companion is always the nearest, darkest shape.
- **Light:** the sun sits on the road's vanishing point, the skyline crosses it, and the companion is rim-lit on the side facing it. City lights are soft blurs of `scene-sun`; tail lights are `scene-sky-low`.
- **Keep the frame:** everything important sits between x 60 and 300 of the 360 × 520 art, because tall phones crop the sides. The top 40% is windscreen sky for the caption; the bottom third is dashboard for the controls.

## Companion states (PRS-3)

Build them in Rive as one state machine with five inputs. Every state runs at ≥ 30 fps on a 2 GB device or falls back to a still.

| State | Motion | Rule |
| --- | --- | --- |
| Idle | Breath: shoulders rise 2% over 4 s | Only on the companion, never on the whole scene |
| Speaking | Looks ahead at the view, a small hand gesture on stressed words | Never turns to stare at the learner |
| Listening | Head turns 10–15° toward the learner, a nod every 6–10 s | Nods are irregular so they never feel mechanical |
| Glance | A brief look and smile after a long or strong answer | At most once a minute |
| Facing | Turns to face the learner for the strength card, then back to profile | Debrief only |

The only other moving things are the two bright stars (see *Motion, haptics and sound*) and, on the drive, tail lights drifting very slowly and a street-light blur passing every 8–12 s. The sky, the star dust and the road never move.

## Voice-only mode (PRS-7)

The scene becomes `scene-sky-high` with the mic ring alone, and ambient sound turns off. It is for low data, low battery or discretion. It is a first-class mode, not a degraded one.

## Mouth-position diagrams

Seven side views cover the six sound patterns in ASM-2: v, w, th, s, sh, z, and starting a word with s ("school", never "ischool"). They appear on a sound fix card after the same sound has been missed in 3 sessions (US-5.6), always for hard-of-hearing learners (US-9.3), and whenever a learner asks how.

- **One frame for all.** A close-up cut-through of the mouth, facing right. Only what changes between sounds moves, so learners compare shapes, not drawings.
- **One coloured part.** Flesh is `mouth-flesh`, the inside of the mouth is `surface`, teeth are `mouth-tooth`, every edge is a 2px `ink` line. The part the learner must move (lower lip, both lips, or tongue) is `mouth-move` with a `hills-ink` edge.
- **Air and voice.** Air is a dashed arrow leaving the lips. z adds three small buzz marks in the throat; that is its only difference from s.
- **Motion.** The moving part travels from rest into place three times, then holds (see MouthDiagram). Built in Rive for the app; still under reduced motion.
- **No labels, no jargon** inside the drawing. The card's words name the sound ("v as in *van*") and give the one-line tip.

A speech or pronunciation teacher must check all seven drawings before release: a wrong tongue position teaches the wrong sound.
