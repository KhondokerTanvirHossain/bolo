import sys, runpy
OUT = sys.argv[1]; T = sys.argv[2]
b = runpy.run_path(T + '/buddy.py'); body, face, antenna, star = b['body'], b['face'], b['antenna'], b['star']
SUN, IND, CREAM, HILL, ROSE = b['SUN'], b['IND'], b['CREAM'], b['HILL'], b['ROSE']
NEAR = '#140A28'
def buddy(eyes='open', mouth='talk', size=120, bg=None, cls=''):
    bgr = f'<rect width="108" height="108" fill="{bg}"/>' if bg else ''
    return (f'<svg class="{cls}" viewBox="0 0 108 108" width="{size}" height="{size}">{bgr}{antenna(54, 56, 26, SUN, CREAM)}'
            f'{body(54, 56, 26, SUN)}{face(54, 56, 26, eyes, mouth)}</svg>')
def status(dark=True):
    c = '#fbf5ff' if dark else '#1f1433'
    return f'<div class="sb" style="color:{c}"><span>9:41</span><span>●●● ▮</span></div>'
def phone(inner, cls='', dark=True):
    return f'<div class="ph {cls}">{status(dark)}{inner}</div>'
# 1-3 splash
splash1 = phone(f'''<div class="splash"><svg viewBox="0 0 300 560" class="full"><defs><radialGradient id="ga"><stop offset="0%" stop-color="{SUN}" stop-opacity=".45"/><stop offset="100%" stop-color="{SUN}" stop-opacity="0"/></radialGradient></defs>
<circle cx="150" cy="300" r="120" fill="url(#ga)"/><g transform="translate(96 270) scale(1)">{antenna(54,56,26,SUN,CREAM)}{body(54,56,26,SUN)}{face(54,56,26,"closed","smile")}</g>
<path d="M0 330 Q150 300 300 330 V560 H0Z" fill="{HILL}"/><path d="M0 352 Q150 336 300 356 V560 H0Z" fill="{NEAR}"/></svg></div>''')
splash2 = phone(f'''<div class="splash"><svg viewBox="0 0 300 560" class="full"><defs><radialGradient id="gb"><stop offset="0%" stop-color="{SUN}" stop-opacity=".5"/><stop offset="100%" stop-color="{SUN}" stop-opacity="0"/></radialGradient></defs>
<circle cx="150" cy="240" r="130" fill="url(#gb)"/>{star(70,120,5,CREAM,.8)}{star(232,92,4,CREAM,.7)}<g transform="translate(64 158) scale(1.6)">{antenna(54,56,26,SUN,CREAM)}{body(54,56,26,SUN)}{face(54,56,26,"open","smile")}</g>
<path d="M0 400 Q150 372 300 400 V560 H0Z" fill="{HILL}"/><path d="M0 424 Q150 410 300 428 V560 H0Z" fill="{NEAR}"/>
<text x="150" y="512" text-anchor="middle" font-family="Anek Bangla" font-weight="750" font-size="30" fill="#fbf5ff" letter-spacing="-1">Bolo</text></svg></div>''')
splash3 = phone(f'''<div class="home"><div class="greet">Good evening, Rafi</div><div class="scard"><svg viewBox="0 0 260 170" class="full"><defs><radialGradient id="gc"><stop offset="0%" stop-color="{SUN}" stop-opacity=".5"/><stop offset="100%" stop-color="{SUN}" stop-opacity="0"/></radialGradient>
<linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2b1b4e"/><stop offset=".55" stop-color="#5b3f8c"/><stop offset="1" stop-color="#c98aa3"/></linearGradient></defs>
<rect width="260" height="170" fill="url(#sky)"/>{star(60,30,3,CREAM,.8)}<circle cx="165" cy="112" r="60" fill="url(#gc)"/><circle cx="165" cy="112" r="30" fill="{SUN}"/><path d="M0 118 Q130 104 260 116 V170 H0Z" fill="{HILL}"/><path d="M0 140 Q130 128 260 146 V170 H0Z" fill="{NEAR}"/>
<g transform="translate(78 108)" fill="{NEAR}"><circle cx="0" cy="0" r="8"/><path d="M-14 26 C-14 12 -8 8 0 8 C8 8 14 12 14 26Z"/></g></svg>
<div class="cap">Did the payment fix go live? Tell me.</div></div><div class="talk">Talk</div></div>''', 'light', dark=False)
# 4 mic permission
mic = phone(f'''<div class="flow">{buddy("side","small",132)}<h3>Bolo needs your mic to hear you</h3><p>Bolo listens only while you practise.</p><p class="bn">আপনার কথা শুনতে বলোর মাইক দরকার।</p><div class="sp"></div><div class="btn">Allow microphone</div></div>''', 'light', dark=False)
# 5 long wait
wait = phone(f'''<div class="flow">{buddy("open","smile",132,cls="breathe")}<h3>Bringing your talks over</h3><p>From your last phone. This takes a few seconds on mobile data.</p><div class="bar"><i style="width:58%"></i></div><p class="meta">7 of 12 talks</p><div class="sp"></div></div>''', 'light', dark=False)
# 6 offline
off = phone(f'''<div class="flow">{buddy("closed","smile",132)}<h3>No internet right now</h3><p>Talking needs a connection. Your drills work offline.</p><p class="bn">ইন্টারনেট নেই। ড্রিলগুলো অফলাইনে চলবে।</p><div class="sp"></div><div class="btn">Practise drills</div></div>''', 'light', dark=False)
# 7 notification
silh = (f'<svg viewBox="0 0 108 108" width="16" height="16"><g fill="#fbf5ff">{antenna(54,56,26,"#fbf5ff","#fbf5ff")}{body(54,56,26,"#fbf5ff")}</g></svg>')
notif = phone(f'''<div class="lock"><div class="clock">6:30</div><div class="sbicons">{silh}<span>status bar icon</span></div>
<div class="notif"><div class="nhead">{silh.replace('#fbf5ff', '#5a4e6e')}<span>Bolo · now</span></div><div class="nbody"><div><b>Bolo</b><br>Bolo wants to know how the demo went.</div>{buddy("open","talk",44,bg=IND)}</div></div></div>''')

SCEN = [
 ('App icon, Play Store', 'Yes', 'Talking (B)', 'Static. The brand\'s face.'),
 ('Splash on a cold start', 'Yes', 'Resting → idle', 'Rises over the hill, opens its eyes, then hands off to Home (≤ 1 s).'),
 ('Notifications', 'Yes', 'Talking', 'Status bar: white silhouette, no face. Expanded: the face as the large icon.'),
 ('Mic permission, onboarding', 'Yes', 'Listening', 'The only Bolo on screen, so it may look toward you once.'),
 ('Long app-level waits (> 2 s): restore, scene download, first start', 'Yes', 'Idle, breathing', 'Always with real progress in words or a bar. Never alone.'),
 ('Offline, nothing yet, couldn\'t prepare', 'Yes', 'Resting or idle', 'Replaces the StatePanel icon. Never sad, never worried.'),
 ('Goodbyes and milestones: "See you tomorrow", a new run, plan started', 'Yes', 'Happy', 'Rare on purpose, so it still means something.'),
 ('Home', 'No', '—', 'The scene and your companion already are Bolo.'),
 ('Conversation: listening, thinking, your turn', 'No', '—', 'The companion and the mic ring carry it. Two Bolos would break presence.'),
 ('Scoring, debrief, drills, Progress', 'No', '—', 'The Coach\'s moments. The Coach is a different character.'),
 ('Short loads (< 1 s), button presses, results', 'No', '—', 'No spinner, no mascot. Content simply appears. Bolo never reacts to how you spoke.'),
]
rows = ''.join(f'<tr><td>{a}</td><td class="{"y" if b=="Yes" else "n"}">{b}</td><td>{c}</td><td>{d}</td></tr>' for a, b, c, d in SCEN)
css = '''
*{box-sizing:border-box} body{margin:0;background:#f7f4fb;color:#1f1433;font-family:'Anek Bangla',system-ui,sans-serif;-webkit-font-smoothing:antialiased}
.wrap{max-width:1080px;margin:0 auto;padding:48px 32px 80px}
h1{font-size:40px;line-height:44px;font-weight:750;letter-spacing:-.02em;margin:0 0 12px} h2{font-size:28px;line-height:34px;font-weight:700;margin:0 0 10px}
.lead{font-size:18px;line-height:28px;color:#5a4e6e;max-width:780px;margin:0}
.card{background:#fff;border-radius:28px;padding:36px 40px;margin-top:28px;box-shadow:0 1px 2px rgba(31,20,51,.06),0 8px 28px rgba(31,20,51,.06)}
.rules{display:grid;grid-template-columns:repeat(3,1fr);gap:16px;margin-top:20px}
.rule{background:#f7f4fb;border-radius:18px;padding:18px 20px} .rule b{display:block;font-size:17px;margin-bottom:4px} .rule span{font-size:15px;line-height:22px;color:#5a4e6e}
.phones{display:flex;flex-wrap:wrap;gap:24px;margin-top:20px} figure{margin:0;width:228px} figcaption{font-size:14px;line-height:20px;color:#3a3048;margin-top:10px} figcaption b{display:block;color:#1f1433}
.ph{width:228px;height:440px;border-radius:30px;overflow:hidden;position:relative;background:#1d1236;box-shadow:0 0 0 6px #0b0c10;display:flex;flex-direction:column;color:#fbf5ff}
.ph.light{background:#f7f4fb;color:#1f1433}
.sb{height:26px;flex:none;display:flex;justify-content:space-between;align-items:center;padding:0 18px;font-size:11px;font-weight:650}
.full{width:100%;height:100%;display:block} .splash{position:absolute;inset:0}
.home{flex:1;padding:6px 14px;display:flex;flex-direction:column} .greet{font-size:12px;color:#5a4e6e;padding:6px 0}
.scard{position:relative;border-radius:16px;overflow:hidden;height:150px} .cap{position:absolute;left:10px;right:10px;bottom:10px;background:rgba(20,12,38,.62);color:#fff8ef;border-radius:12px;padding:8px 10px;font-size:12px;line-height:16px}
.talk{margin:auto auto 36px;width:96px;height:96px;border-radius:50%;background:#f4b942;box-shadow:inset 0 0 0 1.5px #9a6a0a,0 8px 28px rgba(244,185,66,.45);display:flex;align-items:center;justify-content:center;font-weight:750;font-size:18px;color:#2a1a00}
.flow{flex:1;display:flex;flex-direction:column;align-items:center;text-align:center;padding:40px 18px 22px}
.flow h3{font-size:19px;line-height:24px;font-weight:700;margin:14px 0 6px} .flow p{font-size:13px;line-height:19px;color:#5a4e6e;margin:0 0 4px} .flow .bn{font-size:12px}
.flow .meta{font-size:12px;font-weight:600;color:#1f1433;margin-top:6px} .sp{flex:1}
.btn{width:100%;height:42px;border-radius:999px;background:#f4b942;box-shadow:inset 0 0 0 1.5px #9a6a0a;color:#2a1a00;font-weight:650;font-size:14px;display:flex;align-items:center;justify-content:center}
.bar{width:100%;height:6px;border-radius:999px;background:#efedf2;margin-top:14px;overflow:hidden} .bar i{display:block;height:100%;background:#f4b942;border-radius:999px}
.breathe{animation:br 4s ease-in-out infinite;transform-origin:50% 60%} @keyframes br{0%,100%{transform:scale(1)}50%{transform:scale(1.03)}}
.lock{flex:1;background:linear-gradient(180deg,#3a4a6e,#20283e);padding:10px 12px;display:flex;flex-direction:column;gap:14px}
.clock{font-size:56px;font-weight:300;text-align:center;line-height:1;margin-top:10px} .sbicons{display:flex;align-items:center;gap:6px;font-size:11px;color:#c9d2e8}
.notif{background:#f7f4fb;color:#1f1433;border-radius:18px;padding:10px 12px} .nhead{display:flex;align-items:center;gap:6px;font-size:11px;color:#5a4e6e}
.nbody{display:flex;gap:10px;align-items:center;justify-content:space-between;font-size:13px;line-height:18px;margin-top:6px} .nbody svg{border-radius:10px;flex:none}
table{width:100%;border-collapse:collapse;margin-top:16px;font-size:15px;line-height:21px} th{text-align:left;font-size:13px;color:#5a4e6e;font-weight:650;padding:8px 10px;border-bottom:1px solid #e6e1ee}
td{padding:10px;border-bottom:1px solid #efedf2;vertical-align:top} td.y{color:#22743f;font-weight:700} td.n{color:#5a4e6e;font-weight:700}
.motion{display:grid;grid-template-columns:1fr 1fr;gap:12px 28px;margin-top:14px;font-size:15px;line-height:22px} .motion div b{display:block}
.ask{background:#1d1236;color:#fbf5ff} .ask p{font-size:17px;line-height:26px;color:#e3d8ca;max-width:820px} .ask h2{color:#fbf5ff} .ask b{color:#f4b942}
'''
page = f'''<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Bolo the buddy: where it appears</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Anek+Bangla:wdth,wght@75..125,100..800&display=swap">
<style>{css}</style></head><body><div class="wrap">
<h1>Bolo the buddy: where it appears</h1>
<p class="lead">How the talking-sun buddy is used across the app: splash, notifications, waits, empty states and moods. The aim is a mascot that stays special, never one that becomes a spinner.</p>
<section class="card"><h2>Three rules</h2><div class="rules">
<div class="rule"><b>Outside the talk, not inside it</b><span>In a conversation, Bolo is the companion in the scene, and the mic ring shows listening and thinking. The buddy never appears there.</span></div>
<div class="rule"><b>Bolo's moments, not the Coach's</b><span>Scoring, feedback, drills and Progress belong to the Coach, a different character. The buddy stays out.</span></div>
<div class="rule"><b>Rare, never a spinner</b><span>Short loads get nothing. Long app waits get the buddy breathing next to real progress. It never judges, so it has no sad or worried face.</span></div>
</div></section>
<section class="card"><h2>Splash: Bolo rises, then becomes your sun</h2>
<p class="lead" style="font-size:16px;line-height:24px">Android 12+ shows the app icon on a plain background at a cold start. Bolo's version uses that moment: the buddy rises over the hill with its eyes closed, opens them, then shrinks into the scene's sun on Home as its face fades. The icon, the splash and Home become one continuous place. Total under 1 s; with "Remove animations" it is a cross-fade.</p>
<div class="phones">
<figure>{splash1}<figcaption><b>0 ms · rising</b>Eyes closed, just over the hill.</figcaption></figure>
<figure>{splash2}<figcaption><b>~400 ms · awake</b>Eyes open, the star on the antenna twinkles once. The wordmark sits at the bottom (Android's branding slot).</figcaption></figure>
<figure>{splash3}<figcaption><b>~900 ms · Home</b>The buddy has shrunk into the scene's sun and its face has faded. Bolo is now the place, and the companion sits beside you.</figcaption></figure>
</div></section>
<section class="card"><h2>Where else the buddy appears</h2>
<div class="phones">
<figure>{mic}<figcaption><b>Mic permission · listening</b>The only Bolo on screen, so here it may look toward you.</figcaption></figure>
<figure>{wait}<figcaption><b>Long wait · idle, breathing</b>Restoring a new phone. Always with real progress beside it.</figcaption></figure>
<figure>{off}<figcaption><b>Offline · resting</b>Calm, not sad. Points to what still works.</figcaption></figure>
<figure>{notif}<figcaption><b>Notification · talking</b>A white silhouette in the status bar (Android requires one colour); the face as the large icon.</figcaption></figure>
</div></section>
<section class="card"><h2>Every scenario</h2>
<table><tr><th>Scenario</th><th>Buddy?</th><th>Mood</th><th>Why</th></tr>{rows}</table></section>
<section class="card"><h2>How the buddy moves</h2><div class="motion">
<div><b>Breathing</b>Scales 1 → 1.03 over 4 s, only during long waits.</div>
<div><b>Blink</b>Every 4–7 s at random, 120 ms. Never in sync with anything.</div>
<div><b>Star</b>The antenna star twinkles like the scene's stars: 4.2 s, very small.</div>
<div><b>Talking</b>Three mouth shapes, driven by real audio only. It never mimes without sound.</div>
<div><b>Moods</b>Idle, listening, talking, happy, resting. No sad, worried or disappointed face, ever.</div>
<div><b>Small sizes</b>Below 24dp, drop the face and use the silhouette, as in the status bar.</div>
<div><b>Reduced motion</b>Static faces; the splash becomes a 150 ms cross-fade.</div>
<div><b>Performance</b>One Rive file with the five moods as states, under 100 KB.</div>
</div></section>
<section class="card ask"><h2>One decision before the final logo</h2>
<p>The buddy is called Bolo, and the companion in the scene is also Bolo. That works if the buddy is <b>Bolo's face outside the talk</b> and the companion is <b>how Bolo looks beside you</b>, the way a voice assistant has an icon and a voice. The alternative is to make the buddy itself the third companion choice (beside the man and the woman), so a learner can talk to the sun-buddy on the hill. I recommend the first now and testing the second in Phase 0. One correction to the logo page: "Happy" is for Bolo's milestones, not for "Nailed it", which is the Coach's result.</p>
</section>
</div></body></html>'''
open(OUT, 'w').write(page); print('ok', len(page))
