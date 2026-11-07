import os, re, sys, random
S = sys.argv[1]; C = S + '/ds/project/components'
conv = open(S + '/ds/conv.html').read(); home = open(S + '/ds/home.html').read()
HILL_SVG = re.search(r'<svg class="bo-scene__art".*?</svg>', conv, re.S).group(0)
PHONE_HOME = re.search(r'<div class="bo-phone">(.*)</div></div>\s*</body>', home, re.S).group(1)
def icon(n, fill=False, cls=''):
    return f'<span class="bo-icon{" bo-icon--fill" if fill else ""}{(" " + cls) if cls else ""}" aria-hidden="true">{n}</span>'
def status(t='9:41', scene=False):
    return f'<div class="bo-statusbar{" bo-statusbar--scene" if scene else ""}"><span>{t}</span><span>{icon("signal_cellular_alt", cls="bo-icon--sm")} {icon("battery_5_bar", cls="bo-icon--sm")}</span></div>'
def tabbar(sel):
    return '<nav class="bo-tabbar" role="tablist">' + ''.join(
        f'<button class="bo-tab" role="tab" aria-selected="{"true" if n == sel else "false"}"><span class="bo-tab__pill">{icon(i)}</span>{n}</button>'
        for n, i in (('Talk', 'graphic_eq'), ('Progress', 'insights'), ('Me', 'person'))) + '</nav>'
def page(title, sub, body):
    return f'''<!-- @dsCard group="Screens" height=820 subtitle="{sub}" page -->
<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><title>{title} — Bolo</title></head>
<body>
<div class="bo-stage bo-stage--center" style="padding:24px 0">
{body}
</div>
</body>
</html>
'''
def phone(*parts, cls='bo-phone'): return f'<div class="{cls}">\n' + '\n'.join(parts) + '\n</div>'
def dots(n, on): return '<div class="bo-dots" aria-label="Step ' + str(on) + ' of ' + str(n) + '">' + ''.join(f'<i{" class=\"on\"" if i + 1 == on else ""}></i>' for i in range(n)) + '</div>'
def wave(seed, n=26, on=0):
    random.seed(seed); hs = [4, 6] + [random.randint(6, 26) for _ in range(n - 5)] + [8, 5, 4]
    return ''.join(f'<i{" class=\"on\"" if i < on else ""} style="height:{h}px"></i>' for i, h in enumerate(hs))
def clip(seed, model=False, time='0:03', on=0, label='Play'):
    return (f'<div class="bo-clip{" bo-clip--model" if model else ""}"><button class="bo-clip__play" aria-label="{label}">{icon("play_arrow", True)}</button>'
            f'<div class="bo-clip__wave" aria-hidden="true">{wave(seed, on=on)}</div><span class="bo-clip__time">{time}</span>'
            + (f'<button class="bo-clip__tool" aria-label="Play slower">{icon("slow_motion_video", cls="bo-icon--sm")}</button>' if model else '') + '</div>')
def micring(state, label, dash):
    core = {'buddy': 'graphic_eq', 'turn': 'mic'}[state]
    return (f'<div class="bo-mic bo-mic--{state}" role="status" aria-label="{label}"><svg class="arc" viewBox="0 0 96 96" aria-hidden="true"><circle class="track" cx="48" cy="48" r="46"/>'
            f'<circle class="left" cx="48" cy="48" r="46" stroke-dasharray="{dash} 289.0"/></svg><div class="bo-mic__core">{icon(core, cls="bo-icon--lg")}</div></div>')
def row(title, sub=None, value=None, chev=True, toggle=None, ic=None):
    t = f'<div class="bo-row">' + (f'<span class="bo-row__icon">{icon(ic, cls="bo-icon--sm")}</span>' if ic else '')
    t += f'<div class="bo-row__body"><div class="bo-row__title">{title}</div>' + (f'<div class="bo-row__sub">{sub}</div>' if sub else '') + '</div>'
    if value: t += f'<span class="bo-row__value">{value}</span>'
    if toggle is not None: t += f'<button class="bo-toggle" role="switch" aria-checked="{"true" if toggle else "false"}" aria-label="{title}"></button>'
    elif chev: t += icon('chevron_right', cls='bo-chev')
    return t + '</div>'
F = {}

# ================= ONBOARDING =================
F['ScreenWelcome'] = (page('ScreenWelcome', 'First launch: Bolo speaks first, no sign-up', phone(
 HILL_SVG, status(scene=True),
 f'''<div style="position:relative;z-index:2;display:flex;justify-content:flex-end;padding:48px var(--space-5) 0"><button class="bo-scenebtn">Skip intro</button></div>
  <div style="position:relative;z-index:2;padding:var(--space-4) var(--space-5) 0">
    <div class="bo-caption"><p class="bo-caption__text">Hi, I'm Bolo. We'll just talk, like friends. <span class="dim">No one is grading you.</span></p></div>
  </div>
  <div style="flex:1"></div>
  <div style="position:relative;z-index:2;display:flex;flex-direction:column;align-items:center;gap:var(--space-4);padding:0 var(--space-5) var(--space-8)">
    {micring('buddy', 'Bolo is speaking', '220')}
    <button class="bo-scenebtn" style="background:transparent">I already have an account</button>
  </div>''', cls='bo-phone bo-scene')), '''# ScreenWelcome

The first thing a new learner sees and hears (ONB-1, US-1.1). There is no sign-up, no carousel, no "Get started": Bolo starts talking on the hilltop within 3 seconds of the splash.

- **Voice first.** Bolo's welcome plays with captions word by word. The mic ring shows "Bolo is speaking". The welcome is about 15 seconds and ends by handing over to the consent step.
- **Skip intro** (top right) jumps straight to consent. **I already have an account** (bottom, quiet) goes to sign-in and ScreenRestore; onboarding is then skipped (US-8.4).
- **Phone on silent:** captions still show, and a plate above the caption says "Turn up your volume to hear Bolo."
- **No internet:** the welcome is bundled with the app, so it always plays; setup questions and the Day 1 clip also work offline (US-1.9).
''')

F['ScreenConsent'] = (page('ScreenConsent', 'Choose what Bolo keeps, before it listens', phone(status(), f'''<div class="bo-flow">
    <div style="height:var(--space-8)"></div>
    <div class="bo-hero-icon bo-hero-icon--coach" style="align-self:flex-start">{icon('shield_person')}</div>
    <h1 class="bo-flow__title" style="margin-top:var(--space-5)">Bolo listens only while you practise.</h1>
    <p class="bo-flow__body">Keep your recordings so you can hear your progress later?</p>
    <p class="bo-flow__bn">রেকর্ডিং রেখে দেবেন, যাতে পরে নিজের উন্নতি শুনতে পারেন?</p>
    <div style="flex:1"></div>
    <label class="bo-check bo-check--on"><span class="bo-check__box">{icon('check', cls='bo-icon--sm')}</span>I'm 13 or older</label>
    <div class="bo-flow__actions" style="margin-top:var(--space-3);gap:var(--space-2)">
      <button class="bo-btn bo-btn--secondary bo-btn--block">Yes, keep them</button>
      <button class="bo-btn bo-btn--secondary bo-btn--block">No, just give feedback</button>
      <button class="bo-btn bo-btn--quiet-neutral">I'm under 13</button>
    </div>
  </div>''')), '''# ScreenConsent

ConsentChoice as a full screen (ONB-7, US-1.1a). It comes straight after the welcome and before the microphone is ever opened.

- **Two equal answers.** "Yes, keep them" and "No, just give feedback" are the same secondary button, so neither is nudged. There is no mustard button on this screen on purpose.
- Both stay disabled until "I'm 13 or older" is ticked. **I'm under 13** shows "Bolo is for ages 13 and up" and stops onboarding.
- **What each answer does:** Yes keeps clips (90 days by default) for Day 1 vs today. No uses audio only to give feedback and deletes it within 24 hours; the Day 1 clip then stays on the phone only. The choice can be changed in Me → Privacy.
- Most learners should finish this step in under 10 seconds. Then the microphone step follows at once.
''')

F['ScreenMicPermission'] = (page('ScreenMicPermission', 'Why Bolo needs the mic, before Android asks', phone(status(), f'''<div class="bo-flow" style="align-items:center;text-align:center">
    <div style="flex:1"></div>
    <div class="bo-hero-icon">{icon('mic')}</div>
    <h1 class="bo-flow__title" style="margin-top:var(--space-6)">Bolo needs your mic to hear you</h1>
    <p class="bo-flow__body">It listens only while you practise, never in the background.</p>
    <p class="bo-flow__bn">শুধু অনুশীলনের সময় শোনে, পেছনে কখনও না।</p>
    <div style="flex:1.4"></div>
    <div class="bo-flow__actions" style="width:100%"><button class="bo-btn bo-btn--primary bo-btn--block">{icon('mic', True)}Allow microphone</button></div>
  </div>''')), '''# ScreenMicPermission

The one-line reason shown before Android's own microphone prompt (ONB-2, US-1.2). One message, one button.

- **Allow microphone** opens the system prompt. If allowed, onboarding moves on by itself.
- **Denied once:** the same screen returns with **Try again**, which shows the system prompt again (US-1.3).
- **"Don't ask again":** **Try again** opens Bolo's page in Android settings. When the learner comes back with the mic on, the app notices and continues with no extra tap.
- The icon is a plain mic on `sun-soft`; the logo's face never appears on app screens.
''')

F['ScreenSetupQuestion'] = (page('ScreenSetupQuestion', 'Three questions, answered by voice or a tap', phone(status(), f'''<div class="bo-flow">
    <div class="bo-steps"><button class="bo-back" aria-label="Back">{icon('arrow_back')}</button>{dots(3, 1)}<div class="bo-steps__end"></div></div>
    <div class="bo-eyebrow" style="margin-top:var(--space-4)"><span class="bo-buddy-mark">B</span>Bolo asks</div>
    <h1 class="bo-flow__title" style="margin-top:var(--space-2)">What do you need English for most?</h1>
    <p class="bo-flow__bn" style="margin-top:0">ইংরেজি আপনার সবচেয়ে বেশি কোথায় দরকার?</p>
    <p class="bo-footnote" style="text-align:left;margin:var(--space-5) 0 var(--space-3)">You said "for my office meetings". Or tap one.</p>
    <div class="bo-chips">
      <button class="bo-chip" aria-pressed="true">{icon('check', cls='bo-icon--sm')}Work meetings</button>
      <button class="bo-chip" aria-pressed="false">Job interviews</button>
      <button class="bo-chip" aria-pressed="false">Study abroad</button>
      <button class="bo-chip" aria-pressed="false">Daily confidence</button>
    </div>
    <div style="flex:1"></div>
    <div style="display:flex;flex-direction:column;align-items:center;gap:var(--space-5)">
      <div class="bo-voiceonly" style="border-radius:var(--radius-full);padding:4px">{micring('turn', 'Listening', '289')}</div>
      <button class="bo-btn bo-btn--primary bo-btn--block">Next</button>
    </div>
  </div>''')), '''# ScreenSetupQuestion

The three setup questions (ONB-3, US-1.4), one per screen with ProgressDots. Bolo asks each one out loud; the learner answers by voice, and AnswerChips are always there as the fallback.

1. **What do you need English for most?** Work meetings · Job interviews · Study abroad · Daily confidence.
2. **Which moment feels hardest?** Speaking up in meetings · Interviews · Phone calls · Small talk.
3. **How long do you want to talk each day?** 5 minutes · 10 minutes.

- The mic opens automatically after the question (the sun ring). A matched answer is selected and echoed in the footnote ("You said 'for my office meetings'"). If nothing matches, the footnote reads "Or tap one" and nothing is selected.
- **Next** confirms. **Back** returns to the previous question without losing answers. All three are editable later in Me → My goals.
- The Buddy mark (rose "B") says this is Bolo talking; the Coach never appears in onboarding.
''')

lv = [6, 10, 18, 28, 40, 52, 60, 66, 58, 46, 34, 26, 18, 12, 8]
level = ''.join(f'<i class="{"on" if 3 <= i <= 11 else ""}" style="height:{h}px"></i>' for i, h in enumerate(lv))
F['ScreenMicCheck'] = (page('ScreenMicCheck', '5 seconds to check the voice and the room', phone(status(), f'''<div class="bo-flow">
    <div class="bo-steps"><button class="bo-back" aria-label="Back">{icon('arrow_back')}</button><span class="subhead" style="color:var(--ink-secondary)">Mic check</span><div class="bo-steps__end"></div></div>
    <h1 class="bo-flow__title" style="margin-top:var(--space-4)">Read this out loud</h1>
    <p class="bo-flow__body">So Bolo can check it hears you clearly.</p>
    <p class="bo-flow__bn">জোরে পড়ুন, যাতে বলো আপনাকে পরিষ্কার শুনতে পায়।</p>
    <div class="bo-card" style="margin-top:var(--space-8);padding:var(--space-6) var(--space-5)">
      <p class="bo-readout">"I'm ready to practise speaking today."</p>
    </div>
    <div style="flex:1"></div>
    <div class="bo-level" aria-label="Voice level: good">{level}</div>
    <p class="bo-footnote" style="margin:var(--space-3) 0 var(--space-6)">Listening… 3 s</p>
  </div>''')), '''# ScreenMicCheck

A 5-second check of voice level and background noise (ONB-5, US-1.5). The learner reads one short line; mustard bars follow their voice.

- **Good:** a `nailed` check and "Sounds great" for 1 second, then the Day 1 step.
- **Noisy:** "It's a bit noisy. Earbuds or a quieter spot will help." **Check again** is primary, **Continue anyway** secondary. Continuing means later feedback hides low-confidence issues and says so.
- **Too quiet:** "Speak a little louder or hold the phone closer." with **Check again**.
- None of these are errors, so none of them use red, a warning icon or the word "error".
''')

F['ScreenDayOne'] = (page('ScreenDayOne', 'The Day 1 clip: 30 seconds to hear again on Day 7', phone(status(), f'''<div class="bo-flow">
    <div class="bo-steps"><button class="bo-back" aria-label="Back">{icon('arrow_back')}</button><span class="subhead" style="color:var(--ink-secondary)">Your Day 1 clip</span><div class="bo-steps__end"></div></div>
    <h1 class="bo-flow__title" style="margin-top:var(--space-4)">Introduce yourself, like at a new job</h1>
    <p class="bo-flow__body">We'll play this back to you on Day 7.</p>
    <ul class="bo-prompts" style="margin-top:var(--space-5)">
      <li>{icon('person', cls='bo-icon--sm')}Your name and where you're from</li>
      <li>{icon('work', cls='bo-icon--sm')}What you do</li>
      <li>{icon('favorite', cls='bo-icon--sm')}One thing you enjoy</li>
    </ul>
    <div style="flex:1"></div>
    <div class="bo-rec"><svg class="arc" viewBox="0 0 168 168" aria-hidden="true"><circle class="track" cx="84" cy="84" r="80"/><circle class="left" cx="84" cy="84" r="80" stroke-dasharray="301.6 502.7"/></svg>
      <button class="bo-rec__btn" aria-label="Stop recording, 18 seconds left">0:18<small>{icon('stop', True, 'bo-icon--sm')}Stop</small></button></div>
    <p class="bo-footnote" style="margin:var(--space-4) 0 var(--space-2)">Stops by itself at 30 seconds.</p>
  </div>''')), '''# ScreenDayOne

Recording the Day 1 clip (ONB-4, US-1.6): a 30-second self-introduction that comes back on Day 7, 14 and 21 as VoiceCompare.

- Three gentle prompts help people who freeze. The recorder is a mustard disc with a `sun-ink` ring that runs down from 30 s; tapping it stops early.
- **Under 10 seconds:** once only, "Want to say a bit more?" with **Record again** (primary) and **Keep it**.
- **Re-record** up to 3 times; the new clip replaces the old.
- **Saved:** "Saved. We'll play this back to you on Day 7." for 1.5 s, then the first conversation starts in the scene. As a guest the clip stays on the phone, and it is uploaded only after sign-in and only if the learner chose to keep recordings.
- **Interrupted** (a call, the app closed): the partial clip is discarded and this step restarts (US-1.7).
''')

# ================= PROGRESS =================
def trend(name, level, path, ey):
    return (f'<div class="bo-trend__cell"><div class="bo-trend__name">{name}</div><div class="bo-trend__level">{level}</div>'
            f'<svg viewBox="0 0 140 44" preserveAspectRatio="none" aria-hidden="true"><rect class="band" x="0" y="14.7" width="140" height="14.7" rx="4"/>'
            f'<path class="line" d="{path}"/><circle class="end" cx="140.0" cy="{ey}" r="3.5"/></svg></div>')
TR = (trend('Pronunciation', 'Good', 'M0.0 31.6 L23.3 30.0 L46.7 28.4 L70.0 24.6 L93.3 25.2 L116.7 20.4 L140.0 18.8', 18.8)
      + trend('Fluency', 'Strong', 'M0.0 28.4 L23.3 26.8 L46.7 22.0 L70.0 20.4 L93.3 15.6 L116.7 14.0 L140.0 10.8', 10.8)
      + trend('Sentences', 'Building', 'M0.0 33.2 L23.3 31.6 L46.7 32.2 L70.0 30.0 L93.3 29.0 L116.7 29.7 L140.0 28.4', 28.4)
      + trend('Vocabulary', 'Good', 'M0.0 26.8 L23.3 25.2 L46.7 25.8 L70.0 23.6 L93.3 22.0 L116.7 22.6 L140.0 20.4', 20.4))
cmp = (f'<div class="bo-compare"><div class="bo-compare__side"><div class="bo-compare__day"><small>Your first intro</small>Day 1</div>{clip(1, time="0:30")}</div>'
       f'<div class="bo-compare__side bo-compare__side--now"><div class="bo-compare__day"><small>Today</small>Day 21</div>{clip(2, time="0:30")}</div></div>')
F['ScreenProgress'] = (page('ScreenProgress', 'Your voice first, then skills, patterns and history', phone(status(), f'''<div style="flex:1;min-height:0;overflow:hidden;padding:0 var(--space-5)">
    <h1 class="large-title" style="margin:var(--space-5) 0 var(--space-1)">Progress</h1>
    <p class="subhead" style="color:var(--ink-secondary);margin:0">21 days · 14 talks · 70 minutes of speaking</p>
    <div class="bo-section">Your voice, then and now</div>
    {cmp}
    <p class="headline" style="margin:var(--space-3) 0 0">Fewer pauses, clearer v sound.</p>
    <div class="bo-section">Skills <a href="#">Last 30 days</a></div>
    <div class="bo-trend">{TR}</div>
    <div class="bo-section">My patterns <a href="#">See all</a></div>
  </div>''', tabbar('Progress'))), '''# ScreenProgress

The Progress tab once there is enough to show (PRG-1 to PRG-3, PRG-8). It scrolls; order matters:

1. **Large title** and one line of effort ("21 days · 14 talks · 70 minutes of speaking"). Effort first, because it always grows.
2. **Your voice, then and now:** VoiceCompare with the one-line summary. Between unlocks it shows the Day 1 clip and "Day 28 unlocks in 5 days".
3. **Skills:** SkillTrends, 2×2, last 30 days, in words (Building / Good / Strong), never numbers.
4. **My patterns:** PatternList, up to 5, each tagged Improving / Steady / New; tapping one starts a 2-minute drill. Patterns not seen for 5 sessions move to "Fixed" with a small `spring-joy` the first time.
5. **History:** the three latest SessionRows and **See all** (ScreenHistory).

The streak (P1) lives on Home, not here. Nothing on this tab ever shows a score or a percentage.
''')

F['ScreenProgressNew'] = (page('ScreenProgressNew', 'The first days: honest about what is coming', phone(status(), f'''<div style="flex:1;min-height:0;overflow:hidden;padding:0 var(--space-5)">
    <h1 class="large-title" style="margin:var(--space-5) 0 var(--space-1)">Progress</h1>
    <p class="subhead" style="color:var(--ink-secondary);margin:0">Day 3 · 1 talk · 5 minutes of speaking</p>
    <div class="bo-section">Your voice, then and now</div>
    <div class="bo-card"><div class="bo-lockcard"><div class="bo-compare__day" style="width:auto"><small>Your first intro</small>Day 1</div><div style="flex:1;min-width:0">{clip(1, time="0:30")}</div></div>
      <div class="bo-lockcard" style="margin-top:var(--space-4)"><span class="bo-row__icon">{icon('lock_clock', cls='bo-icon--sm')}</span><div><div class="bo-row__title" style="font-weight:650">Day 7 unlocks in 4 days</div><div class="bo-row__sub">Then you'll record your intro again and hear both.</div></div></div></div>
    <div class="bo-section">Skills</div>
    <div class="bo-card" style="text-align:center;padding:var(--space-6) var(--space-5)">
      <div class="bo-hero-icon bo-hero-icon--coach" style="width:56px;height:56px;margin:0 auto var(--space-3)">{icon('insights')}</div>
      <p class="headline" style="margin:0">Your trends appear after 3 talks</p>
      <div class="bo-dots" style="margin-top:var(--space-3)" aria-label="1 of 3"><i class="on" style="width:8px"></i><i></i><i></i></div>
      <p class="bo-row__sub" style="margin-top:var(--space-2)">1 of 3</p>
    </div>
  </div>''', tabbar('Progress'))), '''# ScreenProgressNew

The Progress tab in the first days (US-6.1, US-6.2). It never looks empty or broken; it says honestly what is coming and when.

- **Voice:** the Day 1 clip is playable from the start, with "Day 7 unlocks in 4 days" under it. The free week ends the same day, which makes Day 7 the natural moment for the first comparison.
- **Skills:** a StatePanel-style card, "Your trends appear after 3 talks", with a three-dot count.
- **Patterns** appear after 3 talks too; until then the section is hidden, not empty.
- **History** shows the first talk as soon as it exists.
''')

# ================= ME =================
F['ScreenMe'] = (page('ScreenMe', 'Goals, plan, invites, settings, privacy: two levels at most', phone(status(), f'''<div style="flex:1;min-height:0;overflow:hidden;padding:0 var(--space-5)">
    <h1 class="large-title" style="margin:var(--space-5) 0 var(--space-5)">Me</h1>
    <div class="bo-group__title" style="padding-left:0">My goals</div>
    <div class="bo-group">{row('Work meetings', 'What I need English for')}{row('Speaking up in meetings', 'The hardest moment')}{row('10 minutes', 'Talk time each day')}</div>
    <div style="height:var(--space-6)"></div>
    <div class="bo-group">{row('Your plan', '1 of 2 free talks left this month', ic='workspace_premium')}{row('Invite friends', '2 of 3 joined · a free month of Basic', ic='group_add')}</div>
    <div style="height:var(--space-6)"></div>
    <div class="bo-group">{row('Settings', ic='tune')}{row('Privacy', 'Recordings kept for 90 days', ic='shield_person')}{row('Help', ic='help')}</div>
  </div>''', tabbar('Me'))), '''# ScreenMe

The Me tab (US-9.5, ACC-*). Nothing is nested more than two levels: Me → a page.

- **My goals:** the three setup answers, each editable (and the work context for Standup Rehearsal, P1).
- **Your plan** opens ScreenMePlan; its sub line is the free counter or the paid plan. **Invite friends** (first year only) opens ScreenInvite.
- **Settings**, **Privacy** and **Help**. **About Bolo** and **Sign out** sit at the bottom of the scroll, quiet.
- Guests see **Save your progress** as the first row, opening ScreenSaveProgress.
- No avatar, no profile photo, no level badge: Me is a place to adjust things, not a scoreboard.
''')

F['ScreenSettings'] = (page('ScreenSettings', 'Two groups only: Speaking, Display & data', phone(status(), f'''<div style="flex:1;min-height:0;overflow:hidden;padding:0 var(--space-5)">
    <div class="bo-flow__top"><button class="bo-back">{icon('arrow_back')}Me</button></div>
    <h1 class="large-title" style="margin:var(--space-2) 0 var(--space-5)">Settings</h1>
    <div class="bo-group__title" style="padding-left:0">Speaking</div>
    <div class="bo-group">{row("Bolo's speed", value='Normal')}{row('Captions', toggle=True)}{row('More time to answer', 'Prompts at 16, 30 and 90 seconds', toggle=False)}{row('I stammer', 'Repeats and blocks never count against you', toggle=False)}{row('Ambient sound', toggle=True)}</div>
    <div style="height:var(--space-6)"></div>
    <div class="bo-group__title" style="padding-left:0">Display &amp; data</div>
    <div class="bo-group">{row('Scene and companion', value='Hilltop')}{row('Voice-only mode', toggle=False)}{row('Theme', value='System')}</div>
  </div>''')), '''# ScreenSettings

Me → Settings (SET-1 to SET-4, US-9.2, US-9.4, US-9.5), built from SettingsRow. Exactly two groups.

- **Speaking:** Bolo's speed (Slower / Normal), Captions, More time to answer, I stammer, Ambient sound.
- **Display & data:** Scene and companion (opens ScreenScenePicker), Voice-only mode, Theme (System / Day / Night, P1), Data saver (P1), Bangla helper text (on by default, ONB-6).
- Every change applies at once; there is no Save button. Text size always follows the phone's font setting, so there is no text-size control here.
''')

F['ScreenPrivacy'] = (page('ScreenPrivacy', 'What Bolo keeps, why, and how to delete it', phone(status(), f'''<div style="flex:1;min-height:0;overflow:hidden;padding:0 var(--space-5) var(--space-6);display:flex;flex-direction:column">
    <div class="bo-flow__top"><button class="bo-back">{icon('arrow_back')}Me</button></div>
    <h1 class="large-title" style="margin:var(--space-2) 0 var(--space-5)">Privacy</h1>
    <div class="bo-card"><p class="headline" style="margin:0 0 var(--space-3)">What Bolo keeps</p>
      <div class="bo-keeps"><div class="bo-keeps__row"><span>Your voice clips</span><span>90 days</span></div><div class="bo-keeps__row"><span>Words and feedback</span><span>While you have an account</span></div><div class="bo-keeps__row"><span>Used to train other AI</span><span>Never</span></div></div>
      <p class="bo-row__sub" style="margin:var(--space-4) 0 0;font-size:14px;line-height:20px">Bolo keeps these only to give you feedback and show your progress.</p></div>
    <div style="height:var(--space-5)"></div>
    <div class="bo-group">{row('Keep my recordings', 'Needed for Day 1 vs today', toggle=True)}{row('Delete my recordings', 'Feedback and history stay')}</div>
    <div style="flex:1"></div>
    <button class="bo-btn bo-btn--critical" style="align-self:center">Delete everything</button>
  </div>''')), '''# ScreenPrivacy

Me → Privacy (ACC-3, ACC-4, US-8.5, US-8.6). Plain words first, controls second.

- **What Bolo keeps:** voice clips (90 days by default), words and feedback (while the account exists), and never used to train other AI (NFR-15). One line says why.
- **Keep my recordings** is the consent choice from onboarding, changeable any time; the change applies from the next talk. Turning it off explains that Day 1 vs today will stop.
- **Delete my recordings** removes audio only; feedback and history stay.
- **Delete everything** is the only `critical` control in the app. It opens a Sheet where the learner types DELETE; if a paid plan is active, the sheet first says how billing stops. Everything is gone within 7 days, with a confirmation by SMS or email.
''')

# ================= PRACTICE =================
home_sc = PHONE_HOME
sheet_sc = f'''<div class="bo-sheet-scrim"></div>
  <div class="bo-sheet" role="dialog" aria-modal="true" aria-labelledby="sc-t">
    <div class="bo-sheet__grabber"></div>
    <h2 class="bo-sheet__title" id="sc-t">What do you want to practise?</h2>
    <div class="bo-plans" role="radiogroup" aria-label="Scenario" style="margin-top:var(--space-4)">
      <button class="bo-plan" role="radio" aria-checked="false"><span class="bo-radio"></span><span class="bo-plan__body"><span class="bo-plan__name">Free talk</span><span class="bo-plan__meta">Chat about your day, your work, anything.</span></span></button>
      <div class="bo-plan" role="radio" aria-checked="true" style="flex-wrap:wrap;align-items:flex-start"><span class="bo-radio" style="margin-top:2px"></span><span class="bo-plan__body"><span class="bo-plan__name">Standup rehearsal</span><span class="bo-plan__meta">Bolo plays your manager and asks for your update.</span></span>
        <div class="bo-chips" style="width:100%;padding-left:34px;margin-top:var(--space-2)"><button class="bo-chip" aria-pressed="false" style="background:var(--surface)">Friendly</button><button class="bo-chip" aria-pressed="true" style="background:var(--surface)">{icon('check', cls='bo-icon--sm')}Realistic</button></div></div>
    </div>
    <p class="bo-footnote" style="text-align:left;margin-top:var(--space-3)">Realistic interrupts once or twice, like a real meeting.</p>
  </div>'''
F['ScreenScenarioPicker'] = (page('ScreenScenarioPicker', 'Free talk or a standup rehearsal, from "Change"', phone(home_sc.strip(), sheet_sc)), '''# ScreenScenarioPicker

The sheet behind **Change** under the Talk button (SCN-1, SCN-2, US-7.1, US-7.3).

- **Free talk** (default) and **Standup rehearsal**, as radio cards with one line each.
- Choosing Standup rehearsal reveals the difficulty inside its card: **Friendly** never interrupts; **Realistic** interrupts once or twice and speaks at normal speed.
- **One tap applies and the sheet closes**; for Standup, after the difficulty is picked. The Talk button's line then reads "Standup rehearsal · Realistic · Change" and remembers it.
- The first Standup asks once for the learner's role and team by voice (P1); it's editable in Me → My goals.
''')

def drillbar(n_on, title):
    return (f'<div class="bo-drillbar"><button class="bo-btn bo-btn--quiet-neutral" aria-label="Close drills">{icon("close")}</button>'
            f'{dots(3, n_on)}<button class="bo-btn bo-btn--quiet-neutral">Skip</button></div>'
            f'<div class="bo-eyebrow bo-eyebrow--coach" style="margin-top:var(--space-2)"><span class="bo-coach-mark">C</span>{title}</div>')
F['ScreenDrillPairs'] = (page('ScreenDrillPairs', 'Drill 1: hear the difference, then say it', phone(status(), f'''<div class="bo-flow">
    {drillbar(1, 'The v sound · Hear the difference')}
    <h1 class="bo-flow__title" style="text-align:center;margin-top:var(--space-8)">Which did you hear?</h1>
    <div style="height:var(--space-6)"></div>
    <button class="bo-hear" aria-label="Hear it again">{icon('volume_up', cls='bo-icon--lg')}</button>
    <div style="height:var(--space-8)"></div>
    <div class="bo-pairs"><button class="bo-pair bo-pair--right" aria-label="very, that's it">very<small>{icon('check_circle', True, 'bo-icon--sm')} That's it</small></button><button class="bo-pair bo-pair--other">berry<small>&nbsp;</small></button></div>
    <div style="flex:1"></div>
    <div class="bo-dots" style="align-self:center;margin-bottom:var(--space-2)" aria-label="Listening round 1 of 3"><i class="on"></i><i></i><i></i></div>
    <p class="bo-footnote">Listening round 1 of 3, then you say 5 words</p>
  </div>''')), '''# ScreenDrillPairs

A minimal-pairs drill (DRL-2, US-5.2), the first of up to 3 drills made from today's fixes. Drills are the Coach's, so the header carries the violet Coach mark.

- **Header (every drill):** close, three dots for the drill count, and **Skip**. Skipped drills come back next time; a drill skipped 3 times retires (DRL-5).
- **Listen rounds (3):** tap the big violet button to hear one word of the pair, then pick it. The right card turns `nailed` with a check; a miss plays both words again, slowly. No red, no buzz.
- **Say rounds (5):** one word in big type, the model clip, and **Try it**; feedback is the ResultPill after each.
- **Three tries per item**, then "Good effort. We'll come back to this one." and it moves on by itself (DRL-6).
''')

F['ScreenDrillShadow'] = (page('ScreenDrillShadow', 'Drill 2: say it right after the model', phone(status(), f'''<div class="bo-flow">
    {drillbar(2, 'Shadowing · Say it with me')}
    <p class="bo-fix__sentence" style="font-size:24px;line-height:32px;font-weight:600;margin-top:var(--space-6)">"It's <span class="bo-better">very</span> important to test it early."</p>
    <div style="height:var(--space-6)"></div>
    <p class="bo-fix__label">Model</p>{clip(5, model=True, time='0:03', on=26)}
    <div style="height:var(--space-4)"></div>
    <p class="bo-fix__label">You</p>{clip(6, time='0:03')}
    <div style="flex:1"></div>
    <div class="bo-fix__actions"><span class="bo-result bo-result--closer" role="status" style="margin-bottom:8px">{icon('north_east')}Closer</span>
      <button class="bo-btn bo-btn--primary bo-btn--block">{icon('mic', True)}Try again</button>
      <button class="bo-btn bo-btn--quiet-neutral">Next drill</button></div>
  </div>''')), '''# ScreenDrillShadow

Shadowing (DRL-3, US-5.3): hear the model sentence, then the mic opens by itself and the learner repeats it straight away.

- After the attempt, the model and the learner's clip play back to back (model in violet, the learner in neutral `clip-you`), with one ResultPill: Nailed it, Closer or Once more.
- The slow-motion tool on the model clip plays it at 0.75×.
- **Try again** is primary until Nailed it or 3 tries; **Next drill** is always available.
''')

F['ScreenDrillSayBetter'] = (page('ScreenDrillSayBetter', 'Drill 3: your own sentence, from reading to memory', phone(status(), f'''<div class="bo-flow">
    {drillbar(3, 'Say it better · Round 2 of 3')}
    <p class="bo-fix__label" style="margin-top:var(--space-6)">You said</p>
    <p class="bo-fix__sentence bo-fix__sentence--before" style="margin:0">"Yesterday I fix bug, it was in payment."</p>
    <div style="height:var(--space-5)"></div>
    <p class="bo-fix__label">Now say</p>
    <p class="bo-fix__sentence" style="margin:0;font-size:22px;line-height:34px;font-weight:600">"Yesterday I <span class="bo-blank"></span> bug in the payment service <span style="white-space:nowrap"><span class="bo-blank" style="min-width:120px"></span>."</span></p>
    <p class="bo-footnote" style="text-align:left;margin-top:var(--space-3)">Key words are hidden. Round 3 hides the whole sentence.</p>
    <div style="flex:1"></div>
    <div style="display:flex;flex-direction:column;align-items:center;gap:var(--space-3)">
      <div class="bo-voiceonly" style="border-radius:var(--radius-full);padding:4px">{micring('turn', 'Listening', '289')}</div>
      <p class="bo-footnote">Listening… say the whole sentence</p>
    </div>
  </div>''')), '''# ScreenDrillSayBetter

Say it better (DRL-4, US-5.4): the learner's own upgraded sentence, said three times with less help each time.

- **Round 1:** the full upgraded sentence, read aloud. **Round 2 (shown):** key words become violet blanks. **Round 3:** the whole sentence is hidden and the learner says it from memory.
- The original sentence stays greyed above, so the learner sees how far they've come.
- Saying the same meaning counts as a pass; it doesn't have to match word for word.
''')

F['ScreenDrillDone'] = (page('ScreenDrillDone', 'What moved today, and one button', phone(status(), f'''<div class="bo-state" style="flex:1;justify-content:center;gap:12px;padding:0 var(--space-5)">
    <p class="display" style="margin:0">Nice work.</p>
    <p class="body" style="margin:0;color:var(--ink-secondary)">What moved today</p>
    <div class="bo-card" style="width:100%;text-align:left;display:flex;flex-direction:column;gap:14px;margin-top:var(--space-3)">
      <div style="display:flex;justify-content:space-between;align-items:center"><span class="headline">v sound</span><span style="display:flex;gap:6px;align-items:center"><span class="bo-result bo-result--closer" style="height:32px;font-size:14px">Closer</span>{icon('arrow_forward', cls='bo-icon--sm')}<span class="bo-result bo-result--nailed" style="height:32px;font-size:14px">Nailed it</span></span></div>
      <div style="display:flex;justify-content:space-between;align-items:center"><span class="headline">Shadowing</span><span class="bo-result bo-result--closer" style="height:32px;font-size:14px">{icon('north_east', cls='bo-icon--sm')}Closer</span></div>
      <div style="display:flex;justify-content:space-between;align-items:center"><span class="headline">Upgraded sentence</span><span class="bo-result bo-result--nailed" style="height:32px;font-size:14px;white-space:nowrap">{icon('check', cls='bo-icon--sm')}From memory</span></div>
    </div>
  </div>
  <div style="padding:0 var(--space-5) var(--space-8)"><button class="bo-btn bo-btn--primary bo-btn--block">Done</button></div>''')), '''# ScreenDrillDone

DrillSummary as the end of the drill flow (DRL-7, US-5.7): what moved today, and a single **Done**.

- Each drill gets one line with its before → after result. Only movement and effort are listed; nothing that "failed".
- **Done** returns to Home. The loop already counted when the feedback was heard, so skipping or stopping drills never costs a streak.
- At the end of the first loop, Done leads to ScreenSaveProgress instead (PRG-7).
''')

for name, (html, readme) in F.items():
    d = f'{C}/{name}'; os.makedirs(d, exist_ok=True)
    open(f'{d}/preview.html', 'w').write(html); open(f'{d}/README.md', 'w').write(readme)
print('ok', len(F), list(F))
