import os, re, sys
S = sys.argv[1]
C = S + '/ds/project/components'
home = open(S + '/ds/home.html').read()
SCENE_SVG = re.search(r'(<svg class="bo-scene__art".*?</svg>)', home, re.S).group(1)
PHONE_INNER = re.search(r'<div class="bo-phone">(.*)</div></div>\s*</body>', home, re.S).group(1)

def icon(n, fill=False, cls=''):
    return f'<span class="bo-icon{" bo-icon--fill" if fill else ""}{(" " + cls) if cls else ""}" aria-hidden="true">{n}</span>'

def status(t='9:52'):
    return f'<div class="bo-statusbar"><span>{t}</span><span>{icon("signal_cellular_alt", cls="bo-icon--sm")} {icon("battery_5_bar", cls="bo-icon--sm")}</span></div>'

def tabbar(sel):
    out = []
    for name, ic in (('Talk', 'graphic_eq'), ('Progress', 'insights'), ('Me', 'person')):
        out.append(f'<button class="bo-tab" role="tab" aria-selected="{"true" if name == sel else "false"}"><span class="bo-tab__pill">{icon(ic)}</span>{name}</button>')
    return '<nav class="bo-tabbar" role="tablist">' + ''.join(out) + '</nav>'

GOOGLE_G = ('<svg viewBox="0 0 48 48" aria-hidden="true"><path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>'
            '<path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>'
            '<path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>'
            '<path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/></svg>')

def google():
    return f'<button class="bo-provider">{GOOGLE_G}Continue with Google</button>'
def phone_btn():
    return f'<button class="bo-btn bo-btn--secondary bo-btn--block">{icon("smartphone")}Use phone number</button>'

def keep(rows):
    r = ''.join(f'<div class="bo-keep__row"><span class="bo-keep__icon{(" bo-keep__icon--" + v) if v else ""}">{icon(i, True)}</span><div><div class="bo-keep__title">{t}</div><div class="bo-keep__sub">{s}</div></div></div>' for i, v, t, s in rows)
    return f'<div class="bo-keep">{r}</div>'

def keypad():
    keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', icon('backspace')]
    return '<div class="bo-keypad" aria-hidden="true">' + ''.join(f'<span{" class=\"fn\"" if k in ("", icon("backspace")) else ""}>{k}</span>' for k in keys) + '</div>'

def page(title, card, body, stage=True):
    inner = f'<div class="bo-stage bo-stage--center" style="padding:24px 0">\n{body}\n</div>' if stage else body
    return f'''{card}
<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><title>{title} — Bolo</title></head>
<body>
{inner}
</body>
</html>
'''

def phone(*parts):
    return '<div class="bo-phone">\n' + '\n'.join(parts) + '\n</div>'

def screen_card(sub):
    return f'<!-- @dsCard group="Screens" height=820 subtitle="{sub}" page -->'

def scene_card(caption, h=200, extra=''):
    return f'''<div class="bo-scene" style="border-radius:var(--radius-lg);height:{h}px;flex:none;display:flex;align-items:flex-end;padding:var(--space-4){extra}">
      {SCENE_SVG}
      <div class="bo-caption" style="position:relative;padding:12px 16px"><p class="bo-caption__text" style="font-size:17px;line-height:24px">{caption}</p></div>
    </div>'''

KEEP_FIRST = [('mic', '', 'Your Day 1 clip', "0:30 · we'll play it back on Day 7"),
              ('forum', 'coach', "Today's feedback", '1 strength and 3 things to try'),
              ('flag', 'buddy', 'Your goals', 'Work meetings, job interviews')]

def scene_strip(h=132):
    return f'<div class="bo-scene" style="border-radius:var(--radius-lg);height:{h}px;flex:none;margin-top:var(--space-3)" aria-hidden="true">{SCENE_SVG}</div>'

def save_flow():
    return f'''<div class="bo-flow">
    {scene_strip()}
    <h1 class="bo-flow__title">Save your progress</h1>
    <p class="bo-flow__body">Keep your Day 1 voice and today's feedback, even on a new phone.</p>
    <p class="bo-flow__bn">আপনার অগ্রগতি রেখে দিন, নতুন ফোনেও।</p>
    <div style="height:var(--space-6)"></div>
    {keep(KEEP_FIRST)}
    <div style="flex:1"></div>
    <div class="bo-flow__actions">
      {google()}
      {phone_btn()}
      <button class="bo-btn bo-btn--quiet-neutral">Not now</button>
    </div>
  </div>'''

files = {}

# 1. Save progress
files['ScreenSaveProgress'] = (page('ScreenSaveProgress', screen_card('End of first loop, step 2: keep your Day 1 voice'), phone(status(), save_flow())),
'''# ScreenSaveProgress

Step 2 of the end of the first loop (PRG-7, US-8.1), right after the drills offer. It asks for an account by showing what the learner would lose, not by asking for an account.

- **Top:** the `title-1` "Save your progress", one body line and the Bangla helper.
- **Middle:** a KeepList with exactly three things worth keeping: the Day 1 clip (sun), today's feedback (coach) and the learner's goals (buddy).
- **Bottom third:** SignInButtons: **Continue with Google**, **Use phone number**, and a quiet **Not now**.

This is the one screen without a mustard button: Google's sign-in rules fix the look of its button, so the Google button leads by position instead. Nothing else on the screen competes with it.

The 13+ confirmation from onboarding carries over silently; never ask it again here. Tapping **Not now** opens ScreenGuestNotNow. Tapping **Continue with Google** opens Android's own account sheet (Credential Manager) over this screen.
''')

# 2. Not now sheet
files['ScreenGuestNotNow'] = (page('ScreenGuestNotNow', screen_card('An honest warning before staying a guest'), phone(status(), save_flow(),
'''<div class="bo-sheet-scrim"></div>
  <div class="bo-sheet" role="dialog" aria-modal="true" aria-labelledby="gn-t">
    <div class="bo-sheet__grabber"></div>
    <h2 class="bo-sheet__title" id="gn-t">Stay a guest for now?</h2>
    <p class="bo-sheet__body" style="margin-bottom:0">Your progress stays on this phone only. If you uninstall Bolo, it goes too.</p>
    <p class="bo-flow__bn" style="margin:0 0 var(--space-4)">অ্যাপ মুছে ফেললে আপনার অগ্রগতিও মুছে যাবে।</p>
    <button class="bo-btn bo-btn--primary bo-btn--block">Save my progress</button>
    <button class="bo-btn bo-btn--secondary bo-btn--block">Stay a guest</button>
  </div>''')),
'''# ScreenGuestNotNow

The Sheet that follows **Not now** on ScreenSaveProgress (US-8.2). It states the one real cost of staying a guest, in plain words, once.

- **Save my progress** is the primary action because it is the safe choice for the learner's data; **Stay a guest** is a full-size secondary button, never a hidden link. Choosing to stay a guest must take one tap.
- Dragging the sheet down or tapping the scrim returns to the save screen.
- After **Stay a guest**, go straight to ScreenReminder. The save prompt comes back once after the 2nd and 3rd sessions, then ScreenGuestLimit gates the 4th.
''')

# 3. Phone number
files['ScreenPhoneNumber'] = (page('ScreenPhoneNumber', screen_card('One field, +880 already filled'), phone(status(), f'''<div class="bo-flow" style="padding-bottom:var(--space-4)">
    <div class="bo-flow__top"><button class="bo-back" aria-label="Back">{icon('arrow_back')}</button></div>
    <h1 class="bo-flow__title" style="margin-top:var(--space-2)">Your phone number</h1>
    <p class="bo-flow__body">We'll text you a 6-digit code.</p>
    <p class="bo-flow__bn">আমরা আপনাকে ৬ সংখ্যার একটি কোড পাঠাব।</p>
    <div style="height:var(--space-6)"></div>
    <p class="bo-field__label">Mobile number</p>
    <div class="bo-field bo-field--focus"><span class="bo-field__prefix">+880</span><span>1712 345 678</span><span class="bo-field__caret"></span></div>
    <div style="flex:1"></div>
    <div class="bo-flow__actions">
      <p class="bo-footnote">We use your number only to sign you in.</p>
      <button class="bo-btn bo-btn--primary bo-btn--block">Send code</button>
    </div>
  </div>''', keypad())),
'''# ScreenPhoneNumber

The phone route of SignInButtons (US-8.1). It is one field on one screen.

- The country code **+880** is fixed and shown as a prefix, so the learner types only the 10 digits they know. Group the digits as they type (1712 345 678); accept a leading 0 and strip it.
- The numeric keyboard opens on arrival. **Send code** sits directly above it and is enabled once 10 digits are in.
- One reassurance line sits above the button: the number is used only for signing in.
- If the number is not a Bangladeshi mobile number, say so under the field in `ink`: "That doesn't look like a mobile number. Check the digits after +880." Never use red.
''')

# 4. Code
files['ScreenPhoneCode'] = (page('ScreenPhoneCode', screen_card('Auto-read where Android allows it'), phone(status(), f'''<div class="bo-flow" style="padding-bottom:var(--space-4)">
    <div class="bo-flow__top"><button class="bo-back" aria-label="Back">{icon('arrow_back')}</button></div>
    <h1 class="bo-flow__title" style="margin-top:var(--space-2)">Enter the code</h1>
    <p class="bo-flow__body">Sent to +880 1712 345 678. <a class="bo-link" href="#">Change</a></p>
    <div style="height:var(--space-6)"></div>
    <div class="bo-code" role="group" aria-label="6-digit code"><span class="bo-code__cell bo-code__cell--filled">4</span><span class="bo-code__cell bo-code__cell--filled">8</span><span class="bo-code__cell bo-code__cell--filled">1</span><span class="bo-code__cell bo-code__cell--next"></span><span class="bo-code__cell"></span><span class="bo-code__cell"></span></div>
    <p class="bo-code__note">{icon('sms', cls='bo-icon--sm')}Reading the code from your messages…</p>
    <div style="flex:1"></div>
    <div class="bo-flow__actions">
      <button class="bo-btn bo-btn--quiet-neutral" disabled style="background:transparent">Resend code in 0:18</button>
    </div>
  </div>''', keypad())),
'''# ScreenPhoneCode

CodeInput on its own screen (US-8.1, US-8.3). There is no submit button: the code checks itself when the sixth digit lands.

- Use Android's SMS Retriever / User Consent API so the code fills itself where possible; the note "Reading the code from your messages…" shows while it waits. The learner can always type over it.
- **Resend code** is disabled for 30 s with a live countdown, then becomes a quiet `sun-ink` link. After 2 resends, add a secondary **Try Google instead**.
- **Code didn't match:** clear the cells, keep the keyboard up, and show "That code didn't match. Try again." in `ink` under the cells. No red, no shake.
- **After 5 tries:** replace the cells with "Let's pause for 15 minutes" and a countdown, plus **Try Google instead**.
- On success, go straight on: ScreenRestore if the account already has data, otherwise the next step of the flow.
''')

# 5. Guest limit (sheet over Home)
files['ScreenGuestLimit'] = (page('ScreenGuestLimit', screen_card('The 4th talk asks for an account, kindly'), phone(PHONE_INNER.strip(),
f'''<div class="bo-sheet-scrim"></div>
  <div class="bo-sheet" role="dialog" aria-modal="true" aria-labelledby="gl-t">
    <div class="bo-sheet__grabber"></div>
    <h2 class="bo-sheet__title" id="gl-t">Save your progress to keep talking</h2>
    <p class="bo-sheet__body" style="margin-bottom:var(--space-3)">You've had 3 talks as a guest. Sign in and everything so far comes with you.</p>
    {keep([('history', 'coach', '3 talks and their feedback', 'Scored and saved'), ('mic', '', 'Your Day 1 clip', 'Kept, as you chose at the start')])}
    <div style="height:var(--space-3)"></div>
    {google()}
    {phone_btn()}
    <button class="bo-btn bo-btn--quiet-neutral" style="align-self:center">Not now</button>
  </div>''')),
'''# ScreenGuestLimit

What happens when a guest taps **Talk** for a 4th time (ACC-2, US-8.2). It is a Sheet over Home, not a new screen, so the learner sees they are one step from talking.

- The title says what they get ("…to keep talking"), and the body promises that nothing is lost.
- A two-row KeepList shows what is waiting. Its second row follows the consent choice made at the start: "Kept, as you chose at the start" or, if they chose not to keep recordings, "Your talks were scored; recordings weren't kept".
- SignInButtons follow. **Not now** closes the sheet and leaves Home as it was; the Talk button asks again next time.
''')

# 6. Restore
files['ScreenRestore'] = (page('ScreenRestore', screen_card('New phone: everything is already here'), phone(status(), f'''<div class="bo-flow">
    <div style="height:var(--space-3)"></div>
    {scene_card('Welcome back, Rafi. Good to see you.', 196)}
    <h1 class="bo-flow__title" style="margin-top:var(--space-6)">Your progress is here</h1>
    <p class="bo-flow__body">We brought everything over from your last phone.</p>
    <div style="height:var(--space-5)"></div>
    {keep([('history', 'coach', '12 talks', 'With all their feedback and patterns'), ('mic', '', 'Your Day 1 clip', 'Recorded 3 October'), ('flag', 'buddy', 'Your goals', 'Work meetings, job interviews')])}
    <div style="flex:1"></div>
    <button class="bo-btn bo-btn--primary bo-btn--block">Go to Home</button>
  </div>''')),
'''# ScreenRestore

Shown once after signing in on a new phone (ACC-7, US-8.4), reached from "I already have an account" on the welcome screen. Onboarding is skipped.

- The Buddy greets the learner by name on the scene card, warmly and without mentioning how long they were away (US-6.6).
- A KeepList confirms what came back: talks with their feedback, the Day 1 clip and goals. Show real counts and dates.
- One action: **Go to Home**.
- If the learner chose not to keep recordings, the Day 1 row reads "Not kept, as you chose" and uses `surface-sunken` for its icon disc.
- While data downloads (on 3G it can take a few seconds), show the same screen with the KeepList rows arriving one by one, and the button disabled with "Bringing your talks over…". No spinner.
''')

# 7. Reminder
files['ScreenReminder'] = (page('ScreenReminder', screen_card('End of first loop, step 3: one reminder, the learner\'s time'), phone(status(), f'''<div class="bo-flow">
    <h1 class="bo-flow__title" style="margin-top:var(--space-3)">When should Bolo check in?</h1>
    <p class="bo-flow__body">One reminder a day, only if you haven't talked yet.</p>
    <p class="bo-flow__bn">দিনে একবার, শুধু যদি সেদিন কথা না বলেন।</p>
    <div style="height:var(--space-5)"></div>
    <div class="bo-notif" aria-label="Example reminder"><span class="bo-notif__app">B</span><div><div class="bo-notif__meta">Bolo · 6:30 pm</div><p class="bo-notif__text">Bolo wants to know how the demo went.</p></div></div>
    <div style="height:var(--space-5)"></div>
    <div class="bo-slots" role="radiogroup" aria-label="Reminder time">
      <button class="bo-slot" role="radio" aria-checked="false">{icon('wb_twilight')}<span class="bo-slot__name">Morning commute</span><span class="bo-slot__time">8:00 am</span></button>
      <button class="bo-slot" role="radio" aria-checked="false">{icon('lunch_dining')}<span class="bo-slot__name">Lunch</span><span class="bo-slot__time">1:30 pm</span></button>
      <button class="bo-slot" role="radio" aria-checked="true">{icon('directions_bus', True)}<span class="bo-slot__name">Evening commute</span><span class="bo-slot__time">6:30 pm</span></button>
      <button class="bo-slot" role="radio" aria-checked="false">{icon('bedtime')}<span class="bo-slot__name">Night</span><span class="bo-slot__time">10:00 pm</span></button>
    </div>
    <div class="bo-exact">{icon('schedule')}Pick an exact time</div>
    <div style="flex:1"></div>
    <div class="bo-flow__actions" style="gap:var(--space-2)">
      <p class="bo-footnote">Next, Android asks to allow notifications.</p>
      <button class="bo-btn bo-btn--primary bo-btn--block">Remind me at 6:30 pm</button>
      <button class="bo-btn bo-btn--quiet-neutral">Not now</button>
    </div>
  </div>''')),
'''# ScreenReminder

Step 3 of the end of the first loop (PRG-5, PRG-7, US-6.5). It asks one question and asks for the notification permission on the same step, after the learner has chosen.

- **Top:** the PRD's own question, "When should Bolo check in?", with the promise that makes it safe to say yes: one a day, and none if they already talked.
- **The NotificationPreview** shows a real Buddy-story reminder at the chosen time, so the learner knows exactly what will arrive. It updates as they change the slot.
- **ReminderSlots:** Morning commute, Lunch, Evening commute and Night, with a sensible default time each. Preselect the slot nearest the time of this first session. **Pick an exact time** opens Android's time picker and replaces the slot times.
- **Bottom third:** one line warning that Android will ask next, then **Remind me at 6:30 pm** (the label follows the choice) and a quiet **Not now**.
- **If notifications are denied:** save the time anyway, land on Home with "Come back around 6:30 pm" under the Talk button, and don't ask again for 14 days.
''')

# 8. See you tomorrow (Home)
sy = PHONE_INNER.replace('Did the payment fix go live? Tell me.', 'Nice talking with you, Rafi. See you tomorrow.')
sy = sy.replace('9:41', '9:58').replace('wb_sunny</span>6</span>', 'wb_sunny</span>1</span>')
sy = re.sub(r'<div class="bo-homecard" role="button">.*?</div></div>\s*<span class="bo-icon bo-chev"[^>]*>chevron_right</span></div>',
            f'<div class="bo-homecard" role="button"><span class="bo-homecard__icon bo-homecard__icon--sun">{icon("notifications_active", True)}</span><div class="bo-homecard__body"><div class="bo-homecard__title">Reminder at 6:30 pm</div><div class="bo-homecard__sub">Evening commute, from tomorrow</div></div><span class="bo-icon bo-chev" aria-hidden="true">chevron_right</span></div>', sy, flags=re.S)
assert 'Reminder at 6:30 pm' in sy and 'See you tomorrow' in sy
files['ScreenSeeYouTomorrow'] = (page('ScreenSeeYouTomorrow', screen_card('Home after the first loop: a goodbye, nothing else'), phone(sy.strip())),
'''# ScreenSeeYouTomorrow

Where the first loop ends (US-6.8): Home, with the Buddy saying goodbye on the scene card. No rating request, no plan offer, no tips.

- The scene caption reads "Nice talking with you, Rafi. See you tomorrow." The streak shows its first sun.
- The one HomeCard confirms the reminder ("Reminder at 6:30 pm"); tapping it opens the time choice again. From the next visit on, Home's normal priority table takes over.
- The Talk button stays. Some learners will want another go straight away, and nothing should stop them.
- If notifications were denied, the card reads "Come back around 6:30 pm" with no bell icon. If the learner skipped the reminder, there is no card at all.
- If the app was closed mid-flow, only the missed steps show next time, once, before this screen (US-6.8).
''')

# 9. History
def session(day, mon, topic, meta, fixes, extra=''):
    tags = ''.join(f'<span class="bo-fixtag">{f}</span>' for f in fixes)
    return f'''<div class="bo-session" role="button"><div class="bo-session__date"><div class="bo-session__day">{day}</div><div class="bo-session__mon">{mon}</div></div><div class="bo-session__body"><div class="bo-session__topic">{topic}</div><div class="bo-session__meta">{meta}</div><div class="bo-session__fixes">{extra}{tags}</div></div></div>'''

files['ScreenHistory'] = (page('ScreenHistory', screen_card('Every talk, newest first, with its fixes'), phone(status('10:04'), f'''<div style="flex:1;min-height:0;overflow:hidden;padding:0 var(--space-5)">
    <div class="bo-flow__top" style="margin:0 calc(var(--space-3) * -1)"><button class="bo-back">{icon('arrow_back')}Progress</button></div>
    <h1 class="large-title" style="margin:var(--space-2) 0 0">History</h1>
    <div class="bo-week-label">This week</div>
    <div class="bo-group">
      {session('6', 'Nov', 'The payment bug demo', 'Free talk · 5 min', ['The v sound', '“actually” × 11', 'Say it better'])}
      {session('5', 'Nov', 'Update for your manager', 'Standup rehearsal · 6 min', ['The th sound', 'Shorter answers', 'Past tense'])}
      {session('3', 'Nov', 'Weekend in Sylhet', 'Free talk · 4 min', ['The w sound', 'Linking words'], '<span class="bo-tag bo-tag--new" style="height:26px">In your drills</span>')}
    </div>
    <div class="bo-week-label">Last week</div>
    <div class="bo-group">
      {session('30', 'Oct', 'Why you chose backend', 'Free talk · 5 min', ['The z sound', '“basically” × 6', 'Say it better'])}
    </div>
  </div>''', tabbar('Progress'))),
'''# ScreenHistory

Progress → History (PRG-8, US-6.7): every past talk, newest first, grouped by week.

- Each SessionRow shows the date, a topic the Coach names from the talk ("The payment bug demo"), the scenario and length, and its fixes as violet tags, because fixes belong to the Coach.
- A talk whose feedback wasn't finished carries a sun-soft **In your drills** tag first: its fixes moved to drills, and the row still opens the full feedback.
- Tapping a row opens ScreenHistorySession.
- **Empty state:** History can only be empty if the learner deleted everything, because the first talk always lands here. Then use a StatePanel: "Your talks will appear here", one Bangla line, and **Talk** as the action.
- **Free plan (public launch):** rows older than 7 days show a lock in place of their fix tags and "See all with Plus" in `sun-ink` at the end of the list (US-8.7). Locked rows still show the date and topic, so nothing feels deleted.
''')

# 10. Session detail
def pastfix(title, sub, res=''):
    return f'<div class="bo-pastfix"><button class="bo-clip__play" aria-label="Play {title}">{icon("play_arrow", True)}</button><div class="bo-pastfix__body"><div class="bo-pastfix__title">{title}</div><div class="bo-pastfix__sub">{sub}</div></div>{res}</div>'
wave = ''.join(f'<i style="height:{h}px"></i>' for h in [6,10,16,12,20,14,8,18,24,16,10,22,26,14,8,12,20,16,10,6,14,18,8,12,20,10,6,4])
files['ScreenHistorySession'] = (page('ScreenHistorySession', screen_card('A past talk: listen, review, practise again'), phone(status('10:05'), f'''<div class="bo-flow" style="padding-bottom:var(--space-4)">
    <div class="bo-flow__top"><button class="bo-back">{icon('arrow_back')}History</button></div>
    <div class="bo-eyebrow" style="margin-top:var(--space-2)">Thursday 6 November · Free talk</div>
    <h1 class="bo-flow__title" style="margin:var(--space-1) 0 var(--space-4)">The payment bug demo</h1>
    <div class="bo-clip"><button class="bo-clip__play" aria-label="Play the whole talk">{icon('play_arrow', True)}</button><div class="bo-clip__wave" aria-hidden="true">{wave}</div><span class="bo-clip__time">5:12</span></div>
    <div class="bo-card" style="margin-top:var(--space-5);background:var(--hills-soft);box-shadow:none;padding:var(--space-4) var(--space-5)">
      <div class="bo-eyebrow bo-eyebrow--coach"><span class="bo-coach-mark">C</span>Your strength</div>
      <p style="font:500 17px/24px var(--font-sans);margin:var(--space-2) 0 0">You explained the bug for 40 seconds without stopping.</p>
    </div>
    <div class="bo-group__title" style="padding:var(--space-5) 0 var(--space-1)">3 things to try</div>
    <div>
      {pastfix('The v sound', '“bhery” → “very”', '<span class="bo-tag bo-tag--improving">' + icon('check_circle', True, 'bo-icon--sm') + 'Nailed it</span>')}
      {pastfix('“actually” × 11', 'Try a short pause instead')}
      {pastfix('Say it better', '“I am working here since 2 years” → “I’ve worked here for 2 years”')}
    </div>
    <div style="flex:1"></div>
    <div class="bo-flow__actions" style="gap:var(--space-1)">
      <button class="bo-btn bo-btn--primary bo-btn--block">{icon('mic', True)}Practise these (3 min)</button>
      <button class="bo-btn bo-btn--quiet-neutral">Delete recording</button>
    </div>
  </div>''')),
'''# ScreenHistorySession

One past talk, opened from ScreenHistory (US-6.7). It keeps the debrief's order: your voice, your strength, then up to three things to try.

- **The whole talk** plays from an AudioClip in `clip-you`. If the learner chose not to keep recordings, the clip is replaced by one line: "Recording not kept, as you chose. Feedback and words are still here."
- **The strength** sits on `hills-soft` with the Coach mark, exactly as the Coach said it.
- **Past fixes** are compact rows; the play disc plays the model clip (violet). A fix nailed later in drills carries **Nailed it**; others carry nothing, never a "not yet".
- **Practise these (3 min)** builds a drill set from this talk's fixes. It is the one primary action.
- **Delete recording** (US-8.6) opens a Sheet: "Delete this recording?" with **Keep it** as the primary and "Delete recording" as the secondary. The audio disappears at once; the feedback stays.
''')

# ---- component previews ----
comp = {}
comp['SignInButtons'] = (f'''<!-- @dsCard group="Actions" height=300 subtitle="Google's own button, phone as secondary, Not now quiet" -->
<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><title>SignInButtons — Bolo</title></head>
<body>
<div class="bo-stage bo-stage--col" style="gap:12px">
  {google()}
  {phone_btn()}
  <button class="bo-btn bo-btn--quiet-neutral" style="align-self:center">Not now</button>
</div>
</body>
</html>
''', '''# SignInButtons

The two ways to save progress (ACC-1): **Continue with Google** and **Use phone number**, with a quiet **Not now** under them when skipping is allowed.

**Needs:** nothing but the context; the labels are fixed.

**Use**
- The Google button follows Google's sign-in branding: the full-colour G, the label "Continue with Google", and Google's own fill, outline and label colours (`provider-fill`, `provider-line`, `provider-ink`, light by Day and dark by Night). The capsule shape is allowed by Google's rules and matches Bolo's buttons. Never recolour it mustard.
- Because of that, a screen with SignInButtons has no `sun` button. Google leads by position, phone follows as a `secondary` button, and Not now is `quiet-neutral`.
- On tap, Google opens Android's Credential Manager sheet; phone goes to ScreenPhoneNumber.

**Behaviour:** press scales to 0.97 like every button. While Google's sheet is open, the screen stays still behind it.
''')
comp['CodeInput'] = (f'''<!-- @dsCard group="System" height=200 subtitle="Six cells, checks itself on the last digit" -->
<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><title>CodeInput — Bolo</title></head>
<body>
<div class="bo-stage bo-stage--col">
  <div class="bo-code" role="group" aria-label="6-digit code"><span class="bo-code__cell bo-code__cell--filled">4</span><span class="bo-code__cell bo-code__cell--filled">8</span><span class="bo-code__cell bo-code__cell--filled">1</span><span class="bo-code__cell bo-code__cell--next"></span><span class="bo-code__cell"></span><span class="bo-code__cell"></span></div>
  <p class="bo-code__note">{icon('sms', cls='bo-icon--sm')}Reading the code from your messages…</p>
</div>
</body>
</html>
''', '''# CodeInput

Six cells for the SMS code (US-8.1, US-8.3).

**Needs:** the digits so far, and an optional note line under the cells.

**Use**
- Build it as one numeric text field (number keyboard, one-time-code autofill) drawn as six cells, so paste, autofill and TalkBack all work as one field. TalkBack reads it as "6-digit code, 3 entered".
- Filled cells get an `ink` outline, the next cell the `focus-ring` outline and caret, empty cells `line-strong`.
- The note line carries status in `ink-secondary`: auto-reading, "That code didn't match. Try again.", or the 15-minute pause. Never red, never a shake.

**Behaviour:** each digit drops in with a 120 ms fade. On the sixth digit, check at once; on a miss, clear all cells together and put the caret back in the first.
''')
comp['KeepList'] = (f'''<!-- @dsCard group="Status" height=280 subtitle="What saving keeps, in three rows at most" -->
<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><title>KeepList — Bolo</title></head>
<body>
<div class="bo-stage bo-stage--col">
  {keep(KEEP_FIRST)}
</div>
</body>
</html>
''', '''# KeepList

A short card that names what the learner keeps (or got back). It turns "create an account" into "keep your Day 1 voice".

**Needs:** two or three rows, each with an icon, a title and a sub line.

**Use**
- On ScreenSaveProgress, ScreenGuestLimit and ScreenRestore. Never more than three rows.
- Colour each icon disc by owner: the learner's voice is `sun`, feedback and history are `coach` (hills), goals and the Buddy's story are `buddy` (rose).
- Sub lines are concrete: lengths, counts, dates. No marketing words.

**Behaviour:** static. On ScreenRestore the rows arrive one by one (120 ms apart, `spring-standard`) as data lands.
''')
comp['ReminderSlots'] = (f'''<!-- @dsCard group="Progress" height=300 subtitle="Four times of day and an exact-time fallback" -->
<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><title>ReminderSlots — Bolo</title></head>
<body>
<div class="bo-stage bo-stage--col">
  <div class="bo-slots" role="radiogroup" aria-label="Reminder time">
    <button class="bo-slot" role="radio" aria-checked="false">{icon('wb_twilight')}<span class="bo-slot__name">Morning commute</span><span class="bo-slot__time">8:00 am</span></button>
    <button class="bo-slot" role="radio" aria-checked="false">{icon('lunch_dining')}<span class="bo-slot__name">Lunch</span><span class="bo-slot__time">1:30 pm</span></button>
    <button class="bo-slot" role="radio" aria-checked="true">{icon('directions_bus', True)}<span class="bo-slot__name">Evening commute</span><span class="bo-slot__time">6:30 pm</span></button>
    <button class="bo-slot" role="radio" aria-checked="false">{icon('bedtime')}<span class="bo-slot__name">Night</span><span class="bo-slot__time">10:00 pm</span></button>
  </div>
  <div class="bo-exact">{icon('schedule')}Pick an exact time</div>
</div>
</body>
</html>
''', '''# ReminderSlots

The reminder-time choice (PRG-5, US-6.5): four slots named after the learner's day, in a 2×2 grid, plus **Pick an exact time**.

**Needs:** the four slots with their default times, and which one is selected.

**Use**
- Slots are named by routine, not by clock: Morning commute (8:00 am), Lunch (1:30 pm), Evening commute (6:30 pm), Night (10:00 pm). Commutes come first because the bus is where Bolo is used (persona Rafi).
- It is a radio group: exactly one is selected. Selected is `sun-soft` with a `sun-ink` border and filled icon, like a pressed AnswerChip.
- **Pick an exact time** opens Android's time picker; the chosen time then replaces the selected slot's time.
- The same component appears in Me → Settings → Reminder, with a toggle above it to turn reminders off.

**Behaviour:** selection springs the border in (`spring-toggle`) with a light haptic tick.
''')
comp['NotificationPreview'] = (f'''<!-- @dsCard group="Progress" height=160 subtitle="Show the reminder before asking for permission" -->
<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><title>NotificationPreview — Bolo</title></head>
<body>
<div class="bo-stage bo-stage--col">
  <div class="bo-notif" aria-label="Example reminder"><span class="bo-notif__app">B</span><div><div class="bo-notif__meta">Bolo · 6:30 pm</div><p class="bo-notif__text">Bolo wants to know how the demo went.</p></div></div>
</div>
</body>
</html>
''', '''# NotificationPreview

A drawn example of the daily reminder, shown on ScreenReminder before Android asks for notification permission. Seeing a friendly, story-like message makes "Allow" an easy choice.

**Needs:** the time and one Buddy-story line drawn from the learner's own last talk.

**Use**
- It is an illustration, not a real notification: draw it as a `surface` card, with the app disc in `sun` until a logo exists.
- The line follows the notification rules in *Voice and copy*: it continues the Buddy's story, never names English or a course, and has no emoji.
- The time updates live as the learner changes the slot.

**Behaviour:** when the time changes, the meta line cross-fades (160 ms). Nothing else moves.
''')
comp['SessionRow'] = (f'''<!-- @dsCard group="Progress" height=300 subtitle="Date, topic, length and its fixes" -->
<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><title>SessionRow — Bolo</title></head>
<body>
<div class="bo-stage bo-stage--col">
  <div class="bo-group">
    {session('6', 'Nov', 'The payment bug demo', 'Free talk · 5 min', ['The v sound', '“actually” × 11', 'Say it better'])}
    {session('3', 'Nov', 'Weekend in Sylhet', 'Free talk · 4 min', ['The w sound', 'Linking words'], '<span class="bo-tag bo-tag--new" style="height:26px">In your drills</span>')}
  </div>
</div>
</body>
</html>
''', '''# SessionRow

One past talk in History (PRG-8).

**Needs:** the date, a short topic, the scenario and length, up to three fix names, and whether the feedback was finished.

**Use**
- There is no chevron: the whole row is the target, and dropping it gives the fix tags the width to sit on one or two lines.
- Rows sit in a `bo-group` card, grouped under week labels ("This week", "Last week", then month names).
- The date is a big day number over a short month, so the list scans like a diary.
- Fix tags are `hills-soft` with `hills-ink`, using the fix card's own title ("The v sound", "Say it better"). They wrap to a second line rather than truncate.
- An unfinished feedback adds a `bo-tag--new` **In your drills** before the fix tags.
- TalkBack reads the row as one sentence: "6 November, The payment bug demo, free talk, 5 minutes, 3 things to try."

**Behaviour:** the whole row is the tap target; press dims the row to `surface-sunken` for 120 ms.
''')

for name, (html, readme) in {**files, **comp}.items():
    d = f'{C}/{name}'
    os.makedirs(d, exist_ok=True)
    open(f'{d}/preview.html', 'w').write(html)
    open(f'{d}/README.md', 'w').write(readme)
print('wrote', len(files) + len(comp))
