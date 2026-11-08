# Bolo PRD audit: build vs Bolo-PRD-MVP.md

Repo `bolo`, branch `p0-foundations`, HEAD `b18c57d`, audited 2026-11-09. Read-only: no files changed and nothing deployed.

**Method.** I went through every requirement ID in the PRD and checked the code for each (`api/src/main/java/app/bolo/**`, `web/src/**`, Flyway V1–V13). I read the RUNBOOK too, but checked its claims against the code instead of taking them on trust. Rulings come from `docs/superpowers/plans/*`. Phase placement comes from `Bolo-Architecture-MVP.md` §10. Under the PRD, every **P0** requirement is due *for the closed beta* (Phase 1 = 1a + 1b + 1c), so a PRD-P0 item that isn't built counts as a gap even if the architecture puts it in 1b or 1c. PRD **P1/P2** items are Phase 2 or later and are marked *N/A-later*.

Statuses: **Done**, **Partial**, **Missing**, **Deferred** (a recorded ruling or founder decision defers it), **N/A-later** (PRD P1/P2 or a later phase). "Unmeasured" means the code is in place but there is no evidence yet that it meets the target.

---

## 1. Summary

| Group | Total | Done | Partial | Missing | Deferred | N/A-later |
| --- | --- | --- | --- | --- | --- | --- |
| ONB (onboarding) | 7 | 3 | 3 | 0 | 0 | 1 |
| TLK (conversation) | 16 | 10 | 3 | 1 | 1 | 1 |
| ASM (assessment) | 7 | 2 | 5 | 0 | 0 | 0 |
| DBF (debrief) | 8 | 2 | 3 | 1 | 0 | 2 |
| DRL (drills) | 7 | 0 | 3 | 4 | 0 | 0 |
| PRG (progress, habit) | 8 | 3 | 1 | 3 | 0 | 1 |
| SCN (scenarios) | 3 | 0 | 1 | 1 | 0 | 1 |
| ACC (account, privacy, plans) | 8 | 5 | 0 | 0 | 0 | 3 |
| SET (settings) | 4 | 1 | 1 | 0 | 0 | 2 |
| HOM (home) | 1 | 0 | 1 | 0 | 0 | 0 |
| PRS (presence) | 8 | 0 | 3 | 5 | 0 | 0 |
| **Functional subtotal** | **77** | **26** | **24** | **15** | **1** | **11** |
| AX (accessibility) | 10 | 3 | 4 | 2 | 0 | 1 |
| NFR | 22 | 3 | 12 | 5 | 1 | 1 |
| R1–R12 UX rules + NAV | 13 | 7 | 6 | 0 | 0 | 0 |
| §7 edge cases, §10 analytics (as groups) | 2 | 0 | 2 | 0 | 0 | 0 |
| **All** | **124** | **39** | **48** | **22** | **2** | **13** |

**Overall.** Phases 1a and 1c are mostly built and work well: onboarding, live talk, safety, Coach v1, accounts, invites, Me/Privacy, deletion, Progress and History. **Phase 1b ("the real loop") is mostly not built.** That covers drills, rating, the Standup scenario, barge-in, reconnect, story memory (deferred), presence and the accessibility settings. Two Phase 1c items are missing as well: reminders and the Day 7 comparison. The quality of what exists is high, with good tests, guarded copy and careful privacy. Before strangers arrive, the beta's biggest risks are operational and safety items, not features.

---

## 2. Gap list for the MVP closed beta

### P0: blocks a credible closed beta (safety, privacy, legal, can't run)

| # | Req | What's missing | Size | Founder decision? |
| --- | --- | --- | --- | --- |
| P0-1 | NFR-8, NFR-22, ops | **Capacity.** Azure Speech **F0** allows **one live talk at a time** (`bolo.talk.max-live-sessions: 1`, `application.yml`; RUNBOOK "Talking with Bolo"). A second learner sees "Bolo is in another talk right now". F0's monthly free hours will also run out quickly with 50–100 learners, and practice/Try it uses the same one-recognition limit (429 → "Bolo is busy"). Move to **S0**, raise the cap and load-test the 512 MB `bolo-api` with about 5–10 concurrent talks. | S (config) + S (load test) | **Yes**: S0 spend |
| P0-2 | NFR-21, TLK-14 | **Crisis wording and helplines aren't partner-approved.** The founder approved them for the pilot only (`CrisisScript.java`; RUNBOOK "Pilot safety"). The PRD requires a qualified local partner *before beta*. | S (external) | **Yes**: choose and contact the partner |
| P0-3 | TLK-14, US-P4, Risks table | **No red-team run of the Buddy prompts** (Architecture 1b "TLK-14 sign-off"; I found no artefacts). `BuddyPrompt.system` has no friend-not-romantic rule (US-P4: "never flirts … redirects kindly"). Harmful topics depend on one prompt line, with no output filter. | S–M | No (needs sign-off) |
| P0-4 | NFR-8, NFR-7 | **No database backups.** RUNBOOK "Database" says "not yet automated; add a nightly pg_dump to S3 before the closed beta". There's also **no alerting**: no Slack appender or logback config exists, although Architecture §9 calls for one. Learners' history lives in a single Postgres container on a shared box. | S | No |
| P0-5 | NFR-15 | **Vendor no-training and retention terms haven't been checked or recorded** for Azure Speech (F0/S0), Anthropic and Firebase. Architecture DR-10 makes them a must-pass. The privacy page lists the processors (`web/src/app/privacy/page.tsx`) but makes no no-training promise. | S | **Yes**: confirm the terms |
| P0-6 | NFR-10, ASM-2/5, Gate 1 | **Sound-fix accuracy hasn't been tested on Bangla-accented speech.** The Gate 1 harness exists, but its verdict is "NOT DECIDED" (synthetic set only, RUNBOOK "Accuracy harness"). The Coach's free-talk path (threshold 50, unscripted) has never been checked (RUNBOOK "Limit"). The PRD puts this gate *before* the build. Strangers will see a sound fix in many debriefs. Options: (a) collect 5–8 local speakers plus teacher labels before the beta; (b) turn sound fixes off in the beta debrief, keeping practice. | M | **Yes**: (a) or (b) |
| P0-7 | ACC-4, NFR-14, privacy page | **Guest data is orphaned and never deleted.** Once a consented guest's cookie expires (90 days) or they clear it, their transcripts, scores and talk rows stay on the server forever. `SessionSweepService` deletes only sessions, plus guests *without* consent (`JdbcLearnerAdapter.deleteGuestsWithoutConsentCreatedBefore`). The learner can no longer delete that data, which breaks the privacy page's "stays until you delete it" and US-8.2's "my guest data is gone". Add a purge of idle guests. | S | **Yes**: retention period (e.g. 90 days idle) |

### P1: core loop quality (should land during or just before the beta)

| # | Req | What's missing | Size | Founder decision? |
| --- | --- | --- | --- | --- |
| P1-1 | PRG-2, US-6.1, HOM-1 | **Day 7/14/21 re-record and side-by-side comparison** don't exist. The Day 1 screen already tells every learner "Saved. We'll play this back to you on Day 7." (`Day1Screen.tsx:175`). **Time-boxed:** the first cohort reaches Day 7 one week in. | M | No |
| P1-2 | DRL-1…7, R5, US-5.x | **Drills are only "read sentences for a sound"** from a fixed 3-sentence deck (`PracticeDeck.java`, `PracticeScreen.tsx`). Missing: drills generated from *this session's* fixes, minimal pairs (listen and pick, then 5 spoken words), shadowing (turtle 0.75×), the 3-round "say it better", skip and requeue, 3-skip retire, a limit of 3 attempts in practice, and the end summary with "Done". "Practise (3 min)" opens only the first sound's deck. | L | No |
| P1-3 | PRG-5, PRG-6, PRG-7, US-6.5/6.6/6.8 | **Reminders are missing entirely**: no Web Push, slots or back-off, and no reminder step in the first loop (`p1c1-accounts` plan: "the reminder step waits…"). Day-7 retention is a Gate 2 metric. This overlaps feedback-log item 5 (push and gamification). | M–L | **Yes**: confirm reminders aren't part of item 5's "not yet" |
| P1-4 | SCN-1, SCN-2, US-7.1/7.3/7.4 | **Standup Rehearsal** doesn't exist: no scenarios, no "Change" link, no Friendly/Realistic, no interruptions, no scenario fix. The talk is hard-coded to "Free talk" (`TalkScreen.tsx`). The primary persona's feared moment is the standup. | M | **Yes**: in for beta, or after |
| P1-5 | DBF-8, US-4.11, §10 `session_rated` | **No session rating** after sessions 2, 3, 4 and then every 3rd. Satisfaction (≥ 70%) can't be measured in the beta. | S | No |
| P1-6 | TLK-10, US-2.10, US-2.12, NFR-9, R12, HOM-1 | **A talk doesn't survive a closed tab or a socket drop.** `onClosed()` ends the talk as `DISCONNECTED` (`TalkConversation.java:278`). There's no seq/ack resend, no 20 s grace and no "Continue your chat" on Home (`HomePlan.java` ruling). Pause and resume work only while the tab stays open. Turns already spoken still get a debrief. | M | No |
| P1-7 | DBF-4, US-4.6 | **Disputes:** the card is removed and the kind logged (`fix_dispute`). Missing: saving the clip and transcript for review (with consent), suppressing that issue for that word, and the "noise or mic?" prompt after 2+ disputes. Gate 2 depends on dispute quality. | S–M | No |
| P1-8 | DBF-5, US-4.7 | **Skill levels show no arrows against the last session.** The web says "Arrows appear after your next talk", but no later session shows any. Pronunciation is hidden when there's no phoneme data, so only 3 skills appear. Tapping a skill gives no one-line detail. | S | No |
| P1-9 | ONB-5, US-1.5, US-3.4, US-3.6 | **The mic-check verdict is thrown away.** It stays in browser state only (`steps.ts`) and never reaches the server. There's no "Noisy audio, so some feedback was skipped" note and no overlapping-voice exclusion. | S | No |
| P1-10 | TLK-15, US-2.3 | **No barge-in.** "I'm using headphones" is asked (`MicScreen.tsx`) but never used. While Bolo speaks, the server sends silence to the recogniser (`TalkConversation.onAudio`). | M | No |
| P1-11 | AX-7, AX-8, US-9.2, US-9.4 | **No "More time to answer" (16/30/90 s or off) and no "I stammer".** Both are PRD P0, and without them people who stammer get unfair fluency feedback. | S–M | No |
| P1-12 | US-3.7, ASM-7 | **No "Notify me when ready" and no retry later.** After 3 attempts the debrief is `failed` for good. The copy says "Your talk is saved. Try another talk", but the PRD promises "we'll try again" and a notification. | S–M | No |
| P1-13 | §10, Gate 2 | **Analytics:** 7 of the 30 events (`EventRules.java`). The Gate 2 inputs can be derived (`debrief.viewed_at`, `fix_dispute`, `talk_session`), but there's no query or view for D7 retention, dispute rate or loop completion. `scripts/metrics.sh` covers only the funnel. | S | No |
| P1-14 | NFR-14, ACC-3 | **Day 1 clip retention.** It's kept "until you delete everything" (`PrivacyScreen.tsx`, privacy page) rather than the 90-day clip default, and it **stays on the server when the learner later turns Keep off**. The clip can't be deleted on its own. | S | **Yes**: keep the exception, or apply 90 days and delete on opt-out |
| P1-15 | ACC-4 | **Delete everything leaves the Firebase Auth user** (email and uid) in project `bolo-d44c2`. There's no service account and no client-side `user.delete()`. No confirmation email either (ruled, plan p1c2). | S | Maybe (service account) |
| P1-16 | PRS-1, PRS-3, PRS-4, PRS-6, PRS-7 | **Presence is a still hilltop SVG with a silhouette** (`Scene.tsx`). Missing: the companion's 5 states (facing for the debrief), ambient sound with ducking, the choice of companion, voice-only mode. This is the product's differentiator, but it needs art. | L | **Yes**: art and Phase 0 companion choice |
| P1-17 | ASM-2, ASM-4 | **Free talk can't detect the s-cluster extra vowel** (only scripted practice catches it, through the insertion miscue). Ranking is by frequency alone, with no clarity weight, and improved repeats aren't dropped. The "max 1 sound fix" is a deliberate ASM-5 ruling (plan p1a3). | M | No |
| P1-18 | ONB-1, US-1.1 | **The welcome is caption-only** and moves on by itself after 6 s (`WelcomeScreen.tsx`: "until the TTS vendor is chosen"). TTS exists now, and there's no "turn up your volume" banner. | S | No |
| — | TLK-11 | Story memory: **deferred by decision** (feedback-log item 4, "not to be built until discussed"). Not counted as a gap, but it's a PRD P0 and the founder's main complaint ("repetitive, doesn't know me"). | M | Already pending |
| — | TLK-7 (meaning restated) | "I think you said … [meaning]" is **deferred** (feedback-log item 3). The kind nudge back to English works. | S–M | Already pending |

### P2: polish, or fine to leave for later

| # | Req | Item | Size |
| --- | --- | --- | --- |
| P2-1 | US-2.9 | End sheet says "End this talk?" with "End". PRD: "End and get feedback?". The p1a2 ruling ("debrief doesn't exist yet") is out of date now. | S |
| P2-2 | US-1.1a | Consent button "No, just feedback". PRD: "No, just give feedback". | S |
| P2-3 | US-1.4 | Me → My goals is read-only. PRD: all 3 answers editable. | S |
| P2-4 | ONB-3 | Setup questions are tap-only. PRD: asked by voice, with chips as the fallback (Architecture allowed this in 1a). | M |
| P2-5 | DBF-2, R8, US-4.2 | No autoplay of the learner's clip and then the model. The filler habit card has no clip or timeline of marks. No "11 → 4" change. Navigation is a sidebar plus ↑/↓ instead of dots and swipe. The sound card's title comes before its play button. | S–M |
| P2-6 | US-4.8 | "Finish your feedback" reopens on the strength card, not where the learner stopped. There's no "(2 fixes left)". | S |
| P2-7 | — | The debrief's skills card shows engineering latency ("Bolo answered in about X s") to learners (`CoachFlow.tsx:286`). Not in the PRD and goes against the calm-screen intent. Remove or hide it behind a flag. | S |
| P2-8 | US-3.3 | The too-short state has 2 buttons. PRD: one button. | S |
| P2-9 | SET-2, AX-4, AX-2 | Type is set in `px` (331 px vs 1 rem in `bolo.css`), so it ignores the browser's default font size. The Talk button is a standard button height, not ≥ 96 px. | S–M |
| P2-10 | AX-1, AX-3 | No recorded contrast audit or screen-reader walkthrough (`dk-item` rows are `role=button` divs). There's no "Your turn" announcement policy beyond the `role=status` label. | S |
| P2-11 | US-1.9 | Offline onboarding: boot needs the network ("Bolo can't reach the internet"). | M |
| P2-12 | NFR-20 | Strings are hard-coded in components. There are no message bundles. | M |
| P2-13 | NFR-12, §10 | No early-cutoff tracking. Most of the tuning events (`silence_prompt_shown`, `barge_in`, `bangla_detected`…) are missing. | S |
| P2-14 | §7 | Missing edge states: headphones disconnect, the 2-turn echo notice, service-outage and version screens, low storage. | M |
| P2-15 | PRS-2, PRS-5, PRS-8 | Evening drive scene, stereo placement, offline caching of the scene. | M |
| P2-16 | NFR-22 | The usage ledger stores units but no `usd_micros` (deferred in p1a2), so cost per loop needs a manual price join. | S |
| P2-17 | NFR-6 | PCM on the wire is about 19 MB per 10 minutes against the 5 MB target. **Deferred by ruling** (p1a2), and must land before the mobile web release. | M |
| P2-18 | US-6.3 | My patterns show after the first feedback, while the PRD says after 3+ sessions. Small, and arguably better. | — |

---

## 3. Contradictions (the build differs from the PRD, beyond what's missing)

| # | PRD says | Build does | Evidence | Judgement |
| --- | --- | --- | --- | --- |
| C1 | NFR-14: default clip retention 90 days | Day 1 clip kept until "Delete everything", and kept even after the learner switches Keep off | `PrivacyScreen.tsx`, privacy page, `Day1Service.java` | Disclosed, but no ruling recorded. **Founder decision** (P1-14). |
| C2 | US-8.2: clearing guest data means "my guest data is gone"; the privacy page promises deletion on request | Server keeps orphaned guest data forever | `SessionSweepService.java`, `JdbcLearnerAdapter.java:94-104` | Privacy gap (P0-7). |
| C3 | US-3.5: at most **2** sound fixes | At most **1**, and only for a pattern seen ≥ 2 times | `DebriefPlan.MAX_SOUND_FIXES = 1`; plan p1a3 "ASM-5 confidence first" | Deliberate until Gate 1. Fine, but record it as a PRD deviation. |
| C4 | US-3.7: failed scoring → "we'll try again", then notify | 3 attempts back to back, then permanent `failed`; copy says "Try another talk in a moment" | `CoachService.compose`, `CoachFlow.tsx:61` | Behaviour and copy both differ (P1-12). |
| C5 | US-2.9: "End and get feedback?" | "End this talk?" | `TalkScreen.tsx:236` | Ruling is out of date (P2-1). |
| C6 | US-1.1: Buddy's *voice* welcome within 3 s | Caption only; moves on after 6 s | `WelcomeScreen.tsx` | P1-18. |
| C7 | US-1.5: noisy "Continue anyway" → debrief hides low-confidence items and says so | Verdict discarded | `steps.ts`, `Onboarding.tsx:184` | P1-9. |
| C8 | US-2.6: "I think you said something like [meaning]. Can you try that in English?" | "Sorry, I didn't quite catch that…" (en-US recognition only) | `BuddyPrompt.UNCLEAR_CUE`; feedback-log item 3 | Deferred by founder. |
| C9 | TLK-15: barge-in with headphones | Headphones question asked; answer unused | `MicScreen.tsx`, `TalkConversation.onAudio` | Asked with no effect (P1-10). |
| C10 | US-6.9 priority table ("Talk", "Continue your chat", "Re-record your intro", "3-minute practice", scenario "Change") | "Start talking"; card is "Finish your feedback" or "Practise the v sound"; extra quiet links "Practise sounds" and "Play my Day 1 clip" | `HomePlan.java`, `HomeScreen.tsx` | Ruled for 1a. Revisit as each dependency lands. |
| C11 | NFR-6: ≤ 5 MB per 10 min | PCM, about 19 MB | p1a2 ruling | Deferred by ruling; fine for a desktop beta. |
| C12 | ACC-4 / US-8.6: confirmation SMS or email; deletion "within 7 days" | Deletion is immediate (better than the PRD); no confirmation email | plan p1c2 ruling | Ruled. Also the Firebase user stays (P1-15). |
| C13 | Open question: model voice accent | Buddy is `en-IN-NeerjaNeural`; Coach is `en-US-AndrewNeural` | `application.yml` | Founder wants an American default (feedback-log item 1); still open. |
| C14 | The Day 1 screen promises playback "on Day 7" | No Day 7 feature exists | `Day1Screen.tsx:137,175` | UI promise without the feature (P1-1). |
| C15 | US-3.3: one button, "Talk a bit more" | Two buttons (plus Done) | `CoachFlow.tsx:55-58` | Trivial (P2-8). |
| C16 | §1 and TLK-16 calm screens; no numbers | Debrief shows Bolo's reply latency in seconds | `CoachFlow.tsx:286` | Leftover debug output (P2-7). |
| C17 | US-6.3: patterns after 3+ sessions | After the first feedback | `PatternList.tsx:33` | Minor; arguably better. |

---

## 4. Ops readiness notes (not PRD IDs, but they decide the beta)

- **Concurrency and quota**: see P0-1. The shared EC2 box caps `bolo-api` at 512 MB, and the app has never run more than one live talk.
- **Backups and alerting**: see P0-4. Rollback by image tag is documented (RUNBOOK "Rollback").
- **Sign-in and invites** are built and documented (RUNBOOK "Accounts"). Confirm on the box that the four `BOLO_FIREBASE_*` values are set and that `BOLO_INVITES_REQUIRED=true` is on. Auto-memory still says "waiting on Firebase config", while the RUNBOOK says the config is in place.
- **Stranger test** (Phase 1a exit) has a checklist (RUNBOOK). No results are logged in `feedback-log.md` yet, and R4 (≤ 60 s to the first word) is unmeasured.

---

## 5. Per-requirement table

### 5.1 Onboarding (ONB)

| ID | Status | Evidence / note |
| --- | --- | --- |
| ONB-1 | Partial | No sign-up before the first talk (`steps.ts` start → welcome → consent → mic → setup → day1 → talk). The welcome is caption-only, not voice (`WelcomeScreen.tsx`). No silent-phone banner. |
| ONB-7 | Done | `ConsentScreen.tsx`: nothing pre-selected, 13+ gates both buttons, under-13 stops. Copy "No, just feedback" (C-minor). |
| ONB-2 | Done | `MicScreen.tsx`: blocked → "Bolo needs your mic to hear you", browser-specific unblock guidance (`micErrors.ts`), auto-continue. The reason line comes on the consent card just before. |
| ONB-3 | Partial | 3 questions as chips with Back (`questions.ts`, `SetupScreen.tsx`). Not asked or answered by voice. Goals read-only in Me. |
| ONB-4 | Done | 30 s ring, under-10 s prompt, up to 3 re-records (`day1.ts`), IndexedDB (`clipStore.ts`), upload only after sign-in with Keep on (`Day1Service.java`). |
| ONB-5 | Partial | Live level, great/noisy/quiet copy, "Continue anyway" (`MicScreen.tsx`). Verdict not used downstream (US-1.5 last criterion). |
| ONB-6 | N/A-later | PRD P1. (Bangla helper lines are shown on onboarding with no toggle.) |

### 5.2 Conversation (TLK)

| ID | Status | Evidence / note |
| --- | --- | --- |
| TLK-1 | Done | Home "Start talking" → `POST /api/talk` → WS (`TalkController.java`, `HomeScreen.tsx:61`). |
| TLK-2 | Done | Bolo speaks first (`BuddyPrompt.OPENING_CUE`), one question at a time, goal-aware opener. |
| TLK-3 | Done | Prompt rule "Never correct…" (`BuddyPrompt.system`). Not red-teamed. |
| TLK-4 | Done | `TurnTaker` + config: speculate 1.0 s, end 2.5 s, 4.0 s when unfinished, Done/Space. |
| TLK-5 | Done | `<5 words` → follow-up; 3 in a row → starter card (`TalkConversation.replyTo`, `systemFor`). |
| TLK-6 | Done | `QuietClock` 8/15/45 s: encourage, hint card with 2 starters, pause. |
| TLK-7 | Partial | Unclear or Bangla turns → kind nudge plus a starter (`UNCLEAR_CUE`). Restating the meaning is deferred (feedback item 3). |
| TLK-8 | Done | Captions streamed per sentence; toggle in the talk and in Me → Speaking. |
| TLK-9 | Done | Wrap-up at time limit (`wrapUpSystem`), grace, End sheet with Keep talking as primary. Copy differs (C5). |
| TLK-10 | Partial | Pause and resume within 30 min while the tab is open, auto-pause on hidden tab (`useTalk.ts:225`). A closed tab or socket ends the talk. |
| TLK-11 | Deferred | Story memory not built. Feedback-log item 4. |
| TLK-12 | N/A-later | PRD P1. |
| TLK-13 | Done | `TalkIntent` repeat/slower in Java; replay icon and R key; "Keep Bolo slower next time?" saves the setting. |
| TLK-14 | Partial | Keyword screen + Haiku classifier, fixed crisis script, help card, no debrief, audio deleted (`CrisisScript.java`, `SafetyGuard.java`, `CoachService.onTalkEnded`). Missing: NFR-21 partner approval, red-team, romance guard. |
| TLK-15 | Missing | Headphones asked; no barge-in path. |
| TLK-16 | Done | Scene, caption, mic ring with time arc, Pause, Done, End, CC toggle (`TalkScreen.tsx`). Shows "Free talk · N min" (not a countdown). |

### 5.3 Assessment (ASM)

| ID | Status | Evidence / note |
| --- | --- | --- |
| ASM-1 | Partial | 4 skills, but per *session*, not per turn (`AssessmentService.assess`, `SkillLevels`; sentence and vocabulary from the Coach LLM). |
| ASM-2 | Partial | `SoundPattern` has all 6. In free talk the s-cluster vowel isn't detectable (phoneme scores only). Accuracy on accented speech untested (NFR-10). |
| ASM-3 | Done | `FluencyStats`: wpm, pauses > 1.2 s inside a turn, filler lexicon. |
| ASM-4 | Partial | ≤ 3 fixes, frequency order (`DebriefPlan`, `PatternDetector`). No clarity weighting, no scenario fix, no improved-repeat drop. Max 1 sound fix (C3). |
| ASM-5 | Partial | Threshold-based (sound < 50 in word < 75, ≥ 2 hits). No explicit confidence value, no noise or overlap awareness, no "skipped because of noise" note. |
| ASM-6 | Done | `min-speech-ms: 60000` → `too_short` → "Not enough talking…" / "Talk a bit more". |
| ASM-7 | Partial | Scored live, composed at end, polled behind "Your coach is listening back". p90 unmeasured. No "Notify me when ready". |

### 5.4 Debrief (DBF)

| ID | Status | Evidence / note |
| --- | --- | --- |
| DBF-1 | Done | Strength quote checked against the transcript (`ReplyCheck.quoteIn`, `clean`) with its clip; fallback is specific (minutes and turns). |
| DBF-2 | Partial | One card at a time with clip, model, tip and Try it (`CoachFlow.FixCard`, `TryIt.tsx`, 3 tries). No autoplay; the filler card has no clip. |
| DBF-3 | Done | Sound, upgrade (≤ 25 words, quote-checked) and habit (filler ≥ 5 or short answers) (`CoachService`, `Habit.java`). There's no pause-habit card. |
| DBF-4 | Partial | Removes the card, "Thanks, we've removed it", logs `fix_dispute(kind)`. No review data, no suppression, no prompt after 2+ disputes. |
| DBF-5 | Partial | Building/Good/Strong, "This is your starting point". No arrows; pronunciation can be missing; no skill detail. |
| DBF-6 | N/A-later | PRD P1. Note: full talk audio is deleted after the debrief (p1c3 ruling), which blocks bubble playback later. |
| DBF-7 | N/A-later | PRD P1. |
| DBF-8 | Missing | No rating anywhere (grep "How was today", `session_rated`: none). |

### 5.5 Drills (DRL)

| ID | Status | Evidence / note |
| --- | --- | --- |
| DRL-1 | Partial | "Practise (3 min)" opens the first sound's fixed deck (`CoachFlow.firstSound`); not generated from the session's fixes. |
| DRL-2 | Missing | No minimal pairs (listen-and-pick rounds). |
| DRL-3 | Missing | No shadowing or turtle speed. |
| DRL-4 | Partial | Try it on the upgrade card (`mode=upgrade`); no 3-round reveal-and-hide drill. |
| DRL-5 | Missing | No skip, queue or retire. |
| DRL-6 | Partial | 3 tries on debrief Try it; practice screen has no limit. |
| DRL-7 | Missing | No summary screen; drills never affect loop counts (no loop counter exists). |

### 5.6 Progress and habit (PRG)

| ID | Status | Evidence / note |
| --- | --- | --- |
| PRG-1 | Done | 4 trends over 30 days in words, after 3 talks (`ProgressPlan.TRENDS_AFTER`, `SkillTrends.tsx`). |
| PRG-2 | Missing | No re-record or comparison ("next plan", p1c3). Only "Play my Day 1 clip". |
| PRG-3 | Done | Up to 5 patterns, New/Improving/Steady/Fixed with a one-off note; a sound opens practice (`ProgressPlan`, `PatternList.tsx`). |
| PRG-4 | N/A-later | PRD P1 (streak). |
| PRG-5 | Missing | No reminders or Web Push. |
| PRG-6 | Missing | No back-off rules. |
| PRG-7 | Partial | Drills offer, then save progress (guests), then "See you tomorrow"; missed steps shown once (`account/loop.ts`). No reminder step. |
| PRG-8 | Done | History newest first, grouped by week, topic and ≤ 3 fix tags, never limited by plan (`HistoryScreen.tsx`, `ProgressPlan`). |

### 5.7 Scenarios (SCN)

| ID | Status | Evidence / note |
| --- | --- | --- |
| SCN-1 | Partial | Free Talk only; no picker or "Change" link. |
| SCN-2 | Missing | No Standup Rehearsal. |
| SCN-3 | N/A-later | PRD P1. |

### 5.8 Account, privacy, plans (ACC)

| ID | Status | Evidence / note |
| --- | --- | --- |
| ACC-1 | Done | Firebase Google popup; token verified server-side (`FirebaseIdTokens.java`); prompt at the end of the first loop (`SaveProgress.tsx`). OTP is P1. |
| ACC-2 | Done | 3 guest talks, then `422 sign-in` (`TalkSessionService.GUEST_TALKS`); prompts after talks 2 and 3; the Not now screen warns about clearing browser data. |
| ACC-3 | Done | Append-only `consent_record` with `policy_version` and time (V1); Me → Privacy switch applies from the next talk (`talk_session.keep_audio`, V13). |
| ACC-4 | Done | Delete recording from History (`CoachService.deleteRecording`); Delete everything with typed DELETE, immediate (`DeleteLearnerService`). No email (ruled). See P0-7 and P1-15. |
| ACC-5 | N/A-later | Plans are Phase 2 (PRD P1; plan p1c2 "Free during the closed beta"). |
| ACC-6 | N/A-later | Phase 2. |
| ACC-7 | Done | Guest→account merge by `LearnerMerged` row moves; "I already have an account" restores goals, history and the Day 1 clip, and skips onboarding (RUNBOOK "Accounts"; `StartScreen.tsx`). |
| ACC-8 | N/A-later | Phase 2. (The closed-beta *invite gate* is built: V12 `invite`, `InviteGate.java`, `scripts/invite.sh`.) |

### 5.9 Settings (SET), Home (HOM)

| ID | Status | Evidence / note |
| --- | --- | --- |
| SET-1 | Done | `learner_settings.buddy_speed` (V8); applies from the next talk. |
| SET-2 | Partial | Captions on/off done; text in `px`, so it doesn't follow the browser font setting. |
| SET-3 | N/A-later | PRD P1. |
| SET-4 | N/A-later | PRD P1. |
| HOM-1 | Partial | One primary plus at most one card (`HomePlan.java`); priority table only partly there (C10). |

### 5.10 Presence (PRS)

| ID | Status | Evidence / note |
| --- | --- | --- |
| PRS-1 | Partial | Static hilltop SVG with a seated silhouette (`scene/art.ts`). |
| PRS-2 | Missing | One scene; no switching. |
| PRS-3 | Missing | No companion states. |
| PRS-4 | Missing | No ambient sound. |
| PRS-5 | Missing | No stereo placement. |
| PRS-6 | Missing | No companion choice. |
| PRS-7 | Partial | `prefers-reduced-motion` respected (`bolo.css:609`); no voice-only mode. |
| PRS-8 | Partial | Scene is inline SVG (tiny); no service worker or offline cache. |

### 5.11 Accessibility (AX)

| ID | Status | Evidence / note |
| --- | --- | --- |
| AX-1 | Partial | Design tokens exist; no recorded contrast audit. |
| AX-2 | Partial | `--tap-min` for quiet buttons; Talk button isn't ≥ 96 px. |
| AX-3 | Partial | aria-labels and keys (Space, Esc, P, R, ↑/↓); some div-buttons; no screen-reader walkthrough on record. |
| AX-4 | Partial | px type; 200% browser zoom untested. |
| AX-5 | Done | Icon plus word for each result (`practice/attempts.ts` RESULT). |
| AX-6 | Done | Captions for Bolo; sentence text next to every clip. |
| AX-7 | Missing | No more-time or off setting. |
| AX-8 | Missing | No "I stammer". |
| AX-9 | Done | Reduced motion and transparency media queries; nothing flashes. |
| AX-10 | N/A-later | PRD P1. |

### 5.12 Non-functional (NFR)

| ID | Status | Evidence / note |
| --- | --- | --- |
| NFR-1 | Partial | Speculative reply and browser-measured `heard_ms` (RUNBOOK SQL). Dhaka 4G p50/p90 not on record. |
| NFR-2 | Partial | Unmeasured p90 (`scripts/coach-probe.sh` exists). |
| NFR-3 | Partial | Scripted REST assessment; unmeasured p90. |
| NFR-4 | Partial | Unmeasured. |
| NFR-5 | Partial | Unmeasured; scene is inline. |
| NFR-6 | Deferred | PCM ruling (p1a2); Opus before mobile web. |
| NFR-7 | Missing | No crash-free tracking. |
| NFR-8 | Missing | No monitoring or alerting, no backups, single box, F0 cap. |
| NFR-9 | Missing | No resend; a drop ends the talk. |
| NFR-10 | Partial | Harness built; Gate 1 NOT DECIDED; the Coach path is unchecked. |
| NFR-11 | Partial | `fix_dispute` logged; no rate report. |
| NFR-12 | Missing | Early cutoff not tracked. |
| NFR-13 | Done | TLS through Caddy; audio AES-256-GCM (`EncryptedFileStore.java`, `BOLO_MEDIA_KEY`). |
| NFR-14 | Partial | Hourly sweep, 24 h or 90 days (`AudioRetentionService`); full talk audio deleted after the debrief; deletion immediate. Day 1 clip exception (C1); orphaned guests (C2). |
| NFR-15 | Partial | Processors disclosed; terms not verified or recorded. |
| NFR-16 | Done | Consent with the current policy checked at `POST /api/talk` and the WS handshake (`TalkHandshakeInterceptor.java`). |
| NFR-17 | Done | Sessions expire after 90 days idle (`GuestService.IDLE_EXPIRY`, `SessionSweepService`). OTP is P1. |
| NFR-18 | Partial | e2e on Chromium and Firefox; mp4 fallback for Safari; Safari and Edge untested; echo cancellation on. |
| NFR-19 | N/A-later | No animated scene yet; device target is mobile release. |
| NFR-20 | Missing | No message bundles. |
| NFR-21 | Partial | Founder pilot approval only; partner approval due before beta. |
| NFR-22 | Partial | `usage` ledger in units; no prices. |

### 5.13 UX rules, navigation and section groups

| ID | Status | Evidence / note |
| --- | --- | --- |
| R1 | Done | One filled button per screen (Home, debrief, sheets). |
| R2 | Done | One tap from Home to the talk. |
| R3 | Done | No text inputs in the core loop (typed DELETE is outside it, as the PRD specifies). |
| R4 | Partial | `first_word_spoken` plus `scripts/metrics.sh`; no 10-tester measurement yet. |
| R5 | Done | ≤ 3 fixes; 3 nav items (Talk, Progress, Me). |
| R6 | Done | Banned-word guard on Coach output (`ReplyCheck.BANNED`); I found no banned words in web copy. |
| R7 | Partial | Most states offer one next step; missing states listed in P2-14. |
| R8 | Partial | Sound card's title comes before its play button; no autoplay. |
| R9 | Done | Debrief opens with a strength; banned words guarded. |
| R10 | Partial | Unmeasured on an office laptop and slow link. |
| R11 | Partial | Keyboard keys in the talk and debrief; no full keyboard map or audit. |
| R12 | Partial | Onboarding resumes (localStorage plus server reconcile); a talk doesn't survive a closed tab or drop; the debrief reopens from the start. |
| NAV | Done | AppShell sidebar: Talk, Progress, Me (`AppShell.tsx`); nothing nested more than 2 levels. |
| §7 edge cases | Partial | About 15 of 28 rows covered (mic, noise, too short, crisis, thinking, paused, busy). Missing: headphones disconnect, echo notice, outage, version, low storage, scene download, reconnect. |
| §10 analytics | Partial | 7 of 30 events (`EventRules.CLIENT`/`SERVER`); no Gate 2 views. |

### 5.14 Deliberately not built (feedback-log, not counted as gaps)

- **Item 1–2** (accent and persona/mood): open discussion. Buddy is still `en-IN-NeerjaNeural`.
- **Item 3** (show that Bolo understood Bangla): costed at about $0.003 per turn, needs Azure S0 (which P0-1 asks for anyway).
- **Item 4** (memory and hook): includes PRD TLK-11 story memory.
- **Item 5** (gamification, push, challenges): the founder should say whether PRD reminders (PRG-5/6) fall under this; see P1-3.
