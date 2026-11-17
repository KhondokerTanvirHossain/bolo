# Founder feedback 2026-11-18 — design

Founder test after the hats (docs/product/feedback-log.md, 2026-11-18). The founder approved my proposals ("Ok go") and
asked for all four to be finished overnight: design, build and deploy; he checks in the morning. Azure Speech is on S0.

## 1. The English teacher teaches more

**Why:** "a little, very little more aggressive"; teach vocabulary, grammar and idioms; open with "Is there anything
you'd like to learn today?"

- Gates: never learner turn 1 (it answers the opener); the next teach may come on the turn right after the try
  (teach, try, teach): gap 2, was 3. At most 6 a talk in 5 minutes, 10 in 10 minutes (was 4).
- The check also teaches a stronger word, a more natural phrase, or a common everyday idiom, not only clear grammar.
  It returns a `kind`: `grammar | word | phrase | idiom`. Idioms only when people really say them today ("on the same
  page", "running late"), never textbook ones ("raining cats and dogs").
- The learner's first answer (what they'd like to learn) is passed to every check as their wish ("idioms for work"),
  so the teaching leans that way.
- Spoken line by kind: grammar `A smoother way to say that: "…" Try saying it.`; word `A stronger word for that: "…"
  Try saying it.`; phrase `A more natural way to say that: "…" Try saying it.`; idiom `An idiom for that: "…" Try
  saying it.` The sentence always ends with punctuation (deferred minor fixed).
- The Say it better card names the kind: Grammar / Better word / Natural phrase / Idiom (DS v46 ScreenTeacherTalk).
- The teacher opens with "Is there anything you'd like to learn today?" and doesn't use the free talk's memory opener
  (the memory follow-up is kept for a free talk).

## 2. The opener offers a choice

**Why:** first it was always "what did you do today", now always the last topic. The founder wants: "last time we
talked about X; that, or something else?" with a few examples from history and one weird, interesting one.

- Free talk, remembering on, with any history (a follow-up thread or a recent topic): Bolo opens with a short hello and
  ONE offer of three things, in at most two short sentences: pick up the last thing (the follow-up thread if one is
  due, else the newest recent topic); or something else: one other thing from their history (another recent topic, an
  interest, or a thread); or one odd, fun question from a new "curious" bank ("Would you rather never feel cold or
  never feel sleepy?"). Then it follows whatever they choose.
- Three options, never more (heard aloud, a longer list is lost). The choice is fixed per talk (seeded by the talk
  id), so a reconnect offers the same.
- No history, or remembering off: as today (the goal opener).

## 3. The Coach: Grammar and Vocabulary cards

**Why:** two more feedback cards, when there is something to show.

- The Coach also returns, when real: `grammar` (one learner sentence with a grammar point: said, better, why ≤ 10
  words) and `vocabulary` (one plain or missing word: said quote, the word they used, a stronger word or phrase, an
  example sentence ≤ 15 words; a word said in Bangla counts: the English word).
- Cards: Sound fix (≤1), Grammar (≤1), Vocabulary (≤1), Say it better (2, or 1 when Grammar or Vocabulary is shown),
  habit if room, Standup tip as today. At most 5 cards (was 3).
- Grammar and Say it better never quote the same sentence. Copy never says mistake/wrong/error/incorrect.
- Grammar has Try it (like Say it better); Vocabulary has the model voice saying the example and Try it on it.

## 4. Bangla

**Why:** "It doesn't understand Bangla at all. It should, and it should write the transcript in Bangla."

**Spike (2026-11-18, Azure, synthetic voices):** on English speech the English recogniser is 0.82–0.90 confident; on
Bangla and mixed Bangla-English it is 0.00–0.24 confident and writes romanised Bangla ("Amar boss shesh mourte…"). The
Bangla recogniser (bn-IN) writes all Bangla and mixed clips correctly in Bangla script (0.67–0.87), but is also 0.63–0.77
"confident" on English. So the English confidence is the signal, and the Bangla recogniser only runs when it's low.

- Live recognition stays English (pronunciation scoring unchanged). Each finished segment now carries Azure's
  confidence. When a learner turn ends with English confidence under 0.5 (`bolo.talk.bangla.max-english-confidence`),
  or with speech but no words (today's "unclear"), the turn's audio is recognised once in Bangla (Azure REST, bn-IN,
  ≤ 60 s, 3 s timeout). If that returns Bangla script with confidence ≥ 0.5, the turn is a Bangla turn; otherwise the
  turn goes on as today.
- A Bangla turn: the caption shows the Bangla text (lang="bn"); it is saved as said. Bolo replies in English: a few
  words showing it understood, then the English way to say it in quotes, and "Try saying it in English." In the
  English teacher hat, the English sentence is taught (card "In English", judged like any teach; it doesn't count toward
  the teach cap). Bolo never replies in Bangla.
- Bangla turns are never scored for pronunciation and never chosen for Sound, Grammar or Say it better cards; the
  Vocabulary card may use one ("you said পরিকল্পনা: in English, plan").
- Cost: one short REST call per Bangla turn (about $0.003). Speed: a Bangla turn's reply is up to ~1 s later.

## Non-goals

Bangla replies; automatic language detection on every turn; a per-learner teach level setting; new plan rules.

## Left to the builder

Prompt wording, the curious bank's items, config names, test names.
