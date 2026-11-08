# Bolo web: design audit

Repo `bolo`, branch `p0-foundations`, HEAD `b18c57d`. Audited 9 Nov 2026. This was a read-only audit: no repo file was changed.

**Sources compared**
- Design system: `design/design-system/` (README, 1-voice-and-copy, 3-presence, 4-screen-recipes, 5-desktop-and-web, tokens, component READMEs).
- Prototypes: `design/prototypes/desktop/index.html` (18 `data-screen`s) and `design/prototypes/phone/index.html` (45 screens).
- App: `web/src/**`.

**How it was checked**
- I read the code first.
- I then rendered the app with `next dev` on port 3247, with the API mocked the same way `web/e2e` mocks it. Each page was rendered at 1280, 900 and 390 px, and I measured scrolling, overflow, primary buttons, target sizes and focus order.
- The dev server was stopped afterwards.
- **Note.** While I was auditing, another session changed files in the working tree; I touched none of them. The changed files are `web/src/styles/bolo.css`, `web/e2e/me.spec.ts` and four `api/.../coaching` files. The `bolo.css` change moves the ≤420 px phone rules up to ≤720 px, which fixes the "Me two panes cramped at 421–720" part of the breakpoint P2. Every finding below is against HEAD `b18c57d`.
- Screenshots and the metrics JSON were taken in a local scratch folder and are not committed.
- The Talk screen needs a live socket and mic, so it was audited from code. Only its dock and key row were rendered, as static HTML at 390 px.

---

## 1. Coverage

| Design screen (component / prototype id) | Status | Where in `web/src` |
| --- | --- | --- |
| DesktopMeetBolo / `start` | Built | `onboarding/screens/StartScreen.tsx` |
| ScreenWelcome / `welcome` | Partly: caption only, no voice, moves on after 6 s (ruled) | `onboarding/screens/WelcomeScreen.tsx` |
| ScreenConsent / DesktopConsent / `consent` | Built (adds "I'm under 13", ruled) | `onboarding/screens/ConsentScreen.tsx` |
| ScreenMicPermission + ScreenMicCheck + DesktopMicPicker / `mic` | Built, merged into one card (ruled) | `onboarding/screens/MicScreen.tsx` |
| ScreenSetupQuestion ×3 / `setup` | Partly: chips and keys only, no voice answer | `onboarding/screens/SetupScreen.tsx` |
| ScreenDayOne / `day1` | Built | `onboarding/screens/Day1Screen.tsx` |
| ScreenHome / DesktopHome / `home` | Partly: full-scene Home (deliberate). No greeting, streak, Buddy hook line, TalkButton disc or scenario line | `home/HomeScreen.tsx` |
| ScreenHomeFree | Missing (plans are Phase 2, deliberate) | – |
| ScreenSeeYouTomorrow | Partly: caption note only, no reminder card (deliberate) | `FirstTalkScreen.tsx:148` |
| DesktopTalk / `talk` | Built. No scene button; Repeat appears only as the caption replay button | `talk/TalkScreen.tsx` |
| `endsheet` | Built, with copy drift | `TalkScreen.tsx:232-244` |
| HintCard, paused, crisis card | Built (crisis card is app-only) | `TalkScreen.tsx` |
| ScreenScenePicker, ScreenConversationDrive, companions (PRS-6), voice-only | Missing | – |
| ScreenScenarioPicker ("Change") | Missing | – |
| Scoring wait / `scoring` | Built, without "Notify me when ready" | `coach/CoachFlow.tsx:69-84` |
| DesktopFeedback / `debrief`: strength, fixes, skills | Partly. No skill arrows, no play-slower, no filler timeline, no Bangla tip (ruled), no `T` key | `coach/CoachFlow.tsx` |
| ScreenFixMouth / MouthTip / MouthDiagram | Missing | – |
| DesktopDrill, ScreenDrillPairs / Shadow / SayBetter, ScreenDrillDone | Missing. Sound practice stands in for them | `practice/PracticeScreen.tsx`, `ReadAloud.tsx` |
| ScreenSaveProgress / DesktopSaveProgress / `save` | Built: Google only (deliberate); no scene behind it; no Bangla helper | `account/SaveProgress.tsx` |
| ScreenGuestNotNow | Built | `SaveProgress.tsx:50-57` |
| ScreenReminder / DesktopReminder / ReminderSlots / NotificationPreview | Missing (deliberate, waits for Phase 1c reminders) | – |
| SignInButtons | Partly: Google only (deliberate) | `account/GoogleButton.tsx` |
| ScreenPhoneNumber / ScreenPhoneCode / CodeInput | Missing (deliberate) | – |
| ScreenGuestLimit | Built | `account/GuestLimit.tsx` |
| ScreenRestore | Missing: restore goes straight to Home | `onboarding/Onboarding.tsx:118-137` |
| ScreenProgress / DesktopProgress / `progress` | Partly. No VoiceCompare, no Effort card or streak, no dashboard grid | `progress/ProgressScreen.tsx`, `SkillTrends.tsx`, `PatternList.tsx` |
| ScreenProgressNew | Partly. No Day 1 clip or "Day 7 unlocks…"; patterns section shown instead of hidden | same |
| ScreenHistory + SessionRow | Built (no back link to Progress) | `progress/HistoryScreen.tsx`, `SessionRow.tsx` |
| ScreenHistorySession | Partly. Reuses the debrief two-pane, not compact past-fix rows; no whole-talk player (deliberate) | `CoachFlow.tsx` (`history` prop) |
| ScreenMe / DesktopMe / `me` | Partly. Single column, not section nav + pane; no Help or About; no plan or invite rows (deliberate) | `me/MeScreen.tsx`, `me/AppShell.tsx` |
| ScreenSettings | Partly: Speaking has speed and captions only (deliberate); chips instead of value row and toggle | `me/SpeakingSettings.tsx` |
| ScreenPrivacy | Partly. No "Delete my recordings" (audio only), no "never used to train other AI" | `me/PrivacyScreen.tsx`, `me/DeleteEverything.tsx` |
| Plans, PlanPay, MePlan, CancelPlan, FreeLimit, Invite, DesktopPlans | Missing (Phase 2, deliberate) | – |
| Public privacy page (app only) | Built, but broken (P0-1) | `app/privacy/page.tsx` |
| Closed-beta and offline cards (app only) | Built | `onboarding/Onboarding.tsx:142-168` |

---

## 2. Findings by priority

### P0: broken, misleading or inaccessible

**P0-1. The public `/privacy` page cannot scroll, so over half of it is unreachable. Size S.**
- **Cause.** `web/src/styles/bolo.css:761-762` carries the prototype's preview-harness rules into the app:
  - `html, body { overflow: hidden; background: #0b0816 }`
  - `#root { position: absolute; … }`
  - and with `.dk-root { overflow: hidden; height: 100% }`, `#root` clips everything below the first screen.
- **Measured.**
  - At 1280×800 the content is 1422 px tall and only 800 px is visible. "Deleting your data" sits at y = 1171.
  - At 390×844 the content is 1935 px tall and only 844 px is visible.
  - Mouse wheel, End and PageDown all leave `scrollY` and `root.scrollTop` at 0.
- **Why it matters.** "Who processes it" and "Deleting your data" can't be read. This page is linked from Google's sign-in consent screen and from Me → Privacy.
- **Design reference.** 5-desktop-and-web, "Under 640px … also what 400% browser zoom becomes (WCAG 1.4.10 reflow)".
- **Fix.** Delete the harness block at `bolo.css:761-768` (`html, body`, `#root`, `#pbtn`, `#ppanel`, `.prow`, `.pg`). `.dk-screen` and `.dk-main` already manage their own scrolling. Alternatively, give the privacy page its own scroll container.
- **Also check.** The same harness rules make `body` dark (`#0b0816`) behind everything.

### P1: visible drift on core screens (Talk, Coach debrief, Progress, Me), or accessibility gaps

1. **Debrief and history session: the way out is off-screen on phone width or at 400% zoom. Size S.**
   - Below 720 px the card list becomes a sideways strip (`bolo.css:690-701`).
   - The footer holding "Close feedback" / "Back" and "Delete recording" (`CoachFlow.tsx:156-162`) becomes the last item in that strip. At 390 px it measured x = 939–1101, off-screen.
   - On the strength and fix cards there is then no visible exit. The selected card also isn't scrolled into view in the strip.
   - Ref: DesktopFeedback; 5-desktop-and-web "Under 640px The phone layout"; WCAG 1.4.10.
2. **The Coach shows server latency to the learner. Size S.**
   - The Skills card shows "Bolo answered in about 1.4 s (slowest 2.6 s)." (`CoachFlow.tsx:286-288`). This shows in the live debrief and in the history session.
   - The no-debrief summary does the same (`TalkScreen.tsx:169-173`).
   - This is developer telemetry. Ref: 1-voice-and-copy, "Numbers only where they help … never scores"; prototype `debrief` detail 4 has no such line.
3. **The end sheet's copy is out of date. Size S.**
   - The app says "End this talk?" with **End** / **Keep talking**, and no body (`TalkScreen.tsx:236-238`).
   - The design says "End and get feedback?", "You've talked for 3 minutes. That's enough for useful feedback.", **End and get feedback** / **Keep talking**. Ref: prototype `endsheet`; recipe "Conversation".
   - The P1a-2 ruling chose this copy "because the debrief doesn't exist yet". The debrief now exists, so that reason no longer holds.
4. **Practice has two filled (`sun`) buttons. Size S.**
   - "Start recording" (`ReadAloud.tsx:88-91`) and "Next sentence" (`PracticeScreen.tsx:49`) are both `bo-btn--primary`; the render confirmed both.
   - Ref: README principle 2, "one filled button (`sun`)". "Practise (3 min)" from the debrief lands here.
   - Fix: make "Next sentence" secondary.
5. **Progress can't be reached from Home. Size S.**
   - Home's only navigation is a "Me" scene button (`HomeScreen.tsx:31`). Progress is reachable only through Me → sidebar, i.e. two clicks with no cue.
   - Ref: 5-desktop-and-web, "the same three tabs (Talk, Progress, Me)". The full-scene Home is deliberate; this missing tab is not.
   - Fix: add a Progress scene button next to Me.
6. **Progress has no "Your voice, then and now". Size M.**
   - The Day 1 clip is already on the device (`FirstTalkScreen.tsx:45`) and Home plays it, but Progress doesn't show it.
   - It is missing the "Day 7 unlocks in N days" line, the effort line's day count ("Day 3 · 1 talk · 5 minutes of speaking"), and the Effort card.
   - Refs: ScreenProgress items 1–2; ScreenProgressNew "Voice"; prototype `progress` `.dk-span-8` and `.dk-span-4`; `ProgressScreen.tsx:35-43`.
7. **The skills card never shows arrows. Size M (needs API data).**
   - After the first session it says "Your skills today" with no arrows (`CoachFlow.tsx:278`).
   - Ref: recipe "Debrief" 3, "SkillLevels with arrows (no arrows in session 1)".
8. **Dialogs don't trap focus, don't close on Esc, and focus can start behind them. Size M.**
   - Measured on Delete everything: Tab leaves the dialog after one stop and reaches the sidebar (Talk, Progress, Me) and the page behind. Esc does nothing.
   - The same applies to Delete recording (`CoachFlow.tsx:184-196`), Guest limit and Save progress.
   - Over the guest-limit sheet, Home's `autoFocus` "Start talking" (`HomeScreen.tsx:61`, rendered at `FirstTalkScreen.tsx:97`) takes focus behind the modal.
   - Only the talk end sheet handles Esc.
   - Ref: 5-desktop-and-web, "Esc closes them with the safe choice … dialogs trap focus and return it".
9. **A failed "Delete recording" fails silently. Size S.**
   - On failure the dialog just closes (`CoachFlow.tsx:105-111`), so the learner may believe the audio is gone.
   - Show "That didn't work. Try again." and keep the dialog open, as Delete everything already does.

### P2: polish

**Layout and responsive**
- **Breakpoints differ from the design.**
  - The design has a 1024–1279 two-column tier, a top bar at 640–1023, and a phone layout with bottom tabs below 640.
  - The app keeps the sidebar down to 721 px, then uses a top bar at every width down to 390 (`bolo.css:718-743`), with no bottom tab bar.
  - Me's two-pane rule at 421–720 is cramped. Size M.
- **Progress is one long column.** The prototype is a 12-column dashboard: patterns and history side by side, voice and effort side by side (`.dk-dash`, `.dk-span-*`). Size M.
- **Me is one column.** The design has a section nav on the left and settings on the right (`.dk-me`). The "On this computer" group (microphone, pause on tab switch, shortcuts) is missing (DesktopMe). Size M.
- **Settings controls differ.** Captions should be a `bo-toggle` and speed a value row; the app uses chips (`SpeakingSettings.tsx:14-31`). Sign out should be quiet at the bottom, not `bo-btn--secondary` (`MeScreen.tsx:58-62`). Size S.
- **No back links.** History has no back link to Progress, and Privacy none to Me (phone prototype `history` / `privacy` use `arrow_back`). Size S.
- **Save progress has no scene.** The prototype `save` shows `bo-scene` behind the card; the app shows only `dk-dim` over plain canvas (`SaveProgress.tsx:36-37`). Size S.
- **Home card position.** At 1280 the card hides the companion (`HomeScreen.tsx:42-44`, `bottom:150`). At 390 the companions are cropped off the left edge (3-presence, "important parts in the middle"). Size S.

**States (StatePanel: icon, headline, Bangla line, action)**
- **Progress and History.**
  - Their empty states lack the icon and the Bangla line (`ProgressScreen.tsx:21-31`, `HistoryScreen.tsx:28-34`).
  - Their load errors are a bare footnote with no heading, no action and no Bangla (`ProgressScreen.tsx:18`, `HistoryScreen.tsx:26`). Size S.
- **Patterns before 3 talks.** The design hides the section; the app shows "Your patterns appear after your first feedback." (`PatternList.tsx:32-34`). Size S.
- **Bangla helpers missing on error and flow screens.** Offline, closed-beta, Save progress and Not now have none. Phone `save` has "আপনার অগ্রগতি রেখে দিন, নতুন ফোনেও।". Refs: `Onboarding.tsx:142-168`, `SaveProgress.tsx`. Size S (copywriter strings).
- **Scoring wait has no "Notify me when ready" after 15 s** (recipe "Scoring wait"). It depends on notifications. Size S.

**Copy and components**
- **Pattern rows.**
  - The sub line reads "Tap to practise" on desktop. The design uses examples or counts ("bhery", "bhoice" / "4 per talk").
  - Rows that can't be clicked still get the pointer cursor (`bolo.css` `.bo-row{cursor:pointer}`, `PatternList.tsx:45,53`). Size S.
- **Privacy copy.**
  - "The text of your talks and your scores" / "Text and scores stay" contradicts "never scores".
  - It is missing "Used to train other AI: Never" (NFR-15) and the note that turning Keep off stops Day 1 vs today.
  - Refs: `PrivacyScreen.tsx:26,45`; ScreenPrivacy. Size S.
- **Guest limit KeepList.** Rows should carry a real count ("3 talks and their feedback"); the app says "Your talks and their feedback" (`GuestLimit.tsx:29`; KeepList "Sub lines are concrete"). Size S.
- **Debrief keys.** No `T` (try it) or `Esc` (back to Home), the nav hint drops "T try it" (`CoachFlow.tsx:181`), and no "?" shortcut sheet. Size S.
- **Debrief extras.**
  - The ResultPill sits below Try it; the design puts it above (`ReadAloud.tsx:93`).
  - The clip has no pause and no "Play slower" tool (`Clip.tsx`).
  - Play discs are 40×40 against `tap-min` 48. Size S.
- **Mic ring while Bolo speaks.** It uses `bo-mic--thinking`, a spinning arc, when Bolo is speaking (`TalkScreen.tsx:198`). The design says no spinners and that only the two stars loop. Size S.
- **Repeat key.** `R` has a visible button only while captions are on (`TalkScreen.tsx:163`); the design says "Every key has a visible button too". Size S.
- **Mic check copy.** "Bolo carries on by itself." in the blocked and busy states is unclear (`MicScreen.tsx:65,74`). Size S.
- **Undefined token.** `--success-ink` doesn't exist (`app.css:18`), so "Sounds great" renders in ink instead of `nailed`. Size S.

**Tokens and theming**
- **Night theme is never applied.** `data-theme="daylight"` is fixed (`layout.tsx:22`); README says "Two themes follow the system". Size M.
- **Token bypasses not inherited from the prototype.** Size S.
  - Delete-everything input: `borderRadius: 12`, `height: 48`, raw `font` (`DeleteEverything.tsx:27-28`). Use `--radius-sm`, `--tap-min` and a type token.
  - Privacy list: `fontSize: 15` (`PrivacyScreen.tsx:43,48`).
  - Fixed-pattern note restyles `bo-tag` (`PatternList.tsx:37`).
  - Trends state title override (`SkillTrends.tsx:17`).
  - Wordmark recoloured with a raw `#1D1236` (`AppShell.tsx:7`). The prototype has a `wm-day` mark, and ink is `#1f1433`.
  - The other inline values (StartScreen, SetupScreen, talk top line, fix title) were copied as-is from the prototype.
- **Prototype harness CSS shipped.** Besides P0-1, `bolo.css` ships prototype-only selectors (`#pbtn`, `#ppanel`, `.prow`, `.pg`, `.dk-perm*`) with raw hex colours. Size S.
- **Icon font flashes as text.** Material Symbols loads with `display=swap`, so names like "graphic_eq" first show as words. The first render measured the nav buttons 159 px wide (`layout.tsx:18`; use `display=block` for the icon family). Size S.
- **Bangla helper text has no `lang="bn"`.** `<html lang="en">`, so screen readers read Bangla with an English voice (`.bo-flow__bn` in 5 places). Size S.
- **Small targets.** The "See all" link is 42×20; the toggle is 52×32 (the component's own size). Size S.

**Checked and fine**
- Anek Bangla (with the Noto Sans Bengali fallback) is the computed font everywhere.
- Token values in `bolo.css` match `tokens.css` exactly.
- Token text pairs pass AA. Measured: ink-secondary on canvas 7.0, ink-tertiary on canvas 5.1, sun-ink on canvas 7.3, hills-ink on hills-soft 6.1, critical on canvas 6.0.
- `:focus-visible` uses the 2px `focus-ring` with a 3px offset.
- `prefers-reduced-motion` and `prefers-reduced-transparency` are handled.
- No banned words or emoji appear in client copy.
- `critical` appears only in Me → Privacy.
- SaveProgress and GuestLimit have no mustard button.
- Consent buttons are equal secondaries.
- The tab title is "Bolo".

---

## 3. Copy drift

| Where | Design system / prototype | App | Note |
| --- | --- | --- | --- |
| End sheet title | "End and get feedback?" | "End this talk?" | P1; ruling's premise has lapsed |
| End sheet body | "You've talked for 3 minutes. That's enough for useful feedback." | (none) | P1 |
| End sheet action | **End and get feedback** | **End** | P1 |
| Debrief Skills card | (no latency line) | "Bolo answered in about 1.4 s (slowest 2.6 s)." | P1 |
| Debrief key hint | "Space plays your clip · T try it" | "Space plays your clip" | P2 |
| Home Talk control | TalkButton "Talk" + "Free talk · 5 min · Change" | "Start talking" (pill) | Full-scene Home is deliberate; still drift |
| Home caption | "Good evening, Rafi" / "Ready when you are." + Buddy hook "Did the payment fix go live? Tell me." | "Ready when you are. Bolo talks out loud, so turn your sound on. Your Day 1 clip is saved on this device." | Hook needs memory (not built) |
| Home card | HomeCard "Finish your feedback" · "2 fixes left from yesterday" | "Your coach's feedback is ready." + button "Finish your feedback" | P2 |
| See you tomorrow | "Nice talking with you, Rafi. See you tomorrow." | "See you tomorrow. Bolo will be here." | No name in app |
| Setup footnote | "Say it, or click one. Keys 1–4 work too." | "Click one. Keys 1–4 work too." | Voice answer not built |
| Day 1 step label | "5 of 5" | "Last step" | Ruled |
| Mic, too quiet | "Speak a little louder or hold the phone closer." | "Speak a little louder, or move closer to the mic." | Fine desktop adaptation |
| Mic blocked / busy | "Bolo needs your mic to hear you" + where to unblock | "…{guidance} Bolo carries on by itself." | Unclear tail |
| Paused (tab hidden) | "Paused while you were away" | "Paused. Tap to continue." | Ruled (P1a-4); "Tap" on desktop |
| Progress effort | "21 days · 14 talks · 70 minutes of speaking" | "3 talks · 16 minutes of speaking" | Day count missing |
| Trends gate | ScreenProgressNew "Your trends appear after 3 talks" + 3 dots; recipe "after 3 sessions · 1 of 3" | "Your trends appear after 3 sessions" + "1 of 3" | Design itself is inconsistent |
| Pattern row sub | "bhery", "bhoice" / "4 per talk" / "Seen in 2 talks" | "Tap to practise" (or nothing) | P2 |
| Patterns before 3 talks | (section hidden) | "Your patterns appear after your first feedback." | P2 |
| History empty | StatePanel "Your talks will appear here" + Bangla + **Talk** | Title + **Talk**, no Bangla or icon | P2 |
| Me subtitle | "rafi.ahmed@gmail.com · Free plan" | "… · free during the closed beta" | Deliberate |
| Privacy keeps | "Your voice clips · 90 days / Words and feedback · While you have an account / Used to train other AI · Never" | List incl. "The text of your talks and your scores"; no AI-training line | P2 |
| Keep recordings (off) | Explains Day 1 vs today will stop | "Your audio is deleted within 24 hours. Text and scores stay." | P2 |
| Save progress Bangla | "আপনার অগ্রগতি রেখে দিন, নতুন ফোনেও।" | (none) | P2 |
| Not now body | "…on this phone only. If you uninstall Bolo, it goes too." + Bangla | "…in this browser only. If you clear your browsing data, it goes too." | Good browser adaptation; Bangla missing |
| Guest limit KeepList row 1 | "3 talks and their feedback" | "Your talks and their feedback" | P2 |
| Offline boot | Error screens carry a Bangla helper | "Bolo can't reach the internet" / "Check your connection, then try again." (no Bangla) | P2 |
| Practise icon | `timer` | `record_voice_over` | Trivial |

---

## 4. Deliberate deviations (not counted as defects)

**Plan Global Constraints**
- **Home** keeps its full-scene layout. The dashboard Home inside the sidebar is a later redesign (p1c2).
- **Home priority** (p1a6): Talk is always the primary; at most one card, either "Finish your feedback" or "Practise *the v sound*"; quiet links are "Practise sounds" and "Play my Day 1 clip".
- **Plans are Phase 2.** The sidebar card says "Closed beta / Bolo is free while we test it together." and Me says "free during the closed beta". There is no free counter, See plans, Invite, ScreenHomeFree, ScreenFreeLimit or plan screens (p1c2, p1c1).
- **No whole-talk player** in the history session. Raw talk audio is deleted once feedback is ready (p1c3 and its ruling).
- **Bolo's voice and persona are unchanged** pending the founder discussion (p1a3, p1a4, p1a6). The welcome is caption-only until a TTS vendor is chosen (P0 ruling).
- **Google-only sign-in.** No "Use phone number", ScreenPhoneNumber or ScreenPhoneCode in the beta (p1c1).
- **No reminder step.** It waits for Phase 1c reminders, so ScreenSeeYouTomorrow has no reminder card (p1c1).
- **Settings** show only Bolo's speed and Captions. The other rows are 1b/P1 (p1a6).
- **Me** shows goals read-only (p1c2).
- **Delete everything** is immediate, with no confirmation email (p1c2).
- **Progress trends** are a 2×2 grid with native SVG tooltips and a hidden table (p1c3, Task 4 ruling). DesktopProgress shows four in a row.
- **History** is never limited by plan, and shows "Short talk" / "No feedback" in place of tags (p1c3).

**Ledger "Ruling:" lines**
- **Onboarding** (P0 rulings):
  - permission, picker and mic check are merged into one "Choose your microphone" card;
  - the button reads "Continue anyway" when noisy;
  - an "I'm using headphones" checkbox and an "I'm under 13" link were added.
- **Day 1** (P1a rulings):
  - the label reads "Last step";
  - recording starts on a Record button, not on entry;
  - a quiet "Record again" shows for 4 s after saving.
- **Talk:**
  - PCM instead of Opus;
  - the hidden tab shows "Paused. Tap to continue.";
  - the end sheet reads "End this talk?" (P1a-2). This one is flagged above because its premise is gone.
- **Coach v1** (P1a-3 rulings):
  - at most 1 sound fix;
  - filler cards have no tap-to-hear timeline;
  - the clip wave is decorative;
  - no Bangla tip (DBF-7) or mouth tips yet;
  - the strength falls back to a factual line.
- **Try it** (P1a-5 rulings):
  - hidden for sentences over 20 words;
  - after 3 tries, "This one takes practice. Come back to it any time.";
  - Nailed threshold at a mean of 70 or more.
- **Fonts and CSS** (P0 rulings):
  - fonts load from Google Fonts until the Phase 1c CSP pass;
  - the CSS is the prototype stylesheet extracted verbatim. That verbatim copy is how the harness rules behind P0-1 got in.
- **History topic** comes from the Coach, with "Free talk" as fallback (p1c3 ruling).
