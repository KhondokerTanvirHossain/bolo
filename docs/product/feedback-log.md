# Product feedback log

Feedback from testing the live app, kept for discussion before it becomes PRD changes.

## 2026-11-08 — first voice talk (founder test, laptop)

**Verdict:** "I talked and it talked back … overall somewhat satisfied."

### 1. Bolo's accent
- **Observed:** Bolo speaks with an Indian English accent (Azure `en-IN-NeerjaNeural`, chosen in P1a-2 as a placeholder).
- **Founder's view:** the default should be American; learners can change it if they want. "We need to think hard, don't just do it because I told you. I need to understand what will make people use it more and more."
- **Questions for the discussion:**
  - What do learners say they want to sound like, versus what they understand most easily?
  - Does the choice depend on the learner's goal (job interviews at local or multinational firms, study abroad, everyday talk)?
  - Should the first-talk voice be one default, or picked during onboarding?
  - Can the pilot measure it (A/B, or asking learners after the first talk)?
- **Status:** open, to discuss on the morning of 2026-11-09. Nothing changed yet.

### 2. Bolo's personality and mood
- **Observed:** Bolo is always positive and warm. Good, but "not always hooked"; it can feel flat.
- **Founder's view:** roles should be customisable. The character could sometimes be cheeky, naughty or comic, depending on the mood. Ideal: sense the learner's mood and get along with it. Acknowledged as probably too much for the MVP.
- **Questions for the discussion:**
  - Which persona dimensions (humour, energy, cheekiness, formality) matter most for coming back daily?
  - Should the learner choose the persona, or should Bolo adapt per session?
  - What is the cheapest MVP version: a few preset personas versus mood sensing from the voice and words?
  - How does this fit the design system's voice rules ("never laughs at you", no guilt) and TLK-3 (never correct during the talk)?
- **Status:** open, to discuss on the morning of 2026-11-09.

## 2026-11-08 (evening) — talking in another language (founder test)

### 3. Bolo should show it understood Bangla, then ask for English
- **Observed:**
  - The founder spoke in another language. Bolo replied: "Sorry, I didn't quite catch that. Could you try saying it again in English? For example, you could say, 'I am the owner of Convolve.' What does your work look like each day?"
  - It's long (an apology, an example, then a new question), and it sounds as if Bolo understood nothing.
- **Founder's view:** answer in English and ask the learner to say it in English, but give the feeling that Bolo understood that language too.
- **Why it happens:** recognition listens for English only (en-US), so Bangla returns no words and the turn is "unclear". Bolo has nothing to understand.
- **Option costed (2026-11-08):**
  - **How it would work:** on an unclear turn, recognise the saved turn audio once more in Bangla (Azure short-audio REST), then give Bolo a short cue to reflect what it understood and invite English.
  - **Money:** about $0.003 per Bangla turn.
  - **Code:** about half a day.
  - **Speed:** that one reply is +0.5–1 s.
  - **Blocker:** on Azure F0 the extra call would usually be refused during a live talk (one stream at a time), so it needs S0 (pay-as-you-go, no monthly fee).
  - **Rejected:** automatic language detection on every turn (it risks hearing accented English as Bangla, and may break pronunciation scoring).
  - **No-cost part on its own:** shorten the current unclear-turn reply.
- **Status:** logged only. The founder will handle it later; not urgent. Nothing changed.
