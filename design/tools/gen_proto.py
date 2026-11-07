import os, re, sys, json
S = sys.argv[1]; P = S + '/ds/project'; C = P + '/components'; OUT = S + '/proto/index.html'
F = json.load(open(S + '/fontdl/final.json'))
tokens = open(P + '/tokens.css').read(); bundle = open(P + '/components/bundle.css').read()
imp = re.search(r"@import url\('([^']+)'\);", bundle).group(1); bundle = bundle.replace(f"@import url('{imp}');", '')
def read(p): return open(p).read()
def stage_inner(html):
    m = re.search(r'<body>\s*<div class="bo-stage[^"]*"[^>]*>(.*)</div>\s*</body>', html, re.S)
    return m.group(1).strip()
def head_styles(html): return ''.join(re.findall(r'<style>(.*?)</style>', html, re.S))
def body_inner(html): return re.search(r'<body>(.*)</body>', html, re.S).group(1).strip()
def comp(name): return read(f'{C}/{name}/preview.html')
EXTRA_CSS = []
def screen_from(html):
    EXTRA_CSS.append(head_styles(html)); return stage_inner(html)
def icon(n, fill=False, cls=''):
    return f'<span class="bo-icon{" bo-icon--fill" if fill else ""}{(" " + cls) if cls else ""}" aria-hidden="true">{n}</span>'
SCR = {}
def add(id_, phone_html):
    h = phone_html.strip()
    assert h.startswith('<div class="bo-phone'), (id_, h[:60])
    # unique ids per screen (gradients in hidden screens must not be shared)
    ids = set(re.findall(r'\bid="([^"]+)"', h))
    for i in ids:
        h = h.replace(f'id="{i}"', f'id="{i}--{id_}"').replace(f'url(#{i})', f'url(#{i}--{id_})').replace(f'aria-labelledby="{i}"', f'aria-labelledby="{i}--{id_}"')
    h = h.replace('<div class="bo-phone', f'<div data-screen="{id_}" class="pscreen bo-phone', 1)
    SCR[id_] = h
def with_overlay(phone_html, overlay):
    h = phone_html.strip(); assert h.endswith('</div>'); return h[:-6] + overlay + '\n</div>'
def sheet_overlay(inner):
    return (f'<div class="bo-sheet-scrim" data-scrim style="z-index:5"></div><div class="bo-sheet" role="dialog" aria-modal="true" style="z-index:6;color:var(--ink)">'
            f'<div class="bo-sheet__grabber"></div>{inner}</div>')
status = '<div class="bo-statusbar"><span>9:41</span><span>' + icon('signal_cellular_alt', cls='bo-icon--sm') + ' ' + icon('battery_5_bar', cls='bo-icon--sm') + '</span></div>'

# ---------- splash ----------
icon_svg = F['full'].replace('<svg xmlns="http://www.w3.org/2000/svg"', '<svg class="splash-icon"')
wm = F['wm_night'].replace('<svg xmlns="http://www.w3.org/2000/svg"', '<svg class="splash-wm"')
add('splash', f'<div class="bo-phone" style="background:#1D1236;align-items:center;justify-content:center">{icon_svg}<div style="position:absolute;bottom:56px;left:0;right:0;display:flex;justify-content:center">{wm}</div></div>')

# ---------- onboarding ----------
add('welcome', screen_from(comp('ScreenWelcome')).replace('<div class="bo-phone bo-scene">', '<div class="bo-phone bo-scene convo-welcome">'))
add('restore', screen_from(comp('ScreenRestore')))
add('consent', screen_from(comp('ScreenConsent')))
add('mic', screen_from(comp('ScreenMicPermission')))
q1 = screen_from(comp('ScreenSetupQuestion')); add('q1', q1)
def chips(items, sel=None):
    return '<div class="bo-chips">' + ''.join(f'<button class="bo-chip" aria-pressed="{"true" if i == sel else "false"}">{icon("check", cls="bo-icon--sm") if i == sel else ""}{t}</button>' for i, t in enumerate(items)) + '</div>'
def q_variant(n, title, bn, items):
    h = q1
    h = h.replace('What do you need English for most?', title).replace('ইংরেজি আপনার সবচেয়ে বেশি কোথায় দরকার?', bn)
    h = h.replace('You said "for my office meetings". Or tap one.', 'Say it, or tap one.')
    h = re.sub(r'<div class="bo-chips">.*?</div>', chips(items), h, count=1, flags=re.S)
    h = h.replace('aria-label="Step 1 of 3"><i class="on"></i><i></i><i></i>', f'aria-label="Step {n} of 3">' + ''.join(f'<i{" class=\"on\"" if i + 1 == n else ""}></i>' for i in range(3)))
    return h
add('q2', q_variant(2, 'Which moment feels hardest?', 'কোন মুহূর্তটা সবচেয়ে কঠিন লাগে?', ['Speaking up in meetings', 'Interviews', 'Phone calls', 'Small talk']))
add('q3', q_variant(3, 'How long do you want to talk each day?', 'প্রতিদিন কতক্ষণ কথা বলতে চান?', ['5 minutes', '10 minutes']))
add('miccheck', screen_from(comp('ScreenMicCheck')))
add('day1', screen_from(comp('ScreenDayOne')))

# ---------- conversation ----------
conv = open(S + '/ds/conv.html').read(); conv_phone = stage_inner(conv)
talk = conv_phone.replace('<div class="bo-phone bo-scene">', '<div class="bo-phone bo-scene convo">')
add('talk', talk)
drive = screen_from(comp('ScreenConversationDrive')).replace('<div class="bo-phone bo-scene">', '<div class="bo-phone bo-scene convo">')
add('talkdrive', drive)
sheet_inner = re.search(r'<div class="bo-sheet__grabber"></div>(.*?)</div>\s*</div></div>', read(S + '/ds/extra/sheet.html'), re.S).group(1)
add('endsheet', with_overlay(conv_phone, sheet_overlay(sheet_inner)))
sp = screen_from(comp('ScreenScenePicker')).replace('<div class="bo-sheet-scrim" style="z-index:5">', '<div class="bo-sheet-scrim" data-scrim style="z-index:5">')
add('scenepick', sp)

# ---------- feedback ----------
deb = open(S + '/ds/debrief.html').read(); deb_phone = stage_inner(deb)
head = re.search(r'(<div class="bo-statusbar">.*?</div>\s*<div style="display:flex;align-items:center;justify-content:space-between;padding:var\(--space-2\).*?</div>)', deb_phone, re.S).group(1)
def deb_shell(inner, page):
    d = '<div class="bo-dots" aria-label="Card ' + str(page) + ' of 5" style="align-self:center;margin:var(--space-2) 0">' + ''.join(f'<i{" class=\"on\"" if i + 1 == page else ""}></i>' for i in range(5)) + '</div>'
    return f'<div class="bo-phone debrief">{head}<div style="flex:1;display:flex;flex-direction:column;padding:0 var(--space-4) var(--space-6);overflow:hidden">{d}{inner}</div></div>'
EXTRA_CSS.append('')
add('scoring', f'<div class="bo-phone">{status}<div style="flex:1;display:flex;align-items:center">{body_inner(read(S + "/ds/extra/scoring.html"))}</div></div>')
strength = re.search(r'(<div class="bo-card".*</div>)\s*</div>\s*$', body_inner(read(S + '/ds/extra/strength.html')), re.S).group(1)
add('strength', deb_shell(f'<div style="flex:1;display:flex;flex-direction:column;justify-content:center">{strength}</div><p class="bo-footnote">Swipe for 3 things to try</p>', 1))
fix1 = deb_phone.replace('<div class="bo-phone">', '<div class="bo-phone debrief">', 1)
fix1 = re.sub(r'<span class="bo-result bo-result--closer" role="status" style="margin-bottom:8px">.*?Closer</span>', '<span class="bo-result bo-result--closer" role="status" style="margin-bottom:8px;visibility:hidden"><span class="bo-icon " aria-hidden="true">north_east</span>Closer</span>', fix1, flags=re.S)
fix1 = fix1.replace('Try again</button>', 'Try it</button>')
add('fix1', fix1)
actions = (f'<div style="flex:1"></div><div class="bo-fix__actions"><span class="bo-result bo-result--nailed" role="status" style="margin-bottom:8px;visibility:hidden">{icon("check_circle", True)}Nailed it</span>'
           f'<button class="bo-btn bo-btn--primary bo-btn--block">{icon("mic", True)}Try it</button><button class="bo-btn bo-btn--quiet-neutral">That\'s not what I said</button></div>')
sent = re.search(r'(<article class="bo-fix">.*</article>)', read(S + '/ds/extra/sentence.html'), re.S).group(1).replace('</article>', actions + '</article>').replace('<article class="bo-fix">', '<article class="bo-fix" style="flex:1">')
add('fix2', deb_shell(sent.replace('<span class="bo-eyebrow bo-eyebrow--coach">Say it better</span>', '<div class="bo-fix__head"><div class="bo-dots" aria-label="2 of 3"><i></i><i class="on"></i><i></i></div><span class="bo-eyebrow bo-eyebrow--coach">Say it better</span></div>'), 3))
hab = re.search(r'(<article class="bo-fix">.*</article>)', read(S + '/ds/extra/habit.html'), re.S).group(1)
hab = hab.replace('<article class="bo-fix">', '<article class="bo-fix" style="flex:1">').replace('<div class="bo-fix__head"><span class="bo-eyebrow bo-eyebrow--coach">Filler watch</span>', '<div class="bo-fix__head"><div class="bo-dots" aria-label="3 of 3"><i></i><i></i><i class="on"></i></div><span class="bo-eyebrow bo-eyebrow--coach">Filler watch</span>')
hab = hab.replace('<span class="bo-delta"><span class="bo-icon bo-icon--sm" aria-hidden="true">south</span>11 → 4</span>', '').replace('"actually" × 4', '"actually" × 11')
hab = hab.replace('</article>', f'<div style="flex:1"></div><div class="bo-fix__actions"><button class="bo-btn bo-btn--quiet-neutral">That\'s not what I said</button></div></article>')
add('fix3', deb_shell(hab, 4))
skills = re.search(r'(<div class="bo-card".*</div>)\s*</div>\s*$', body_inner(read(S + '/ds/extra/skills.html')), re.S).group(1)
skills = re.sub(r' <span class="(up|same)"><span class="bo-icon bo-icon--sm" aria-hidden="true">[a-z_]+</span></span>', '', skills)
add('skills', deb_shell(f'''<p class="title-2" style="margin:var(--space-2) 0 var(--space-1)">This is your starting point</p><p class="subhead" style="color:var(--ink-secondary);margin:0 0 var(--space-4)">Arrows appear after your next talk.</p>{skills}<div style="flex:1"></div>
  <div class="bo-flow__actions"><button class="bo-btn bo-btn--primary bo-btn--block">{icon('timer', True)}Practise (3 min)</button><button class="bo-btn bo-btn--quiet-neutral">Later</button></div>''', 5))

# ---------- drills, end of loop, tabs, plans ----------
for sid, name in [('drill1', 'ScreenDrillPairs'), ('drill2', 'ScreenDrillShadow'), ('drill3', 'ScreenDrillSayBetter'), ('drilldone', 'ScreenDrillDone'),
                  ('save', 'ScreenSaveProgress'), ('notnow', 'ScreenGuestNotNow'), ('phone', 'ScreenPhoneNumber'), ('code', 'ScreenPhoneCode'),
                  ('reminder', 'ScreenReminder'), ('home', 'ScreenSeeYouTomorrow'), ('scenario', 'ScreenScenarioPicker'),
                  ('progress', 'ScreenProgressNew'), ('progress21', 'ScreenProgress'), ('history', 'ScreenHistory'), ('hsession', 'ScreenHistorySession'),
                  ('me', 'ScreenMe'), ('settings', 'ScreenSettings'), ('privacy', 'ScreenPrivacy'), ('freelimit', 'ScreenFreeLimit'),
                  ('plans', 'ScreenPlans'), ('pay', 'ScreenPlanPay'), ('meplan', 'ScreenMePlan'), ('cancel', 'ScreenCancelPlan'),
                  ('invite', 'ScreenInvite'), ('guestlimit', 'ScreenGuestLimit')]:
    h = screen_from(comp(name)).replace('<div class="bo-sheet-scrim"', '<div class="bo-sheet-scrim" data-scrim', 1)
    if 'z-index:6' not in h: h = h.replace('<div class="bo-sheet" role="dialog"', '<div class="bo-sheet" role="dialog" style="z-index:6;color:var(--ink)"', 1)
    if 'data-scrim style' not in h: h = h.replace('data-scrim>', 'data-scrim style="z-index:5">').replace('data-scrim></div>', 'data-scrim style="z-index:5"></div>')
    add(sid, h)
# progress21 needs a history entry point
SCR['progress21'] = SCR['progress21'].replace('<div class="bo-section">My patterns <a href="#">See all</a></div>', '<div class="bo-section">History <a href="#" data-go="history">See all</a></div>')

screens_html = '\n'.join(SCR.values())
GROUPS = [('First launch', [('splash', 'Splash'), ('welcome', 'Welcome'), ('consent', 'Consent'), ('mic', 'Microphone'), ('q1', 'Setup questions'), ('miccheck', 'Mic check'), ('day1', 'Day 1 clip')]),
          ('Talk', [('talk', 'Conversation'), ('scenepick', 'Scene picker'), ('talkdrive', 'Evening drive'), ('endsheet', 'End and get feedback?')]),
          ('Feedback', [('scoring', 'Coach listening back'), ('strength', 'Strength'), ('fix1', 'Sound fix'), ('fix2', 'Say it better'), ('fix3', 'Filler watch'), ('skills', 'Starting point')]),
          ('Drills', [('drill1', 'Hear the difference'), ('drill2', 'Shadowing'), ('drill3', 'Say it better'), ('drilldone', 'Nice work')]),
          ('End of first loop', [('save', 'Save progress'), ('notnow', 'Stay a guest?'), ('phone', 'Phone number'), ('code', 'Code'), ('reminder', 'Reminder'), ('home', 'Home: see you tomorrow')]),
          ('Tabs', [('scenario', 'Choose what to practise'), ('progress', 'Progress, first days'), ('progress21', 'Progress, day 21'), ('history', 'History'), ('hsession', 'A past talk'), ('me', 'Me'), ('settings', 'Settings'), ('privacy', 'Privacy')]),
          ('Plans and edge cases', [('freelimit', 'Free talks used'), ('plans', 'Choose a plan'), ('pay', 'Payment'), ('meplan', 'Your plan'), ('cancel', 'Cancel plan'), ('invite', 'Invite friends'), ('guestlimit', '4th guest talk'), ('restore', 'New phone: restore')])]
nav = ''.join(f'<div class="pgroup"><div class="pgroup__t">{g}</div>' + ''.join(f'<a href="#{i}" data-jump="{i}">{t}</a>' for i, t in items) + '</div>' for g, items in GROUPS)
TASKS = ['Open Bolo and get to your first conversation. (Watch the first-word timer.)', 'Talk with Bolo for a few turns about your day, then end and get feedback.',
         'Listen to what your coach says. Try one fix.', 'Save your progress and choose when Bolo should remind you.',
         'Find out how you are doing so far. Then find where to make Bolo speak slower.', 'It\'s next month and your free talks are used up. What would you do?']
tasks = ''.join(f'<li>{t}</li>' for t in TASKS)
JS = open(S + '/tools/proto.js').read()
page = f'''<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Bolo prototype</title>
<link rel="stylesheet" href="{imp}">
<style>{tokens}
{bundle}
{''.join(EXTRA_CSS)}
{open(S + '/tools/proto.css').read()}</style></head>
<body>
<div class="pwrap">
  <div class="pstage"><div class="pdevice" id="device" data-theme="daylight">{screens_html}<div class="ptoast" id="toast" role="status"></div></div></div>
  <aside class="ppanel" id="panel">
    <div class="ppanel__head"><div><b>Bolo</b> prototype</div><button class="pclose" id="panelClose" aria-label="Close panel">×</button></div>
    <p class="pnote">Phase 0 test build. Bolo's lines play by themselves. When it's the tester's turn, they <b>speak out loud</b> and tap <b>Done</b>; the prototype doesn't listen. On a phone, open this page full screen; tap the small dot at the top left for this panel.</p>
    <div class="prow"><button class="pbtn" id="restart">Restart from splash</button><button class="pbtn" id="theme">Night theme</button></div>
    <label class="pcheck"><input type="checkbox" id="voice"> Bolo speaks aloud (device voice)</label>
    <div class="ptimer">Time to first word <b id="timer">–</b><span>PRD target: 60 s or less</span></div>
    <div class="pgroup"><div class="pgroup__t">Tasks for testers</div><ol class="ptasks">{tasks}</ol></div>
    {nav}
  </aside>
  <button class="pdot" id="panelOpen" aria-label="Open facilitator panel"></button>
</div>
<script>{JS}</script>
</body></html>'''
open(OUT, 'w').write(page); print('ok', len(SCR), 'screens,', len(page) // 1024, 'KB')
