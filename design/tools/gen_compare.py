import math, random, sys
OUT = sys.argv[1]
def wave(n=22, seed=1, played=0.0, maxh=22):
    r = random.Random(seed); o = []
    for k in range(n):
        env = math.sin(math.pi*(k+.5)/n)**.6
        h = max(3, round((0.25+0.75*r.random())*env*maxh))
        o.append(f'<i class="{"on" if k < played*n else ""}" style="height:{h}px"></i>')
    return ''.join(o)
def ic(n, cls=''): return f'<span class="ms {cls}">{n}</span>'
SCENE = '''<svg class="art" viewBox="0 0 360 520" preserveAspectRatio="xMidYMid slice" aria-hidden="true"><circle class="sun" cx="222" cy="300" r="54"/><path class="far" d="M0 316 C64 290 118 300 168 312 C228 326 286 294 360 302 L360 520 L0 520Z"/><path class="near" d="M0 386 C96 356 204 362 360 392 L360 520 L0 520Z"/><g transform="translate(104 312)"><path class="near" d="M-27 66 C-27 33 -16 18 0 18 C16 18 27 33 27 66 Z"/><circle class="near" cx="0" cy="3" r="14"/><path class="rim" d="M9 -8 A14 14 0 0 1 14 9"/></g></svg>'''
SCENE_TALL = SCENE.replace('xMidYMid slice', 'xMidYMax slice')

OPTS = [
 dict(id='a', name='Dusk', tag='Recommended', line='The sunset, done properly. The deep evening sky is the face of Bolo, and apricot is a glowing light on it rather than orange on cream.',
  story='Your PRD already sets Bolo on a hilltop at sunset and on an evening drive. This option makes that sky the brand: calm like Apple Weather and Apple TV, cinematic, quiet and dark-first.',
  fonts='Instrument Sans + Hind Siliguri', fontEn="'Instrument Sans'", fontBn="'Hind Siliguri'", weightTitle=700, radius='22px', btnRadius='999px',
  home='dark', debrief='light',
  dark=dict(canvas='#0d1b2e', surface='#16263c', sunken='#22344d', ink='#f3f5f8', ink2='#a7b3c4', tint='#ffa25c', ontint='#2a1404', tintink='#ffb680', tintsoft='#3a2a28', coach='#5fd0c4', oncoach='#05221f', coachink='#7fdcd2', coachsoft='#123a40', closer='#f2c45f', closersoft='#3b3012'),
  light=dict(canvas='#f4f6f9', surface='#ffffff', sunken='#e8ecf2', ink='#0e1726', ink2='#4f5b6b', tint='#dc6b25', ontint='#2a1404', tintink='#a94d15', tintsoft='#fde5d3', coach='#0c6b66', oncoach='#ffffff', coachink='#0c6b66', coachsoft='#dcefee', closer='#8a5a00', closersoft='#fbeccb'),
  scene=dict(hi='#0f2340', lo='#f59a62', sun='#ffd6a0', far='#2c3b57', near='#121d30', plate='rgba(10,18,32,.62)'),
  swatches=[('Evening sky', '#0d1b2e'), ('Apricot light', '#ffa25c'), ('Tide (Coach)', '#5fd0c4'), ('Cloud white', '#f4f6f9'), ('Deep apricot (light theme)', '#dc6b25')],
  score=[('Looks like Claude?', 'Low', 'good'), ('Apple principles', 'High: Weather, TV', 'good'), ('Feels Bangladeshi', 'Low to medium', 'mid'), ('Calm for nervous learners', 'Highest', 'good')],
  risk='Dark navy is common in meditation apps (Calm, Headspace). The companion and the waveform motif have to do the work of making it Bolo.'),
 dict(id='b', name='Shapla', tag='Most local', line='Rooted in Bangladesh: water-lily pink and monsoon green, with a rounded, Bangla-first typeface.',
  story='Shapla is the national flower, and monsoon green is the colour of home. It feels warm, friendly and proudly local, as if the Buddy grew up in Dhaka.',
  fonts='Baloo Da 2 (Bangla + Latin)', fontEn="'Baloo Da 2'", fontBn="'Baloo Da 2'", weightTitle=700, radius='26px', btnRadius='999px',
  home='light', debrief='light',
  dark=dict(canvas='#0f1a16', surface='#18261f', sunken='#22332b', ink='#eef5f1', ink2='#a9bab1', tint='#f27aa4', ontint='#2b0614', tintink='#f79bbb', tintsoft='#3d1a28', coach='#6fd0a8', oncoach='#06231a', coachink='#8fdcbb', coachsoft='#163a2e', closer='#f2c45f', closersoft='#3b3012'),
  light=dict(canvas='#f5f8f4', surface='#ffffff', sunken='#e8eee7', ink='#14211c', ink2='#4d5e56', tint='#c93a6e', ontint='#ffffff', tintink='#a8285a', tintsoft='#fbe1ea', coach='#0e5a45', oncoach='#ffffff', coachink='#0e5a45', coachsoft='#dcede5', closer='#8a5a00', closersoft='#fbeccb'),
  scene=dict(hi='#1f4a45', lo='#f2a7b8', sun='#ffe6c8', far='#2f5a48', near='#173a2f', plate='rgba(12,30,24,.6)'),
  swatches=[('Shapla pink', '#c93a6e'), ('Monsoon green', '#0e5a45'), ('Paddy mist', '#f5f8f4'), ('Lily blush', '#fbe1ea'), ('Dusk pond', '#1f4a45')],
  score=[('Looks like Claude?', 'Very low', 'good'), ('Apple principles', 'Medium: playful', 'mid'), ('Feels Bangladeshi', 'High', 'good'), ('Calm for nervous learners', 'Medium to high', 'mid')],
  risk='Rounded type and pink can drift toward a children\'s app, and pink may read as gendered to a Rafi or Kamrul. It needs disciplined use.'),
 dict(id='c', name='Marigold', tag='Most Apple', line='Apple-level restraint: true black, white type, one marigold tint and a leaf-green Coach, like a festival garland.',
  story='Marigold and mango leaf make the garland at every Bangladeshi celebration. This option executes it like Apple Fitness and Apple Watch: a black canvas, bold type and one vivid colour.',
  fonts='Onest + Noto Sans Bengali', fontEn="'Onest'", fontBn="'Noto Sans Bengali'", weightTitle=750, radius='20px', btnRadius='14px',
  home='dark', debrief='light',
  dark=dict(canvas='#0b0b0c', surface='#1c1c1e', sunken='#2c2c2e', ink='#ffffff', ink2='#a1a1a6', tint='#ffb81c', ontint='#1a1200', tintink='#ffc94d', tintsoft='#3a2c0a', coach='#5fd39a', oncoach='#06210f', coachink='#7ee0ae', coachsoft='#12321f', closer='#ffc94d', closersoft='#3a2c0a'),
  light=dict(canvas='#ffffff', surface='#f5f5f7', sunken='#e8e8ed', ink='#1d1d1f', ink2='#6e6e73', tint='#ffb81c', ontint='#1a1200', tintink='#8a5a00', tintsoft='#fff1cc', coach='#1f7a4d', oncoach='#ffffff', coachink='#1f7a4d', coachsoft='#e1f2e8', closer='#8a5a00', closersoft='#fff1cc'),
  scene=dict(hi='#050507', lo='#3a2a12', sun='#ffb81c', far='#1c1c1e', near='#0b0b0c', plate='rgba(0,0,0,.6)'),
  swatches=[('Night', '#0b0b0c'), ('Marigold', '#ffb81c'), ('Mango leaf', '#5fd39a'), ('White', '#ffffff'), ('Graphite', '#6e6e73')],
  score=[('Looks like Claude?', 'Very low', 'good'), ('Apple principles', 'Highest: Fitness, Watch', 'good'), ('Feels Bangladeshi', 'Medium', 'mid'), ('Calm for nervous learners', 'Medium: energetic', 'mid')],
  risk='High contrast and black feel like "performance". That suits a fitness app but can add pressure for anxious speakers. In light mode the marigold fill also needs an edge to stand off white (1.7:1).'),
]

def vars_for(o, theme):
    t = o[theme]; s = o['scene']
    v = ';'.join(f'--{k}:{val}' for k, val in t.items())
    v += f";--sky-hi:{s['hi']};--sky-lo:{s['lo']};--scene-sun:{s['sun']};--far:{s['far']};--near:{s['near']};--plate:{s['plate']}"
    v += f";--fen:{o['fontEn']}, system-ui, sans-serif;--fbn:{o['fontBn']}, sans-serif;--r:{o['radius']};--br:{o['btnRadius']};--tw:{o['weightTitle']}"
    return v

def home(o):
    return f'''<div class="phone" style="{vars_for(o, o['home'])}">
<div class="sb"><span>9:41</span><span>{ic('signal_cellular_alt','sm')}{ic('battery_5_bar','sm')}</span></div>
<div class="pad col">
 <div class="row between"><span class="sub">Good evening, Rafi</span><span class="streak">{ic('wb_sunny','sm fill')}6</span></div>
 <div class="scene card-scene">{SCENE}<div class="cap small">Sam wants to know if the payment fix went live.</div></div>
 <div class="bn">আজ পাঁচ মিনিট কথা বলি?</div>
 <div class="grow"></div>
 <div class="homecard"><span class="hc-ic">{ic('forum','fill')}</span><div class="grow"><div class="hc-t">Finish your feedback</div><div class="hc-s">2 fixes left</div></div>{ic('chevron_right','dim')}</div>
 <div class="talkwrap"><button class="talk">{ic('mic','fill lg')}<span>Talk</span></button><div class="meta">Free talk · 5 min · <b>Change</b></div></div>
</div>
<nav class="tabs"><span class="tab on"><i>{ic('graphic_eq','fill')}</i>Talk</span><span class="tab"><i>{ic('insights')}</i>Progress</span><span class="tab"><i>{ic('person')}</i>Me</span></nav>
</div>'''

def convo(o):
    return f'''<div class="phone scene" style="{vars_for(o, 'dark')}">{SCENE_TALL}
<div class="sb onscene"><span>9:41</span><span>{ic('signal_cellular_alt','sm')}{ic('battery_5_bar','sm')}</span></div>
<div class="pad top"><div class="row between"><span class="sbtn">{ic('landscape','sm')}</span><span class="sbtn">End</span></div>
<div class="cap">Oh nice. What was the bug doing before you fixed it?</div></div>
<div class="micbar"><span class="sbtn">{ic('pause','fill')}</span><div class="mic"><svg viewBox="0 0 96 96"><circle cx="48" cy="48" r="45" class="trk"/><circle cx="48" cy="48" r="45" class="lft" stroke-dasharray="165 283"/></svg><div class="core">{ic('mic','fill lg')}</div></div><span class="sbtn">Done</span></div>
</div>'''

def fix(o):
    return f'''<div class="phone" style="{vars_for(o, o['debrief'])}">
<div class="sb"><span>9:47</span><span>{ic('signal_cellular_alt','sm')}{ic('battery_5_bar','sm')}</span></div>
<div class="pad row between"><span class="coachey"><b>C</b>Your coach</span>{ic('close','dim')}</div>
<div class="fix">
 <div class="row between"><span class="dots"><i class="on"></i><i></i><i></i></span><span class="eyebrow">Sound fix</span></div>
 <div class="ftitle">The v sound</div>
 <div><div class="lbl">You said</div><div class="clip"><span class="play">{ic('play_arrow','fill')}</span><span class="wv">{wave(22,11)}</span><span class="tm">0:02</span></div><div class="sent">"It's <span class="said">bhery</span> important."</div></div>
 <div><div class="lbl">Try</div><div class="clip model"><span class="play">{ic('pause','fill')}</span><span class="wv">{wave(22,11,.45)}</span><span class="tm">0:02</span></div><div class="sent">"It's <span class="better">very</span> important."</div></div>
 <div class="tip">{ic('sentiment_satisfied')}<span class="grow">Top teeth touch your lower lip for v.</span><b class="bnl">বাংলায়</b></div>
 <div class="grow"></div>
 <div class="result">{ic('north_east','sm')}Closer</div>
 <button class="btn">{ic('mic','fill')}Try again</button>
 <div class="quiet">That's not what I said</div>
</div></div>'''

def section(o, i):
    sw = ''.join(f'<div class="sw"><span style="background:{c}"></span><b>{n}</b><code>{c}</code></div>' for n, c in o['swatches'])
    sc = ''.join(f'<div class="sc"><span>{a}</span><b class="{k}">{b}</b></div>' for a, b, k in o['score'])
    spec = f'''<div class="spec" style="--fen:{o['fontEn']}, sans-serif;--fbn:{o['fontBn']}, sans-serif"><div class="spec-en">Speak first.<br>Get better after.</div><div class="spec-bn">আগে বলুন, পরে শিখুন।</div><div class="spec-f">{o['fonts']}</div></div>'''
    return f'''<section class="opt" id="option-{o['id']}">
<header class="oh"><div><div class="num">Option {'ABC'[i]}</div><h2>{o['name']}</h2><p class="line">{o['line']}</p></div><span class="badge">{o['tag']}</span></header>
<div class="ogrid">
 <div class="left"><p class="story">{o['story']}</p><div class="sws">{sw}</div>{spec}<div class="scs">{sc}</div><p class="risk"><b>Watch out:</b> {o['risk']}</p></div>
 <div class="phones"><figure>{home(o)}<figcaption>Home</figcaption></figure><figure>{convo(o)}<figcaption>Conversation</figcaption></figure><figure>{fix(o)}<figcaption>Debrief</figcaption></figure></div>
</div></section>'''

CSS = r'''
*{box-sizing:border-box}html,body{margin:0}
body{background:#fafafa;color:#1d1d1f;font:16px/1.5 'Instrument Sans',system-ui,sans-serif;-webkit-font-smoothing:antialiased}
.wrap{max-width:1240px;margin:0 auto;padding:56px 32px 96px}
.intro h1{font-size:44px;line-height:1.05;letter-spacing:-.03em;margin:0 0 12px;font-weight:700}
.intro p{max-width:720px;color:#55555b;margin:0 0 8px;font-size:17px}
.jump{display:flex;gap:8px;margin:24px 0 8px;flex-wrap:wrap}.jump a{color:#1d1d1f;text-decoration:none;border:1px solid #d2d2d7;border-radius:999px;padding:8px 16px;font-weight:600;font-size:14px;background:#fff}
.keep{margin:20px 0 0;padding:16px 20px;background:#fff;border:1px solid #e5e5ea;border-radius:16px;font-size:15px;color:#3a3a3f;max-width:900px}
.opt{margin-top:64px;padding-top:40px;border-top:1px solid #e5e5ea}
.oh{display:flex;justify-content:space-between;align-items:flex-start;gap:24px}
.num{font-size:13px;font-weight:650;color:#86868b;letter-spacing:.02em}
.oh h2{font-size:40px;letter-spacing:-.03em;margin:2px 0 6px;line-height:1.05}
.line{margin:0;color:#3a3a3f;font-size:18px;max-width:760px}
.badge{flex:none;font-size:13px;font-weight:650;border-radius:999px;padding:6px 14px;background:#1d1d1f;color:#fff}
.ogrid{display:grid;grid-template-columns:280px 1fr;gap:32px;margin-top:28px;align-items:start}
.story{margin:0 0 20px;color:#55555b;font-size:15px}
.sws{display:grid;gap:8px;margin-bottom:20px}.sw{display:grid;grid-template-columns:28px 1fr auto;gap:10px;align-items:center;font-size:14px}.sw span{width:28px;height:28px;border-radius:8px;box-shadow:inset 0 0 0 1px rgba(0,0,0,.08)}.sw code{font-size:12px;color:#86868b}
.spec{background:#fff;border:1px solid #e5e5ea;border-radius:16px;padding:16px 18px;margin-bottom:20px}.spec-en{font-family:var(--fen);font-size:26px;line-height:1.1;font-weight:700;letter-spacing:-.02em}.spec-bn{font-family:var(--fbn);font-size:20px;line-height:1.5;margin-top:8px;font-weight:600}.spec-f{font-size:12px;color:#86868b;margin-top:8px;font-weight:600}
.scs{display:grid;gap:6px;margin-bottom:16px}.sc{display:flex;justify-content:space-between;gap:12px;font-size:14px;padding:6px 0;border-bottom:1px solid #ececf0}.sc span{color:#55555b}.sc b.good{color:#1f7340}.sc b.mid{color:#8a5a00}
.risk{font-size:14px;color:#55555b;margin:0}
.phones{display:flex;gap:12px;flex-wrap:wrap}
figure{margin:0}figcaption{text-align:center;font-size:13px;font-weight:600;color:#86868b;margin-top:10px}
.ms{font-family:'Material Symbols Rounded';font-size:20px;line-height:1;font-variation-settings:'FILL' 0,'wght' 500,'opsz' 24;display:inline-block}
.ms.fill{font-variation-settings:'FILL' 1,'wght' 500,'opsz' 24}.ms.sm{font-size:16px}.ms.lg{font-size:30px}.ms.dim{color:var(--ink2)}
.phone{width:262px;height:568px;border-radius:40px;overflow:hidden;position:relative;background:var(--canvas);color:var(--ink);font-family:var(--fen);box-shadow:0 0 0 8px #111,0 24px 48px rgba(0,0,0,.18);display:flex;flex-direction:column;margin:8px}
.sb{height:34px;flex:none;display:flex;justify-content:space-between;align-items:center;padding:0 22px;font-size:12px;font-weight:650}.sb span{display:flex;gap:2px;align-items:center}
.pad{padding:0 16px}.col{flex:1;display:flex;flex-direction:column;gap:10px}.row{display:flex;align-items:center;gap:8px}.between{justify-content:space-between}.grow{flex:1;min-width:0}
.sub{font-size:13px;color:var(--ink2);font-weight:500}
.streak{display:inline-flex;align-items:center;gap:3px;font-size:12px;font-weight:700;color:var(--tintink);background:var(--tintsoft);border-radius:999px;padding:3px 9px}
.scene{position:relative;overflow:hidden;background:linear-gradient(180deg,var(--sky-hi) 0%,var(--sky-hi) 18%,var(--sky-lo) 66%,var(--sky-lo) 100%);color:#fff8ef}
.scene .art{position:absolute;inset:0;width:100%;height:100%}.art .sun{fill:var(--scene-sun)}.art .far{fill:var(--far)}.art .near{fill:var(--near)}.art .rim{fill:none;stroke:var(--scene-sun);stroke-width:1.5;opacity:.8}
.card-scene{height:148px;border-radius:var(--r);display:flex;align-items:flex-end;padding:10px}
.cap{position:relative;background:var(--plate);-webkit-backdrop-filter:blur(14px);backdrop-filter:blur(14px);border-radius:calc(var(--r) - 4px);padding:12px 14px;font-size:17px;line-height:24px;font-weight:500;color:#fff8ef}
.cap.small{font-size:13px;line-height:18px;padding:8px 12px}
.bn{font-family:var(--fbn);font-size:13px;color:var(--ink2)}
.homecard{display:flex;align-items:center;gap:10px;background:var(--surface);border-radius:calc(var(--r) - 6px);padding:10px 12px;box-shadow:0 1px 2px rgba(0,0,0,.05),0 4px 14px rgba(0,0,0,.05)}
.hc-ic{width:32px;height:32px;border-radius:999px;background:var(--coachsoft);color:var(--coachink);display:flex;align-items:center;justify-content:center}.hc-t{font-size:14px;font-weight:650}.hc-s{font-size:12px;color:var(--ink2)}
.talkwrap{display:flex;flex-direction:column;align-items:center;gap:8px;padding:10px 0 10px}
.talk{width:96px;height:96px;border-radius:999px;border:0;background:var(--tint);color:var(--ontint);display:flex;flex-direction:column;align-items:center;justify-content:center;font:700 19px/1 var(--fen);gap:2px;box-shadow:0 10px 30px color-mix(in srgb,var(--tint) 45%,transparent)}
.meta{font-size:12px;color:var(--ink2)}.meta b{color:var(--tintink)}
.tabs{height:54px;flex:none;display:grid;grid-template-columns:repeat(3,1fr);border-top:1px solid color-mix(in srgb,var(--ink) 10%,transparent);background:color-mix(in srgb,var(--canvas) 88%,transparent)}
.tab{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:1px;font-size:10px;font-weight:650;color:var(--ink2)}.tab i{font-style:normal;width:44px;height:24px;border-radius:999px;display:flex;align-items:center;justify-content:center}.tab.on{color:var(--ink)}.tab.on i{background:var(--sunken)}
.onscene{position:absolute;inset:0 0 auto;z-index:2;color:#fff8ef}
.top{position:relative;z-index:2;padding-top:40px;display:flex;flex-direction:column;gap:12px}
.sbtn{height:38px;min-width:38px;padding:0 12px;border-radius:999px;background:rgba(255,248,239,.16);color:#fff8ef;display:inline-flex;align-items:center;justify-content:center;font-size:13px;font-weight:650}
.micbar{position:absolute;left:0;right:0;bottom:28px;z-index:2;display:flex;align-items:center;justify-content:space-between;padding:0 26px}
.mic{position:relative;width:84px;height:84px;display:flex;align-items:center;justify-content:center}.mic svg{position:absolute;inset:0;transform:rotate(-90deg)}.mic .trk{fill:none;stroke:rgba(255,248,239,.18);stroke-width:3}.mic .lft{fill:none;stroke:rgba(255,248,239,.75);stroke-width:3;stroke-linecap:round}
.mic .core{width:62px;height:62px;border-radius:999px;background:var(--tint);color:var(--ontint);display:flex;align-items:center;justify-content:center;box-shadow:0 0 0 10px color-mix(in srgb,var(--tint) 25%,transparent),0 8px 28px color-mix(in srgb,var(--tint) 50%,transparent)}
.coachey{display:inline-flex;align-items:center;gap:6px;font-size:12px;font-weight:650;color:var(--coachink)}.coachey b{width:20px;height:20px;border-radius:999px;background:var(--coach);color:var(--oncoach);display:inline-flex;align-items:center;justify-content:center;font-size:10px}
.fix{flex:1;margin:8px 12px 16px;background:var(--surface);border-radius:calc(var(--r) + 4px);padding:14px;display:flex;flex-direction:column;gap:10px;box-shadow:0 1px 2px rgba(0,0,0,.05),0 6px 18px rgba(0,0,0,.05)}
.dots{display:inline-flex;gap:4px}.dots i{width:6px;height:6px;border-radius:9px;background:var(--ink2);opacity:.35}.dots i.on{width:18px;background:var(--ink);opacity:1}
.eyebrow{font-size:11px;font-weight:650;color:var(--coachink)}
.ftitle{font-size:19px;font-weight:var(--tw);letter-spacing:-.015em}
.lbl{font-size:10px;font-weight:650;color:var(--ink2);margin-bottom:4px}
.clip{display:flex;align-items:center;gap:8px;background:var(--sunken);border-radius:var(--br);padding:3px 10px 3px 3px}
.play{width:30px;height:30px;border-radius:999px;background:var(--surface);color:var(--ink);display:flex;align-items:center;justify-content:center;flex:none}
.clip.model .play{background:var(--coach);color:var(--oncoach)}
.wv{flex:1;display:flex;align-items:center;gap:2px;height:24px;overflow:hidden}.wv i{width:2.5px;border-radius:9px;background:var(--ink2);opacity:.45;flex:none}.wv i.on{opacity:1}.clip.model .wv i{background:var(--coach)}
.tm{font-size:10px;font-weight:600;color:var(--ink2)}
.sent{font-size:15px;line-height:22px;font-weight:500;margin-top:6px}
.said{background:var(--tintsoft);color:var(--tintink);border-radius:5px;padding:0 3px;font-weight:700}.better{background:var(--coachsoft);color:var(--coachink);border-radius:5px;padding:0 3px;font-weight:650}
.tip{display:flex;gap:8px;align-items:flex-start;background:var(--coachsoft);border-radius:12px;padding:8px 10px;font-size:13px;line-height:18px;font-weight:500}.tip .ms{color:var(--coachink)}.bnl{font-family:var(--fbn);font-size:12px;color:var(--coachink)}
.result{align-self:center;display:inline-flex;align-items:center;gap:4px;font-size:13px;font-weight:650;color:var(--closer);background:var(--closersoft);border-radius:999px;padding:6px 12px}
.btn{height:46px;border:0;border-radius:var(--br);background:var(--tint);color:var(--ontint);font:650 15px/1 var(--fen);display:flex;align-items:center;justify-content:center;gap:6px}
.quiet{text-align:center;font-size:12px;font-weight:600;color:var(--ink2)}
#option-c .fix .btn{box-shadow:inset 0 0 0 1px rgba(0,0,0,.18)}
.verdict{margin-top:72px;padding-top:40px;border-top:1px solid #e5e5ea}.verdict h2{font-size:32px;letter-spacing:-.02em;margin:0 0 16px}
table{border-collapse:collapse;width:100%;font-size:15px;background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 0 0 1px #e5e5ea}th,td{text-align:left;padding:12px 16px;border-bottom:1px solid #f0f0f3;vertical-align:top}th{font-size:13px;color:#86868b;font-weight:650}
@media (max-width:980px){.ogrid{grid-template-columns:1fr}}
'''

page = f'''<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Bolo: three visual directions</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Instrument+Sans:wght@400..700&family=Hind+Siliguri:wght@400;500;600;700&family=Baloo+Da+2:wght@400..800&family=Onest:wght@400..800&family=Noto+Sans+Bengali:wght@400..700&family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@20..48,400..600,0..1,0&display=swap">
<style>{CSS}</style></head>
<body><div class="wrap">
<div class="intro"><h1>Bolo: three visual directions</h1>
<p>Each option shows the same three moments (Home, the conversation and a Coach fix), so you compare the feeling, not the layout. Layout, components and UX rules stay the same whichever you pick; only colour, type and shape change.</p>
<div class="jump"><a href="#option-a">A · Dusk</a><a href="#option-b">B · Shapla</a><a href="#option-c">C · Marigold</a><a href="#verdict">Side by side</a></div>
<p class="keep"><b>Kept in every option:</b> one colour for actions, a separate Coach colour, no red in feedback, the learner's own clip in neutral grey, the companion beside you, the waveform motif, and every text colour at WCAG AA contrast or better.</p></div>
{''.join(section(o, i) for i, o in enumerate(OPTS))}
<section class="verdict" id="verdict"><h2>Side by side</h2>
<table><tr><th></th><th>A · Dusk</th><th>B · Shapla</th><th>C · Marigold</th></tr>
<tr><td><b>One-line feel</b></td><td>Cinematic calm at sunset</td><td>Warm, local, friendly</td><td>Bold, premium, focused</td></tr>
<tr><td><b>Looks like Claude?</b></td><td>Low</td><td>Very low</td><td>Very low</td></tr>
<tr><td><b>Apple influence</b></td><td>High: Weather, TV</td><td>Medium: Apple principles, playful look</td><td>Highest: Fitness, Watch</td></tr>
<tr><td><b>Feels Bangladeshi</b></td><td>Low to medium</td><td>High</td><td>Medium (the garland story)</td></tr>
<tr><td><b>Calm for an anxious speaker</b></td><td>Highest</td><td>Medium to high</td><td>Medium</td></tr>
<tr><td><b>Fits the PRD's scenes</b></td><td>Exactly (sunset, drive)</td><td>Needs a pond or paddy-field scene</td><td>Needs a night scene</td></tr>
<tr><td><b>Biggest risk</b></td><td>Looks like a meditation app</td><td>Drifts toward a children's app</td><td>Feels like performance pressure</td></tr>
<tr><td><b>Change from today</b></td><td>Small: palette and type</td><td>Large: palette, type and scenes</td><td>Medium: palette, type and shape</td></tr>
</table></section>
</div></body></html>'''
open(OUT, 'w').write(page)
print('ok', len(page))
