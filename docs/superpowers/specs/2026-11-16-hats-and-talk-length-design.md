# Bolo's hats and 10-minute talks — design

Founder idea 2026-11-16 (docs/product/feedback-log.md). Founder answers the same night, before sleeping:
- Teacher hat teaches **only when worth it**; Teacher **and IELTS practice (no band score)** tonight;
- 10-minute talks for **Premium and Ultimate**, a 10-minute talk **counts as one talk**;
- **design, build and deploy** tonight without waiting for the screens to be approved (founder checks in the morning).

## Why

The founder sees teaching *during* the talk as Bolo's core value ("my English teacher — most important"): the learner
says something, Bolo shows a better way, the learner says it again, Bolo judges. Feedback after the talk stays. IELTS
practice brings a common real goal into the same picker. Longer talks are a reason to pay.

## Goals

1. **Hats under Change.** The scenario picker (built for Standup, DS v44) becomes "What do you want to practise?" with
   four choices: Free talk (default, Bolo the buddy), English teacher, Standup rehearsal (Friendly / Realistic), IELTS
   Speaking practice. One tap applies; Home's line names the choice ("English teacher · 5 min").
2. **English teacher.** Bolo is a warm teacher. It invites the learner to talk about something real (a situation they
   need to handle, their day, their work). After a learner turn whose sentence can clearly be said better, Bolo teaches:
   one short better version (their meaning, their words where possible), shown on screen, and "Try saying it." The next
   learner turn is the retry; Bolo judges it — **Nailed it / Closer / Good try** — in a few warm words, then carries on.
   Otherwise Bolo just replies and asks a question.
   - "When worth it": never on the first learner turn, never two teach turns in a row (at least one ordinary turn
     between), at most 4 a talk, only when the better version passes the same checks as the Coach's upgrades.
   - With I stammer on, a sentence is never taught for repeats, blocks or pauses (the Coach's stammer rule), and the
     retry is never judged on them.
   - Judging compares the retry's words with the better version (order kept, repeats ignored): ≥ 80 % → Nailed it,
     ≥ 50 % → Closer, else Good try. Never "wrong", never a score.
3. **IELTS Speaking practice.** Bolo is a calm examiner running a practice test: Part 1 (familiar questions), Part 2
   (a cue-card topic read aloud: "Describe …, you should say …"; the learner speaks for up to 2 minutes), Part 3
   (discussion). In a 5-minute talk: a short Part 1 and Part 2; in 10 minutes: all three parts. The examiner gives no
   feedback and **no band score** during or after; the normal debrief follows. Slow answering timers (at least the
   Relaxed pace) so Part 2's long turn and thinking aren't cut off.
4. **10-minute talks.** Premium and Ultimate learners choose 5 or 10 minutes for their talks in the same picker
   ("Talk length"); a 10-minute talk uses one talk. Free and Basic talks are 5 minutes; the picker shows
   "10-minute talks come with Premium" with See plans.

## Non-goals

No IELTS band score or band-like number; no client role; no per-hat difficulty other than Standup's; no live grammar
correction outside the Teacher hat; no new plan prices.

## Anti-goals

- Free talk (the default) behaves exactly as today. The Teacher's live correcting happens only when the learner picked
  English teacher.
- Teaching never shames: no "mistake", "wrong", "error", "incorrect"; one better sentence at a time.
- A teach turn never blocks the talk: if the check is slow (> 2.5 s) or fails, Bolo just replies.
- Never claim an IELTS band or "you would score".

## How it works (my calls, from the founder's answers)

- Scenarios become `free | standup | teacher | ielts` (V33 widens the checks). Same settings, same copy onto the talk.
- **Teacher:** after a learner turn, a small structured LLM call (`claude-haiku-5-5`, 2.5 s timeout) returns
  `{teach, better, why}`; when `teach` passes the gates, Bolo speaks a fixed template ("A smoother way to say that:
  "…". Try saying it.") and the web shows a "Say it better" card with the sentence. The next turn is judged in Java;
  Bolo speaks a short template judgement, then the normal model reply continues the talk. Events `teacher_taught`,
  `teacher_judged {result}`.
- **IELTS:** a prompt branch; pace floored at Relaxed.
- **Length:** `learner_settings.talk_minutes` (5 | 10, null = the onboarding answer). Start: Premium, Ultimate and
  unlimited learners get their choice; free and Basic get 5. The web sends the shown length with the start (like the
  scenario). *Changes Basic:* a Basic learner who chose 10 in onboarding now gets 5.
- **Debrief:** unchanged for Teacher and IELTS (no standup tip). History names the hat.

## Design system (v45)

ScreenScenarioPicker / DesktopScenarioPicker (four hats + Talk length), ScreenTeacherTalk (the Say it better card and
its judgement pill in the talk), ScreenIeltsTalk (examiner label, Part 2 cue card in the chat).

## Left to the builder

Exact prompt wording, template lines, config names, the LCS details, test names, where the picker's length row sits.
