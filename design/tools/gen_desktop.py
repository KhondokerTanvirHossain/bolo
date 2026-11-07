import os, re, sys, json, random
S = sys.argv[1]; P = S + '/ds/project'
F = json.load(open(S + '/fontdl/final.json'))
def icon(n, fill=False, cls=''):
    return f'<span class="bo-icon{" bo-icon--fill" if fill else ""}{(" " + cls) if cls else ""}" aria-hidden="true">{n}</span>'
WM = (F['wm_day'].replace('<svg xmlns="http://www.w3.org/2000/svg"', '<svg class="wm-day"') + F['wm_night'].replace('<svg xmlns="http://www.w3.org/2000/svg"', '<svg class="wm-night"'))
def wave(seed, n=30, on=0):
    random.seed(seed); hs = [4, 6] + [random.randint(6, 26) for _ in range(n - 5)] + [8, 5, 4]
    return ''.join(f'<i{" class=\"on\"" if i < on else ""} style="height:{h}px"></i>' for i, h in enumerate(hs))
def clip(seed, model=False, time='0:02', on=0, label='Play'):
    return (f'<div class="bo-clip{" bo-clip--model" if model else ""}"><button class="bo-clip__play" aria-label="{label}" data-toast="Plays the clip">{icon("play_arrow", True)}</button>'
            f'<div class="bo-clip__wave" aria-hidden="true">{wave(seed, on=on)}</div><span class="bo-clip__time">{time}</span>'
            + (f'<button class="bo-clip__tool" aria-label="Play slower" data-toast="Plays at 0.75×">{icon("slow_motion_video", cls="bo-icon--sm")}</button>' if model else '') + '</div>')
def scene(sid, figure=True, sun=(930, 420)):
    random.seed(11)
    dust = ''.join(f'<circle class="dust" cx="{random.randint(20, 1420)}" cy="{random.randint(14, 330)}" r="{random.choice([1.2, 1.5, 1.8, 2.2])}" opacity="{random.choice([.5, .6, .7, .85])}"/>' for _ in range(70))
    def spark(x, y, s, d):
        return (f'<g class="twinkle" style="animation-delay:{d}s"><circle class="starglow" cx="{x}" cy="{y}" r="{s * 1.6}"/>'
                f'<path class="spark" d="M{x} {y - s} Q{x} {y} {x + s} {y} Q{x} {y} {x} {y + s} Q{x} {y} {x - s} {y} Q{x} {y} {x} {y - s}Z"/></g>')
    fig = ('<g transform="translate(560 452) scale(1.9)"><path class="figure" d="M-27 66 C-27 33 -16 18 0 18 C16 18 27 33 27 66 Z"/><circle class="figure" cx="0" cy="3" r="14"/>'
           '<path class="rim" d="M9 -8 A14 14 0 0 1 14 9"/><path class="rim" d="M17 26 C22 34 25 44 26 56"/></g>') if figure else ''
    sx, sy = sun
    return (f'<svg class="bo-scene__art" viewBox="0 0 1440 640" preserveAspectRatio="xMidYMax slice" aria-hidden="true"><defs>'
            f'<radialGradient id="au-{sid}"><stop offset="0%" style="stop-color:var(--scene-sun);stop-opacity:.75"/><stop offset="42%" style="stop-color:var(--scene-sun);stop-opacity:.32"/><stop offset="100%" style="stop-color:var(--scene-sun);stop-opacity:0"/></radialGradient>'
            f'<radialGradient id="st-{sid}"><stop offset="0%" style="stop-color:var(--scene-star);stop-opacity:.7"/><stop offset="100%" style="stop-color:var(--scene-star);stop-opacity:0"/></radialGradient></defs>'
            + dust.replace('class="dust"', 'class="dust"') + spark(300, 150, 9, 0).replace('class="starglow"', f'class="starglow" style="fill:url(#st-{sid})"') + spark(1150, 110, 7, 1.6).replace('class="starglow"', f'class="starglow" style="fill:url(#st-{sid})"')
            + f'<circle cx="{sx}" cy="{sy}" r="300" style="fill:url(#au-{sid})"/><circle class="sun" cx="{sx}" cy="{sy}" r="118"/>'
            '<path class="far" d="M0 452 C220 404 430 420 650 444 C880 468 1100 408 1440 428 L1440 640 L0 640Z"/>'
            '<path class="near" d="M0 540 C320 488 760 494 1440 548 L1440 640 L0 640Z"/>' + fig + '</svg>')
def side(cur):
    items = [('home', 'graphic_eq', 'Talk'), ('progress', 'insights', 'Progress'), ('me', 'person', 'Me')]
    nav = ''.join(f'<button class="dk-nav" data-go="{g}"{" aria-current=\"page\"" if g == cur else ""}>{icon(i)}{t}</button>' for g, i, t in items)
    return (f'<aside class="dk-side"><div class="dk-logo">{WM}</div>{nav}<div class="dk-side__foot">'
            f'<div class="dk-side__card"><b>Free plan</b><span>4 of 7 free talks left in your first week</span><br><a class="dk-link" data-go="plans">See plans</a></div>'
            f'<div class="dk-side__card"><b>Bolo on your phone</b><span>Same account in your phone&rsquo;s browser, for talks on the bus.</span><br><a class="dk-link" data-toast="A code to scan: bolo.app opens on your phone, already signed in">Show the code</a></div></div></aside>')
def app(cur, body): return f'<div class="dk-app">{side(cur)}<main class="dk-main"><div class="dk-page">{body}</div></main></div>'
def micring(state, label, dash, core):
    return (f'<div class="bo-mic bo-mic--{state}" role="status" aria-label="{label}" data-act="ring"><svg class="arc" viewBox="0 0 96 96" aria-hidden="true"><circle class="track" cx="48" cy="48" r="46"/>'
            f'<circle class="left" cx="48" cy="48" r="46" stroke-dasharray="{dash} 289.0"/></svg><div class="bo-mic__core">{icon(core, cls="bo-icon--lg")}</div></div>')
def scenebg(sid, figure=True): return f'<div class="bo-scene" style="position:absolute;inset:0">{scene(sid, figure)}</div>'
def card(inner, wide=False): return f'<div class="dk-dim"></div><div class="dk-card{" dk-card--wide" if wide else ""}" role="dialog" aria-modal="true">{inner}</div>'
SC = {}
# ---------- onboarding ----------
SC['welcome'] = (f'{scenebg("w")}<div class="dk-talk__top"><span style="display:flex;height:28px">{F["wm_night"].replace('<svg xmlns="http://www.w3.org/2000/svg"', '<svg style="height:28px;width:auto"')}</span>'
                 f'<button class="bo-scenebtn" data-go="consent">Skip intro</button></div>'
                 f'<div class="dk-talk__cap"><div class="bo-caption"><p class="bo-caption__text" data-cap>Hi, I\'m Bolo. We\'ll just talk, like friends. <span class="dim">No one is grading you.</span></p></div></div>'
                 f'<div class="dk-dock" style="background:transparent;-webkit-backdrop-filter:none;backdrop-filter:none">{micring("buddy", "Bolo is speaking", "220", "graphic_eq")}</div>')
SC['consent'] = scenebg('c') + card(f'''<div class="dk-steps"><span>Before Bolo listens</span><span>1 of 5</span></div>
  <div class="bo-hero-icon bo-hero-icon--coach" style="width:64px;height:64px;margin-bottom:18px">{icon('shield_person')}</div>
  <h1 class="bo-flow__title">Bolo listens only while you practise.</h1>
  <p class="bo-flow__body">Keep your recordings so you can hear your progress later?</p>
  <p class="bo-flow__bn">রেকর্ডিং রেখে দেবেন, যাতে পরে নিজের উন্নতি শুনতে পারেন?</p>
  <label class="bo-check bo-check--on" style="margin-top:18px" data-act="check"><span class="bo-check__box">{icon('check', cls='bo-icon--sm')}</span>I'm 13 or older</label>
  <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:12px"><button class="bo-btn bo-btn--secondary" data-go="mic" data-need-check>Yes, keep them</button><button class="bo-btn bo-btn--secondary" data-go="mic" data-need-check>No, just feedback</button></div>
  <p class="bo-footnote" style="margin-top:14px">You can change this any time in Me → Privacy.</p>''')
lv = [6, 10, 18, 28, 40, 52, 60, 66, 58, 46, 34, 26, 18, 12, 8]
SC['mic'] = scenebg('m') + card(f'''<div class="dk-steps"><span>Microphone</span><span>2 of 5</span></div>
  <h1 class="bo-flow__title">Choose your microphone</h1>
  <p class="bo-flow__body">Read this out loud: <b style="color:var(--ink)">"I'm ready to practise speaking today."</b></p>
  <div class="dk-select" style="margin-top:20px" data-toast="Lists every mic: laptop, headset, webcam">{icon('headset_mic')}<span>Headset microphone (USB)</span>{icon('expand_more')}</div>
  <div class="bo-level" style="margin-top:18px" data-level>{''.join(f'<i class="{"on" if 3 <= i <= 11 else ""}" style="height:{h}px"></i>' for i, h in enumerate(lv))}</div>
  <div class="dk-tip" style="margin-top:18px">{icon('headphones')}<span>Using laptop speakers? Headphones stop Bolo hearing itself, and keep your talk private.</span></div>
  <button class="bo-btn bo-btn--primary bo-btn--block" style="margin-top:20px" data-go="setup">Sounds good, continue</button>
  <p class="bo-footnote" style="margin-top:12px">Your browser asked for the mic once. Bolo listens only while you practise.</p>''')
SC['setup'] = scenebg('s') + card(f'''<div class="dk-steps"><span><span class="bo-buddy-mark" style="width:22px;height:22px;font-size:11px;margin-right:6px">B</span>Bolo asks</span><span data-qn>3 of 5</span></div>
  <h1 class="bo-flow__title" data-q>What do you need English for most?</h1>
  <p class="bo-flow__bn" style="margin-top:0" data-qbn>ইংরেজি আপনার সবচেয়ে বেশি কোথায় দরকার?</p>
  <p class="bo-footnote" style="text-align:left;margin:18px 0 10px">Say it, or click one. Keys 1–4 work too.</p>
  <div class="bo-chips" data-chips><button class="bo-chip" aria-pressed="false">Work meetings</button><button class="bo-chip" aria-pressed="false">Job interviews</button><button class="bo-chip" aria-pressed="false">Study abroad</button><button class="bo-chip" aria-pressed="false">Daily confidence</button></div>
  <button class="bo-btn bo-btn--primary bo-btn--block" style="margin-top:28px" data-act="nextq">Next</button>''')
SC['day1'] = scenebg('d') + card(f'''<div class="dk-steps"><span>Your Day 1 clip</span><span>5 of 5</span></div>
  <h1 class="bo-flow__title">Introduce yourself, like at a new job</h1>
  <p class="bo-flow__body">30 seconds. We'll play it back to you on Day 7.</p>
  <ul class="bo-prompts" style="margin-top:16px"><li>{icon('person', cls='bo-icon--sm')}Your name and where you're from</li><li>{icon('work', cls='bo-icon--sm')}What you do</li><li>{icon('favorite', cls='bo-icon--sm')}One thing you enjoy</li></ul>
  <div class="bo-rec" style="margin-top:22px"><svg class="arc" viewBox="0 0 168 168" aria-hidden="true"><circle class="track" cx="84" cy="84" r="80"/><circle class="left" cx="84" cy="84" r="80" stroke-dasharray="301.6 502.7" data-recarc/></svg>
    <button class="bo-rec__btn" data-act="rec" aria-label="Stop recording"><span data-rect>0:18</span><small>{icon('stop', True, 'bo-icon--sm')}Stop</small></button></div>
  <p class="bo-footnote" style="margin-top:12px">Press <span class="dk-kbd">Space</span> to stop early.</p>''')
# ---------- home ----------
week = ''.join(f'<div class="bo-day{c}"><span class="bo-day__dot">{icon(i, f) if i else ""}</span>{d}</div>' for d, c, i, f in [('Sa', ' bo-day--done', 'wb_sunny', True), ('Su', ' bo-day--done', 'wb_sunny', True), ('Mo', ' bo-day--rest', 'bedtime', False), ('Tu', ' bo-day--done', 'wb_sunny', True), ('We', ' bo-day--done', 'wb_sunny', True), ('Th', ' bo-day--today', 'wb_sunny', False), ('Fr', '', '', False)])
SC['home'] = app('home', f'''<p class="dk-sub" style="margin:0">Good evening, Rafi</p><h1 class="dk-h1">Ready when you are.</h1>
  <div class="dk-hero bo-scene">{scene('h')}
    <div class="bo-caption"><p class="bo-caption__text" style="font-size:20px;line-height:28px">Did the payment fix go live? Tell me.</p></div>
    <div class="dk-hero__hint"><span class="dk-kbd">Space</span>to start talking</div>
    <div class="dk-hero__talk"><button class="bo-talk" data-go="talk" aria-label="Talk">{icon('mic', True)}<span class="bo-talk__label">Talk</span></button>
      <div class="bo-talk-meta">Free talk · 5 min · <a data-go="scenario-dlg">Change</a></div></div>
  </div>
  <div class="dk-cols">
    <div><div class="dk-h2">Next up</div><div class="bo-homecard" data-go="debrief">{'<span class="bo-homecard__icon">' + icon('forum', True) + '</span>'}<div class="bo-homecard__body"><div class="bo-homecard__title">Finish your feedback</div><div class="bo-homecard__sub">2 fixes left from yesterday</div></div>{icon('chevron_right', cls='bo-chev')}</div></div>
    <div><div class="dk-h2">This week <a data-go="progress">Progress</a></div><div class="bo-card"><div style="display:flex;justify-content:space-between;margin-bottom:12px"><b>6-day run</b><span class="subhead" style="color:var(--ink-secondary)">1 rest day used</span></div><div class="bo-week">{week}</div></div></div>
  </div>''')
# ---------- talk ----------
SC['talk'] = (f'<div class="dk-talk bo-scene" data-convo>{scene("t")}'
              f'<div class="dk-talk__top"><button class="bo-scenebtn" data-toast="Scene and companion: Hilltop, Evening drive, Voice only">{icon("landscape", cls="bo-icon--sm")}Hilltop</button>'
              f'<span style="color:var(--on-scene-secondary);font:500 14px/20px var(--font-sans)">Free talk · 5 min</span><button class="bo-scenebtn" data-go="endsheet">End</button></div>'
              f'<div class="dk-talk__cap"><div class="bo-caption"><p class="bo-caption__text" data-cap>So, Rafi, what did you work on today?</p><button class="bo-caption__replay" aria-label="Repeat that" data-act="repeat">{icon("replay", cls="bo-icon--sm")}</button></div></div>'
              f'<div class="dk-dock"><button class="bo-scenebtn" aria-label="Pause" data-act="pause">{icon("pause", True)}</button>{micring("turn", "Your turn", "250", "mic")}<button class="bo-scenebtn" data-act="done" aria-label="Done, end my turn">Done</button></div>'
              f'<div class="dk-keys"><span><span class="dk-kbd">Space</span>Done talking</span><span><span class="dk-kbd">P</span>Pause</span><span><span class="dk-kbd">R</span>Repeat</span><span><span class="dk-kbd">Esc</span>End</span></div></div>')
SC['endsheet'] = SC['talk'].replace('data-convo', 'data-convo-static') + card('''<h2 class="bo-sheet__title">End and get feedback?</h2><p class="bo-sheet__body" style="margin-top:8px">You've talked for 3 minutes. That's enough for useful feedback.</p>
  <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px"><button class="bo-btn bo-btn--secondary" data-go="scoring">End and get feedback</button><button class="bo-btn bo-btn--primary" data-go="talk">Keep talking</button></div>
  <p class="bo-footnote" style="margin-top:12px"><span class="dk-kbd">Esc</span> keeps talking</p>''')
SC['scoring'] = (f'<div class="dk-talk" style="display:flex;align-items:center;justify-content:center"><div class="bo-state"><div class="bo-listenback" aria-hidden="true">'
                 + ''.join(f'<i style="height:{h}px;animation-delay:{i * .08:.2f}s"></i>' for i, h in enumerate([10, 18, 28, 40, 30, 22, 36, 48, 34, 20, 26, 40, 30, 16, 10]))
                 + '</div><p class="bo-state__title">Your coach is listening back</p><p class="body" style="margin:0;color:var(--ink-secondary)">Picking the three things that will help most.</p></div></div>')
# ---------- debrief ----------
def tryblock(pill='closer'):
    return (f'<div style="display:flex;flex-direction:column;align-items:center;gap:6px;margin-top:22px"><span class="bo-result bo-result--{pill}" role="status" style="visibility:hidden" data-pill>{icon("north_east")}Closer</span>'
            f'<button class="bo-btn bo-btn--primary" style="min-width:260px" data-act="try">{icon("mic", True)}Try it</button><button class="bo-btn bo-btn--quiet-neutral" data-toast="Thanks. We\'ll leave this one out.">That\'s not what I said</button></div>')
D = [
 ('Something you did well', 'Strength', f'''<div class="bo-card" style="background:var(--hills-soft);box-shadow:none;display:flex;flex-direction:column;gap:16px;padding:28px"><div class="bo-eyebrow bo-eyebrow--coach"><span class="bo-coach-mark">C</span>Your coach · something you did well</div><p class="title-1" style="margin:0">You explained the payment bug for 40 seconds without stopping.</p>{clip(3, time='0:40', label='Hear that moment')}</div>'''),
 ('The v sound', '"bhery" → "very"', f'''<article class="bo-fix" style="padding:28px"><div class="bo-fix__head"><span class="bo-eyebrow bo-eyebrow--coach">Sound fix · 1 of 3</span></div><h2 class="bo-fix__title">The v sound <span style="font:500 15px/20px var(--font-sans);color:var(--ink-secondary)">· v as in <i>van</i></span></h2>
   <div style="display:grid;grid-template-columns:1fr 1fr;gap:20px"><div><p class="bo-fix__label">You said</p>{clip(4)}<p class="bo-fix__sentence">"It's <span class="bo-said">bhery</span> important."</p></div><div><p class="bo-fix__label">Try</p>{clip(5, model=True, on=12)}<p class="bo-fix__sentence">"It's <span class="bo-better">very</span> important."</p></div></div>
   <div class="bo-tip">{icon('sentiment_satisfied')}<p class="bo-tip__text">Top teeth touch your lower lip for v.</p><button class="bo-tip__bn" data-toast="উপরের দাঁত নিচের ঠোঁটে ছুঁইয়ে v বলুন।">বাংলায়</button></div>{tryblock()}</article>'''),
 ('Say it better', 'Your sentence, one step better', f'''<article class="bo-fix" style="padding:28px"><span class="bo-eyebrow bo-eyebrow--coach">Say it better · 2 of 3</span><div><p class="bo-fix__label">You said</p><p class="bo-fix__sentence bo-fix__sentence--before" style="margin:0">"Yesterday I fix bug, it was in payment."</p></div>
   <div><p class="bo-fix__label">One step better</p><p class="bo-fix__sentence" style="margin:0">"Yesterday I <span class="bo-better">fixed a</span> bug in the payment service <span class="bo-better">that was causing failed checkouts</span>."</p></div>{clip(6, model=True, time='0:04')}{tryblock()}</article>'''),
 ('Filler watch', '"actually" × 11', f'''<article class="bo-fix" style="padding:28px"><div class="bo-fix__head"><span class="bo-eyebrow bo-eyebrow--coach">Filler watch · 3 of 3</span></div><div><span class="bo-big-count">"actually" × 11</span><p class="callout" style="margin:4px 0 0;color:var(--ink-secondary)">Click a mark to hear it. Try a short pause instead.</p></div>
   <div><div class="bo-timeline" role="group" aria-label="Where you said actually">{''.join(f'<i style="left:{x}%" data-toast="Plays that moment"></i>' for x in [8, 15, 22, 31, 38, 44, 52, 63, 70, 81, 90])}</div><div class="bo-timeline__axis"><span>0:00</span><span>5:00</span></div></div>
   <div style="display:flex;justify-content:center;margin-top:12px"><button class="bo-btn bo-btn--quiet-neutral" data-toast="Thanks. We\'ll leave this one out.">That's not what I said</button></div></article>'''),
 ('Your starting point', 'Skills and what\'s next', f'''<div class="bo-card" style="padding:28px"><p class="title-2" style="margin:0 0 4px">This is your starting point</p><p class="subhead" style="color:var(--ink-secondary);margin:0 0 12px">Arrows appear after your next talk.</p>
   {''.join(f'<div class="bo-skill"><span class="bo-skill__name">{n}</span><span class="bo-skill__level">{l}</span><div class="bo-meter">{"".join("<i class=\"on\"></i>" if k < m else "<i></i>" for k in range(3))}</div></div>' for n, l, m in [('Pronunciation', 'Good', 2), ('Fluency', 'Strong', 3), ('Sentence building', 'Building', 1), ('Vocabulary', 'Good', 2)])}</div>
   <div style="display:flex;gap:12px;justify-content:center;margin-top:24px"><button class="bo-btn bo-btn--primary" data-go="drill">{icon('timer', True)}Practise (3 min)</button><button class="bo-btn bo-btn--secondary" data-go="save">Later</button></div>'''),
]
lst = ''.join(f'<div class="dk-item" data-deb="{i}"{" aria-current=\"true\"" if i == 0 else ""}><span class="dk-item__n">{icon("star", True, "bo-icon--sm") if i == 0 else (icon("insights", cls="bo-icon--sm") if i == 4 else i)}</span><div><div class="dk-item__t">{t}</div><div class="dk-item__s">{s}</div></div></div>' for i, (t, s, _) in enumerate(D))
det = ''.join(f'<div class="dk-detail d{i}{" on" if i == 0 else ""}" data-detail="{i}">{h}</div>' for i, (_, _, h) in enumerate(D))
SC['debrief'] = (f'<div class="dk-deb"><div class="dk-deb__list"><div class="bo-eyebrow bo-eyebrow--coach" style="padding:0 14px 12px"><span class="bo-coach-mark">C</span>Your coach · today\'s talk</div>{lst}'
                 f'<div style="margin-top:auto;padding:0 14px"><button class="bo-btn bo-btn--quiet-neutral" data-go="home" style="padding:0">{icon("close", cls="bo-icon--sm")}Close feedback</button></div></div>'
                 f'<div class="dk-deb__pane">{det}<div class="dk-deb__nav"><span><span class="dk-kbd">↑</span> <span class="dk-kbd">↓</span> move between cards</span><span><span class="dk-kbd">Space</span> plays your clip · <span class="dk-kbd">T</span> try it</span></div></div></div>')
# ---------- drill ----------
SC['drill'] = (f'<div class="dk-main" style="padding:28px 48px"><div style="display:flex;justify-content:space-between;align-items:center;max-width:640px;margin:0 auto 24px"><button class="bo-btn bo-btn--quiet-neutral" data-go="save" aria-label="Close drills">{icon("close")}</button>'
               f'<div class="bo-dots"><i class="on"></i><i></i><i></i></div><button class="bo-btn bo-btn--quiet-neutral" data-go="drilldone">Skip</button></div>'
               f'<div class="dk-stagecard"><div class="bo-eyebrow bo-eyebrow--coach"><span class="bo-coach-mark">C</span>The v sound · Hear the difference</div>'
               f'<h1 class="bo-flow__title" style="text-align:center;margin-top:24px">Which did you hear?</h1><button class="bo-hear" style="margin:20px auto" aria-label="Hear it again" data-toast="Bolo plays one word">{icon("volume_up", cls="bo-icon--lg")}</button>'
               f'<div class="bo-pairs" style="margin-top:12px"><button class="bo-pair" data-act="pair-right">very<small><span class="dk-kbd">1</span></small></button><button class="bo-pair" data-act="pair-other">berry<small><span class="dk-kbd">2</span></small></button></div>'
               f'<p class="bo-footnote" style="margin-top:18px">Listening round 1 of 3, then you say 5 words. Press <span class="dk-kbd">L</span> to listen again.</p></div></div>')
SC['drilldone'] = (f'<div class="dk-main" style="display:flex;align-items:center;justify-content:center"><div class="bo-state" style="gap:12px;width:520px"><p class="display" style="margin:0">Nice work.</p><p class="body" style="margin:0;color:var(--ink-secondary)">What moved today</p>'
                   f'<div class="bo-card" style="width:100%;text-align:left;display:flex;flex-direction:column;gap:14px;margin-top:12px"><div style="display:flex;justify-content:space-between;align-items:center"><span class="headline">v sound</span><span style="display:flex;gap:6px;align-items:center"><span class="bo-result bo-result--closer" style="height:32px;font-size:14px">Closer</span>{icon("arrow_forward", cls="bo-icon--sm")}<span class="bo-result bo-result--nailed" style="height:32px;font-size:14px">Nailed it</span></span></div>'
                   f'<div style="display:flex;justify-content:space-between;align-items:center"><span class="headline">Upgraded sentence</span><span class="bo-result bo-result--nailed" style="height:32px;font-size:14px;white-space:nowrap">{icon("check", cls="bo-icon--sm")}From memory</span></div></div>'
                   f'<button class="bo-btn bo-btn--primary" style="min-width:240px;margin-top:20px" data-go="save">Done</button></div></div>')
# ---------- save + reminder ----------
G = F['full']  # not used
GOOGLE_G = ('<svg viewBox="0 0 48 48" width="20" height="20" aria-hidden="true"><path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/><path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/><path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/><path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/></svg>')
keep = ''.join(f'<div class="bo-keep__row"><span class="bo-keep__icon{(" bo-keep__icon--" + v) if v else ""}">{icon(i, True)}</span><div><div class="bo-keep__title">{t}</div><div class="bo-keep__sub">{s}</div></div></div>' for i, v, t, s in [('mic', '', 'Your Day 1 clip', "0:30 · we'll play it back on Day 7"), ('forum', 'coach', "Today's feedback", '1 strength and 3 things to try'), ('flag', 'buddy', 'Your goals', 'Work meetings, job interviews')])
SC['save'] = scenebg('sv', False) + card(f'''<h1 class="bo-flow__title">Save your progress</h1><p class="bo-flow__body">Keep your Day 1 voice and today's feedback, on this computer and your phone.</p>
  <div class="bo-keep" style="margin-top:18px">{keep}</div>
  <div style="display:flex;flex-direction:column;gap:10px;margin-top:22px"><button class="bo-provider" data-go="reminder" data-toast="Google's sign-in window opens">{GOOGLE_G}Continue with Google</button><button class="bo-btn bo-btn--secondary bo-btn--block" data-go="reminder" data-toast="Phone number and SMS code">{icon('smartphone')}Use phone number</button>
  <button class="bo-btn bo-btn--quiet-neutral" style="align-self:center" data-go="reminder" data-toast="Your progress stays in this browser only">Not now</button></div>''', wide=False)
qr = ''.join('<i class="o"></i>' if (random.seed(i) or random.random() < .45) and not (i % 11 < 3 and i // 11 < 3 or i % 11 > 7 and i // 11 < 3 or i % 11 < 3 and i // 11 > 7) else '<i></i>' for i in range(121))
slots = ''.join(f'<button class="bo-slot" role="radio" aria-checked="{"true" if s else "false"}" data-act="slot">{icon(ic, s)}<span class="bo-slot__name">{n}</span><span class="bo-slot__time">{t}</span></button>' for ic, n, t, s in [('wb_twilight', 'Morning', '8:00 am', False), ('lunch_dining', 'Lunch', '1:30 pm', False), ('directions_bus', 'Evening', '6:30 pm', True), ('bedtime', 'Night', '10:00 pm', False)])
FAVI = F['notif'].replace('<svg xmlns="http://www.w3.org/2000/svg"', '<svg width="22" height="22"').replace('#FFFFFF', '#F4B942')
SC['reminder'] = scenebg('r', False) + card(f"""<h1 class="bo-flow__title">When should Bolo check in?</h1><p class="bo-flow__body">One reminder a day in this browser, only if you haven't talked yet. Your browser will ask you once.</p>
  <div class="bo-slots" style="grid-template-columns:repeat(4,1fr);margin-top:18px" role="radiogroup" aria-label="Reminder time">{slots}</div>
  <div class="dk-notifbox">
    <div class="micro" style="color:var(--ink-secondary);margin-bottom:8px">What you'll see</div>
    <div class="dk-wnotif" aria-label="Example reminder"><span class="dk-wnotif__icon">{FAVI}</span><div style="min-width:0"><div class="dk-wnotif__meta"><b>Bolo</b> · bolo.app · <span data-slot-time>6:30 pm</span></div><p class="dk-wnotif__text" data-notif-text data-full="Bolo wants to know how the demo went." data-short="Time for today's talk.">Bolo wants to know how the demo went.</p></div></div>
    <div class="bo-row" style="padding:14px 0 0;cursor:default"><div class="bo-row__body"><div class="bo-row__title">Keep it private</div><div class="bo-row__sub">Show only "Time for today's talk", never what we talked about</div></div><button class="bo-toggle" role="switch" aria-checked="false" aria-label="Keep it private" data-act="privnote"></button></div>
  </div>
  <button class="bo-btn bo-btn--primary bo-btn--block" style="margin-top:22px" data-act="notify">{icon('notifications', cls='bo-icon--sm')}Turn on reminders</button>
  <div class="dk-alt"><span class="dk-link" data-act="ics" data-toast="Calendar event: Talk with Bolo, daily, 6:30 pm">Add to my calendar instead</span><span class="dk-link" data-toast="A code to scan: bolo.app opens on your phone, already signed in">Remind me on my phone</span><span class="dk-link dk-link--quiet" data-go="home" data-toast="No reminders. You can turn them on in Me.">Not now</span></div>
""", wide=True) + '''<div class="dk-perm" role="dialog" aria-label="Browser permission" hidden><div class="dk-perm__site"><span class="bo-icon bo-icon--sm" aria-hidden="true">lock</span>bolo.app wants to</div><div class="dk-perm__ask"><span class="bo-icon bo-icon--sm" aria-hidden="true">notifications</span>Show notifications</div><div class="dk-perm__btns"><button data-act="perm-block">Block</button><button data-act="perm-allow">Allow</button></div><div class="dk-perm__note">This is your browser's own box; Bolo cannot style it.</div></div>'''
# ---------- progress ----------
def trend(name, level, path, ey):
    return (f'<div class="bo-trend__cell"><div class="bo-trend__name">{name}</div><div class="bo-trend__level">{level}</div>'
            f'<svg viewBox="0 0 140 44" preserveAspectRatio="none" aria-hidden="true"><rect class="band" x="0" y="14.7" width="140" height="14.7" rx="4"/><path class="line" d="{path}"/><circle class="end" cx="140.0" cy="{ey}" r="3.5"/></svg></div>')
TR = (trend('Pronunciation', 'Good', 'M0.0 31.6 L23.3 30.0 L46.7 28.4 L70.0 24.6 L93.3 25.2 L116.7 20.4 L140.0 18.8', 18.8) + trend('Fluency', 'Strong', 'M0.0 28.4 L23.3 26.8 L46.7 22.0 L70.0 20.4 L93.3 15.6 L116.7 14.0 L140.0 10.8', 10.8)
      + trend('Sentences', 'Building', 'M0.0 33.2 L23.3 31.6 L46.7 32.2 L70.0 30.0 L93.3 29.0 L116.7 29.7 L140.0 28.4', 28.4) + trend('Vocabulary', 'Good', 'M0.0 26.8 L23.3 25.2 L46.7 25.8 L70.0 23.6 L93.3 22.0 L116.7 22.6 L140.0 20.4', 20.4))
pat = ''.join(f'<div class="bo-row" data-go="drill"><div class="bo-row__body"><div class="bo-row__title">{t}</div><div class="bo-row__sub">{s}</div></div><span class="bo-tag bo-tag--{k}">{l}</span>{icon("chevron_right", cls="bo-chev")}</div>' for t, s, k, l in [('v → bh', '"bhery", "bhoice"', 'improving', 'Improving'), ('Says "actually" often', '4 per talk', 'steady', 'Steady'), ('Drops "the" and "a"', 'Seen in 2 talks', 'new', 'New')])
hist = ''.join(f'<div class="bo-session" data-toast="Opens that talk"><div class="bo-session__date"><div class="bo-session__day">{d}</div><div class="bo-session__mon">Nov</div></div><div class="bo-session__body"><div class="bo-session__topic">{t}</div><div class="bo-session__meta">{m}</div><div class="bo-session__fixes">{"".join(f"<span class=bo-fixtag>{f}</span>" for f in fx)}</div></div></div>' for d, t, m, fx in [('6', 'The payment bug demo', 'Free talk · 5 min', ['The v sound', 'Say it better']), ('5', 'Update for your manager', 'Standup rehearsal · 6 min', ['The th sound', 'Shorter answers']), ('3', 'Weekend in Sylhet', 'Free talk · 4 min', ['The w sound'])])
SC['progress'] = app('progress', f'''<h1 class="dk-h1">Progress</h1><p class="dk-sub">21 days · 14 talks · 70 minutes of speaking</p>
  <div class="dk-dash">
    <div class="dk-span-8"><div class="dk-h2">Your voice, then and now</div><div class="bo-compare"><div class="bo-compare__side"><div class="bo-compare__day"><small>Your first intro</small>Day 1</div>{clip(1, time="0:30")}</div><div class="bo-compare__side bo-compare__side--now"><div class="bo-compare__day"><small>Today</small>Day 21</div>{clip(2, time="0:30")}</div></div><p class="headline" style="margin:12px 0 0">Fewer pauses, clearer v sound.</p></div>
    <div class="dk-span-4"><div class="dk-h2">Effort</div><div class="bo-card" style="display:flex;flex-direction:column;gap:18px"><div class="dk-stat">70 min<small>of speaking English</small></div><div class="dk-stat">6 days<small>in a row, 1 rest day used</small></div></div></div>
    <div class="dk-span-12"><div class="dk-h2">Skills <a>Last 30 days</a></div><div class="bo-trend dk-trend4">{TR}</div></div>
    <div class="dk-span-6"><div class="dk-h2">My patterns</div><div class="bo-group">{pat}</div></div>
    <div class="dk-span-6"><div class="dk-h2">History <a data-toast="Opens the full history">See all</a></div><div class="bo-group">{hist}</div></div>
  </div>''')
# ---------- me ----------
def row(title, sub=None, value=None, toggle=None, chev=True):
    t = f'<div class="bo-row"><div class="bo-row__body"><div class="bo-row__title">{title}</div>' + (f'<div class="bo-row__sub">{sub}</div>' if sub else '') + '</div>'
    if value: t += f'<span class="bo-row__value">{value}</span>'
    if toggle is not None: t += f'<button class="bo-toggle" role="switch" aria-checked="{"true" if toggle else "false"}" aria-label="{title}" data-act="toggle"></button>'
    elif chev: t += icon('chevron_right', cls='bo-chev')
    return t + '</div>'
mnav = ''.join(f'<button class="dk-nav"{" aria-current=\"page\"" if i == 2 else ""} data-toast="{t} section">{icon(ic)}{t}</button>' for i, (ic, t) in enumerate([('flag', 'My goals'), ('workspace_premium', 'Your plan'), ('record_voice_over', 'Speaking'), ('tune', 'Display & data'), ('shield_person', 'Privacy'), ('group_add', 'Invite friends')]))
SC['me'] = app('me', f'''<h1 class="dk-h1">Me</h1><p class="dk-sub">rafi.ahmed@gmail.com · Free plan</p>
  <div class="dk-me"><nav class="dk-me__nav">{mnav}</nav><div>
    <div class="dk-h2">Speaking</div><div class="bo-group">{row("Bolo's speed", value='Normal')}{row('Captions', toggle=True)}{row('More time to answer', 'Prompts at 16, 30 and 90 seconds', toggle=False)}{row('I stammer', 'Repeats and blocks never count against you', toggle=False)}{row('Ambient sound', 'Off until you press Talk', toggle=True)}</div>
    <div class="dk-h2" style="margin-top:28px">On this computer</div><div class="bo-group">{row('Microphone', value='Headset microphone (USB)')}{row('How you end your turn', value='Space or Done')}{row('Pause when I switch tabs', 'So no one hears Bolo by surprise', toggle=True)}{row('Keyboard shortcuts', value='Press ?')}</div>
  </div></div>''')
# ---------- plans ----------
plans = ''.join(f'<div class="dk-plan{" dk-plan--current" if cur else ""}" role="radio" aria-checked="{"true" if sel else "false"}" data-act="{"" if cur else "plan"}" data-name="{n}" data-price="{p}"><div class="dk-plan__name">{n}{" <span class=\"bo-tag bo-tag--steady\">Your plan</span>" if cur else ""}</div><div class="dk-plan__price">৳{p}<small> a month</small></div><div class="dk-plan__talks">{t}</div></div>'
                for n, p, t, cur, sel in [('Free', '0', '7 talks in your first week, then 2 a month', True, False), ('Basic', '300', '20 talks a month', False, True), ('Premium', '500', '50 talks a month', False, False), ('Ultimate', '1000', '100 talks a month', False, False)])
SC['plans'] = app('me', f'''<h1 class="dk-h1">Choose a plan</h1><p class="dk-sub">Each talk is up to 5 minutes, then your coach's feedback. Every plan has every feature.</p>
  <div class="dk-plans" role="radiogroup">{plans}</div>
  <div style="display:flex;align-items:center;justify-content:space-between;margin-top:28px;background:var(--surface);border-radius:var(--radius-lg);box-shadow:var(--shadow-card);padding:18px 24px">
    <div><b>Pay with bKash or card</b><p class="bo-row__sub" style="margin:2px 0 0;font-size:14px">On a computer, bKash shows a QR code to scan with your bKash app. Renews monthly; cancel in 2 clicks.</p></div>
    <button class="bo-btn bo-btn--primary" data-act="buy" data-plan-btn>Get Basic · ৳300 a month</button></div>''')
SC = {'start': (f'{scenebg("st")}<div class="dk-dim" style="background:rgba(20,12,38,.35);backdrop-filter:none"></div>'
  f'<div style="position:absolute;left:50%;top:50%;transform:translate(-50%,-58%);z-index:4;text-align:center;color:var(--on-scene);width:620px">'
  f'<div style="display:flex;justify-content:center">{F["wm_night"].replace('<svg xmlns="http://www.w3.org/2000/svg"', '<svg style="height:64px;width:auto"')}</div>'
  f'<p style="font:600 30px/38px var(--font-sans);margin:22px 0 8px;letter-spacing:-.01em">Talk first. Get better after.</p>'
  f'<p style="font:500 17px/26px var(--font-sans);color:var(--on-scene-secondary);margin:0 0 28px">Practise speaking English with a friend who never judges. Bolo talks out loud, so turn your sound on.</p>'
  f'<button class="bo-btn bo-btn--primary" style="min-width:240px;height:60px;font-size:19px" data-go="welcome">{icon("volume_up", True)}Meet Bolo</button>'
  f'<p style="margin:18px 0 0"><a class="dk-link" style="color:var(--scene-sun)" data-go="home" data-toast="Signs in and restores your talks">I already have an account</a></p></div>'), **SC}
for k in list(SC): SC[k] = f'<section class="dk-screen" data-screen="{k}">{SC[k]}</section>'
json.dump(SC, open(S + '/desk/screens.json', 'w'))
print('ok', list(SC))
