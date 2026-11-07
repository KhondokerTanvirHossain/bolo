# Screen recipes

How the components assemble into the MVP's screens. Each recipe is the blueprint for its screen; the Screens previews show them built.

## First launch (onboarding)

Seven short steps, voice first, no sign-up (ONB-1 to ONB-7). The aim is the first spoken word within 60 seconds.

1. **ScreenWelcome:** Bolo speaks on the hilltop with captions. Skip intro; "I already have an account" for a new phone.
2. **ScreenConsent:** keep recordings or not, two equal buttons, 13+ tick.
3. **ScreenMicPermission:** one reason, then Android's prompt.
4. **ScreenSetupQuestion** ×3: goal, hardest moment, daily talk time; voice first, chips as fallback.
5. **ScreenMicCheck:** read one line; 5 seconds.
6. **ScreenDayOne:** the 30-second Day 1 clip.
7. The first conversation starts in the scene.

Every step resumes where it was left (US-1.7), and everything before the first conversation works offline.

## Home (Talk tab)

- **Top third:** a greeting in `subhead`, the streak count as a quiet rose `bo-tag--buddy` with a sun, and the scene card showing the Buddy's hook line in its own voice ("Did the payment fix go live? Tell me.") on `scene-plate`.
- **Middle:** at most one HomeCard, chosen by the priority table (US-6.9): Finish your feedback → Re-record your intro → 3-minute practice.
- **Bottom third:** the TalkButton with its scenario line ("Free talk · 5 min · Change"). If a session was paused under 30 minutes ago, it becomes "Continue your chat", with "Start a new chat" as the quiet link.
- On the free plan, the free-talk counter ("4 of 7 free talks left in your first week", later "1 of 2 free talks left this month") sits in `footnote` under the scenario line, never as a banner (ScreenHomeFree).

## Conversation

The screen shows only: the scene, the BuddyCaption (top), the MicRing with its time arc (bottom centre), Pause (bottom left), Done (bottom right, ends the turn) and a small End (top right). Nothing else (TLK-16).

- When it's your turn, the ring turns `sun` and pulses once, with a haptic tick. Your words are never shown.
- At 8 s of silence the Buddy speaks kindly. At 15 s the HintCard rises. At 45 s the ring shows Paused.
- Tapping End opens the Sheet: "End and get feedback?", with **Keep talking** as the primary action.
- The small scene button (top left) opens ScreenScenePicker: Hilltop, Evening drive or Voice only, and the companion. One tap applies; the Buddy pauses while it is open. The same sheet lives in Me → Settings → Scene and companion. ScreenConversationDrive shows the screen in the drive.

## Scoring wait

On full-screen `canvas`, show the listen-back waveform, "Your coach is listening back" and one line underneath. After 15 s, a quiet "Notify me when ready" appears. There is no progress bar and no percentage.

## Debrief

A horizontal pager, one card per screen, with ProgressDots at the top:

1. **StrengthCard.** The companion turns to face you, and you can play the clip.
2. **Up to 3 fix cards** (FixCard, SentenceUpgrade, HabitCard). The learner's clip auto-plays once, then the model clip. Try it is the one primary action, with the ResultPill above it after an attempt. "That's not what I said" sits under it on every card. A sound fix card swaps its one-line tip for a MouthTip after 3 sessions on the same sound, or always for hard-of-hearing learners (ScreenFixMouth).
3. **SkillLevels** with arrows (no arrows in session 1: "This is your starting point"), then **Practise (3 min)** as primary and **Later** as quiet.

Swipe left to advance and right to go back; the cards follow the finger 1:1 and hand off on a velocity-projected spring.

## Drills

Up to 3 drills from today's fixes, one per screen, each with the same header: close, three dots, quiet **Skip**, and the Coach mark. ScreenDrillPairs (hear, then say), ScreenDrillShadow (repeat after the model) and ScreenDrillSayBetter (your own sentence, from reading to memory). Three tries per item, then "Good effort. We'll come back to this one." The flow ends on ScreenDrillDone with a single **Done**.

The scenario (Free talk or Standup rehearsal, Friendly or Realistic) is picked from **Change** under the Talk button: ScreenScenarioPicker.

## End of first loop (PRG-7)

Four full screens in a fixed order, each with one job. There is no rating and no plan offer.

1. **Drills offer.** The last debrief card: **Practise (3 min)** and **Later**.
2. **Save your progress** (ScreenSaveProgress). A dusk strip, a KeepList of what's at stake (Day 1 clip, today's feedback, goals), then SignInButtons. **Not now** opens ScreenGuestNotNow, which says plainly that guest progress lives on this phone only.
3. **Reminder** (ScreenReminder). "When should Bolo check in?", a NotificationPreview, ReminderSlots and **Remind me at 6:30 pm**. Android's permission prompt follows on the same step.
4. **Home** (ScreenSeeYouTomorrow). The Buddy says "See you tomorrow" on the scene card, and one HomeCard confirms the reminder.

If the app closes partway, show only the missed steps, once, before Home.

## Account

- **Google:** Android's Credential Manager sheet over the current screen; no screen of our own.
- **Phone:** ScreenPhoneNumber (+880 fixed, **Send code** above the number keyboard) → ScreenPhoneCode (CodeInput, auto-read, checks itself on the sixth digit; resend after 30 s; **Try Google instead** after 2 resends).
- **Guest limit:** on the 4th talk, the Talk button opens ScreenGuestLimit, a Sheet over Home with SignInButtons.
- **New phone:** "I already have an account" on the welcome screen → sign-in → ScreenRestore → Home. Onboarding is skipped.
- Screens with SignInButtons have no mustard button, because Google's button keeps Google's colours.

## Progress tab

ScreenProgress (and ScreenProgressNew for the first days). Under the `large-title` "Progress" and one line of effort: VoiceCompare (when unlocked) → SkillTrends (2×2) → "My patterns" PatternList → the three latest SessionRows and **See all**, which opens ScreenHistory. With fewer than 3 sessions, the trends area is a StatePanel: "Your trends appear after 3 sessions · 1 of 3".

## Me tab

ScreenMe: My goals → Your plan and Invite friends → Settings, Privacy, Help. ScreenSettings has two SettingsRow groups (Speaking; Display & data). ScreenPrivacy says what is kept, why and for how long, then the controls, with **Delete everything** as the app's only `critical` button. Nothing is nested more than 2 levels deep.

## History

ScreenHistory lists SessionRows newest first under week labels; each opens ScreenHistorySession: the whole talk (if kept), the strength, the three past fixes with their model clips, **Practise these (3 min)**, and a quiet **Delete recording**.

## Plans

- **Free:** 7 talks in the first 7 days (from the first talk), then 2 talks a month, back on the 1st of each month. A talk is a 5-minute conversation plus the coach's feedback. The free week ends just as the Day 1 vs Day 7 voice comparison unlocks, the most natural moment to show plans.
- **Paid, monthly:** Basic ৳300, Premium ৳500, Ultimate ৳1000. Plans differ only in how many talks you get; every plan has every feature, and every plan states its limit as a number. The limits shown (20, 50, 100 talks a month) are placeholders until the cost test.
- **Flow:** Talk with no free talks left → ScreenFreeLimit → ScreenPlans → ScreenPlanPay (bKash or card) → Home. Me → Plan is ScreenMePlan; cancelling is ScreenCancelPlan, two taps in total.
- **Invite friends (first year only):** 3 invited friends who finish a first talk earn a month of Basic (ScreenInvite). Entry points: Me → Invite friends, ScreenFreeLimit, and the end-of-free-week note.
- No urgency anywhere: no badges, timers, crossed-out prices or "upgrade" banners. The free limit never interrupts a talk in progress.
- **Reminders on the free plan:** daily during the first week; after it, only when free talks are available (for example "Your 2 free talks are back" on the 1st), never daily nudges toward a talk the learner can't take.
