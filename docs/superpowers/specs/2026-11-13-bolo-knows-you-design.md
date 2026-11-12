# Bolo knows you: design

Founder request, 2026-11-08, confirmed 2026-11-12 and 2026-11-13. This covers project "3 + 4" in the agreed order (1 live chat → 3 + 4 → 2 voice settings):
- **3:** Bolo starts each talk from who the learner is, remembers earlier talks, and follows up.
- **4:** conversations are interesting, not "what did you have for breakfast".

The PRD already asks for part of this: TLK-11 (P0, never built) and US-2.11 "Continue yesterday's story".

## Why
- **Today Bolo forgets everything between talks.** It knows only the three setup answers: goal, hardest moment and minutes. Every talk opens with a general hello and question, so it feels like a stranger every day.
- **A friend who remembers makes people come back and talk more.** The founder's examples of a good first line:
  - "Last time you said you'd send your CV. Did you do it?" (a plan the learner shared)
  - "You were worried about your manager's feedback. Is that still bothering you?" (something on their mind)
  - "How's your interview preparation going?" (their English goal)
  - In the founder's words: "personal touch can be helpful when starting."
- **Boring questions kill speaking practice.** "What did you do today?" gets a one-line answer. Stories, opinions and choices get people talking.

## Goals

### A. Bolo keeps short notes about the learner
After every talk, Bolo updates one short note per learner. The note holds:
- **About you:** simple facts the learner shared about their life. Examples: job, field, company type, city, family role, what they're preparing for. At most 12 lines.
- **To follow up:** open threads, at most 8. Each is one of:
  - a **plan** ("Sending the CV to the bank job", with a due date if they said one);
  - a **worry** ("Nervous about the manager's feedback");
  - **prep progress** ("Practising answers for 'tell me about yourself'").
  - Each thread records the date it was mentioned. It's marked done when the learner says it happened, and dropped 30 days after it was last mentioned.
- **Interests:** things they enjoy talking about ("cricket", "cooking biryani", "travel to Cox's Bazar"). At most 8.
- **Recent topics:** the last 6 talk topics, so Bolo doesn't repeat itself. These are not shown to the learner.

There is no separate goal-progress line: how their preparation is going is a "prep progress" thread in **To follow up**, so the learner can see and remove it like any other line.

Only what the learner said about themselves goes in. Bolo's own lines and guesses do not.

### B. Each talk opens with a personal touch
- **When the note has something to follow up on,** Bolo's first line is a short greeting plus **one** personal follow-up. It picks the one most likely to matter today:
  - a plan that should have happened by now;
  - a worry;
  - how their prep is going.
  - It is one touch, never a checklist of all three.
- **The first talk ever, or an empty note:** Bolo opens from the setup answers with a real question about the learner's situation. For job interviews that is "What kind of job are you going for?", not "How was your day?".
- **The learner steers.** If they'd rather talk about something else ("Let's talk about something else", or they simply start on another subject), Bolo follows fully and doesn't pull back. Founder: "if user led the conversation in any other direction it's user choice."

### C. Conversations are interesting
Bolo's questions mix these kinds instead of daily-routine chatter:
- **stories:** "Tell me about a time…";
- **opinions:** "Do you think…?";
- **small what-ifs:** "If you could…";
- **choices:** "Which would you pick…?";
- **light, real rehearsal tied to the goal:** "Say an interviewer asks why you left your last job. What would you say?" This is never framed as a test.

Rules for keeping it interesting:
- **No routine chatter.** Bolo never opens with or falls back to "what did you eat / do today" or the weather, unless the learner brings it up.
- **It builds on what it knows.** Bolo uses the learner's interests from the note and avoids the recent topics.
- **The talk feels two-sided.** Bolo reacts to what the learner said with a short opinion or feeling of its own before asking the next question, so it feels like a conversation and not an interview. Bolo never invents a life of its own ("I went to Cox's Bazar last week").
- **Fresh ideas come from a topic bank.** It holds about 20 conversation seeds per goal (work meetings, job interviews, study abroad, daily confidence) plus 20 general ones, set in everyday Bangladeshi adult life. Each talk gets 3 random seeds that aren't recent topics, as ideas for when the talk needs a new direction.

### D. The learner sees and controls the note
Me → a new row, **"What Bolo remembers"**, opens a screen that:
- lists the note in three plain sections: **About you**, **To follow up** (with the date each was mentioned), **Your interests**;
- lets the learner **remove any line** (×). The line is gone from the note at once, so the next talk can't use it. If the learner raises the same thing in a later talk, it may be noted again: removing is not a permanent block;
- has a switch, **"Let Bolo remember our talks"**, on by default. Turning it off deletes the note and stops new notes. Talks then open from the setup answers only;
- has **"Forget everything Bolo knows"**, with a confirm, which empties the note but keeps remembering from the next talk;
- shows a privacy line: "Bolo keeps short notes from your talks so it can follow up, like a friend would. Only you can see them. Delete any line, or turn this off.";
- shows an empty state: "After your next talk, the things Bolo will ask you about show up here."

Privacy and terms copy also mention the notes. **Delete everything** (Me → Privacy) also deletes the note.

## Non-goals
- **No editing a line's text.** Delete only. Rewording invites the learner to "correct" Bolo, which is not the job.
- **No admin view of anyone's note.** The founder's admin portal doesn't show it.
- **No voice, accent or mood settings.** That is project 2, next.
- **No change to the debrief, the coach or the scoring.**
- **No scenario picker** (Standup Rehearsal stays a separate PRD item).

## Anti-goals (failures even if it technically works)
- **Creepy:** Bolo brings up something sensitive the learner said once, or mentions a crisis talk. Or it remembers details about other people beyond a first name and role ("my manager Rahim").
- **Wrong:** Bolo "remembers" something the learner never said, or asks about a plan it already heard was done.
- **Pushy:** Bolo keeps dragging the talk back to the follow-up or the goal after the learner moved on.
- **Repetitive:** the same opener or topic two talks in a row.
- **Slower:** the first line takes noticeably longer to arrive than today.

## Constraints
- **The note never stores sensitive details:**
  - health, including mental health;
  - religion, politics, sexuality;
  - exact money amounts;
  - phone numbers, addresses and ID numbers;
  - anything from a talk that ended in crisis care.
  - The updater is told this. A talk that ended for crisis care is never used to update the note.
- **A short talk doesn't update the note.** A talk with fewer than 2 learner turns leaves it unchanged.
- **Sizes are capped in code, whatever the model returns:** at most 12 / 8 / 8 / 6 lines, each line at most 120 characters.
- **The talk's model and timing don't change.** The Buddy stays on claude-haiku-5-5, and the note adds a few hundred tokens to its prompt. The note is updated by one claude-sonnet-5-5 call after the talk, off the talk's path, using structured output.
- **Module boundaries:** the note lives in a new `memory` module. It listens to `TalkEnded`, `LearnerDeleting` and `LearnerMerged`, and reads talks through talk's port. Talk asks memory for the note through a port in. ArchUnit rules stay green.
- **Data:** one row per learner. The switch state is stored with the note.
- **The founder can test it by clicking** on bolo.eliooo.org:
  - in a talk, mention a plan;
  - about 15 s after the talk ends, Me → What Bolo remembers shows it;
  - the next talk opens by asking about it;
  - delete it, and the next talk doesn't.

## How the opener picks its follow-up (decided in code, not left to the model)
- **Each thread records when Bolo last opened a talk with it.** A "Not yet" answer keeps it open.
- **The server picks one thread** and puts it in Bolo's prompt as the opener. The model only phrases it.
  1. It prefers a plan whose due date has passed.
  2. Then a worry.
  3. Then prep progress.
  4. Within a kind, the most recently mentioned comes first.
- **A thread that opened a talk is skipped for the next 2 talks and 3 days**, whichever is later.
- **When every thread is resting,** Bolo opens from an interest or the goal instead.
- **The opener is recorded when the talk starts.** A talk that ends before the learner speaks still counts.

## Decided by the founder
- The opener uses a personal touch from what Bolo remembers: a plan, a worry, or prep progress (2026-11-13).
- The learner steers; Bolo follows (2026-11-08).
- Interesting, not routine, questions (2026-11-08).
- "Everything is okay, now start" on the playback above (2026-11-13). The founder must see the design-system screens and say yes before development.

## My calls (founder to check)
- **The note is a set of short lines, not a free-text "context file".** Lines can be shown, deleted and capped.
- **The updater is a separate Sonnet call per talk,** not part of the coach's call. This keeps memory and coaching separate.
- **Remembering is on by default,** with a switch to turn it off.
- **Delete only, no editing.**
- **Threads drop 30 days after they were last mentioned.**
- **Bolo may offer small opinions and feelings,** but never invents a life of its own.
- **There is a topic bank** (about 100 seeds), so variety doesn't depend on the model alone.
- **No notes are built from past talks.** People had those talks before any copy mentioned notes. Every note starts empty and fills from the first talk after release. The founder tests with a fresh talk.
- **Removing a line is a plain delete,** not a permanent block. It may come back only if the learner raises it again.
- **The opener rotates** by the rule above, so the same follow-up doesn't open talk after talk.
- **Prep progress is a thread,** not a separate hidden line.
- **Threads marked done are removed when the note is updated.** Bolo may react to "I did it!" during that talk, but it doesn't keep finished plans.
- **Merging two notes:** when a guest's talks join an account (rare now that sign-in comes before the first talk), the account's note is kept and the guest's lines are added up to the caps.

## Left to the builder
- Table and column names, and the exact JSON shape of the note.
- How the note is written into the Buddy's prompt.
- The topic-bank seeds' wording, within the rules above.
- How the updater handles two talks ending close together. The later talk's update must not lose the earlier one's.
- What happens when the updater fails: the old note stays, and the failure is logged.
