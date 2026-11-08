# Golden sets (Gate 1 accuracy harness)

A golden set is a folder of recordings plus the teacher's notes. The harness compares Bolo's sound flags with those
notes and writes `report.md` in the folder. Run it with `scripts/golden-set.sh golden-sets/<name>`. The full guide
is in docs/RUNBOOK.md, under "Accuracy harness (Gate 1)".

**Never commit real recordings.** Everything here is git-ignored except this file and `synthetic/`. Real speakers'
audio (GMU archive, local volunteers) is personal data and comes under its own licence.

## Folder layout

```
golden-sets/<name>/
  manifest.json      the recordings and the teacher's labels
  *.wav              16 kHz, mono, 16-bit PCM, at most 60 s each
  cache/azure/*.json each vendor result, saved so reruns are free (written by the harness)
  report.md          written by the harness
```

Convert other formats first, for example: `ffmpeg -i in.mp3 -ar 16000 -ac 1 -sample_fmt s16 out.wav`.

## manifest.json

```json
{
  "name": "gmu-bengali",
  "source": "GMU Speech Accent Archive, Bengali speakers",
  "licence": "CC BY-NC-SA 4.0 (permission requested 2026-11-08)",
  "recordings": [
    {"id": "bengali1", "audio": "bengali1.wav", "labelledBy": "teacher initials",
     "reference": "Please call Stella. Ask her to bring these things with her from the store: ...",
     "issues": [
       {"word": "Stella", "pattern": "S_CLUSTER_VOWEL"},
       {"word": "her", "nth": 2, "pattern": "Z_J"}
     ]}
  ]
}
```

- **`reference`:** exactly what the speaker read.
- **`issues`:** one entry per word where the teacher heard the swap. Words with no swap are left out; they count as "fine".
- **`nth`:** needed when the word appears more than once in the reference (1 = first).
- **`pattern`:** one of these:

| `pattern` | The sound | What the teacher listens for |
|---|---|---|
| `V_BH` | v as in van | v said as bh/b ("very" → "bery") |
| `W_V` | w as in we | w said as v ("we" → "ve") |
| `TH_TD` | th as in think | th said as t/d ("three" → "tree", "the" → "de") |
| `Z_J` | z as in zoo | z said as j ("zero" → "jero") |
| `S_SH` | sh as in ship | sh said as s ("ship" → "sip") |
| `S_CLUSTER_VOWEL` | s at the start | an extra vowel before s + consonant ("school" → "ischool") |

Judge **clarity, not accent** (PRD US-3.2). Label a word only if the swap changes or blurs the word, not just
because it sounds Bangladeshi.

## Collecting local speakers (2026-11-09)

Aim for **20 people reading once each** (12 at the least), with a mix of English levels (including fluent speakers,
to catch wrong flags), genders, ages and home districts (Dhaka, Sylhet, Chittagong and others).

**How to record:**
- Use a phone voice recorder in a quiet room, held about 20 cm from the mouth.
- Read once at a normal pace, with no retakes unless they badly stumble.
- Name the files speaker01, speaker02 and so on.
- Keep a sheet with: age range, gender, home district, English level (weak / okay / good).

**Consent** (spoken or written): "My recording will be used privately to test Bolo's pronunciation feedback. It won't be
shared or published, and I can ask for it to be deleted."

**The paragraph** (our own text, about 30 s):

> Every Wednesday, my mother and I walk to the small village station. We usually buy fresh fish, seven green
> vegetables and a bag of sweet oranges. She always says, "Speak slowly and stay calm," when we visit the busy street
> market. I think the three of us will drive to the zoo next week. Then we will visit my old school and share a story
> with the teachers.

Each reading gives about:
- 9 s-at-the-start chances (small, station, sweet, speak, slowly, stay, street, school, story);
- 6 v (every, village, seven, vegetables, visit, drive);
- 6 or more w (Wednesday, walk, we, will, week);
- 7 or more th (mother, think, three, then, with, the);
- 6 or more z (zoo, busy, always, says, oranges, teachers);
- 4 sh (she, fresh, fish, share).
