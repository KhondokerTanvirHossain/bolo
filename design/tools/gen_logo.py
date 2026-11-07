import sys
OUT = sys.argv[1]
IND, SUN, HILL, ROSE, NEAR, CREAM = '#1D1236', '#F4B942', '#5B3F8C', '#C98AA3', '#140A28', '#FFF8EF'
AURA = f'<defs><radialGradient id="AU{{k}}"><stop offset="0%" stop-color="{SUN}" stop-opacity=".55"/><stop offset="100%" stop-color="{SUN}" stop-opacity="0"/></radialGradient></defs>'
def bust(x, y, sc, fill):
    # one person, seen from behind: head joined to rounded shoulders
    return (f'<g transform="translate({x} {y}) scale({sc})" fill="{fill}"><circle cx="0" cy="-11" r="6.2"/>'
            f'<path d="M-11 9 C-11 -1 -6 -5.5 0 -5.5 C6 -5.5 11 -1 11 9 Z"/></g>')
def icon1(mono=False, k='a'):
    bg = 'var(--mbg)' if mono else IND; sun = 'var(--mfg)' if mono else SUN
    hill = 'var(--mfg)' if mono else HILL; fig = 'var(--mbg)' if mono else NEAR; near = 'var(--mfg)' if mono else NEAR
    aura = '' if mono else AURA.replace('{k}', k) + f'<circle cx="54" cy="48" r="42" fill="url(#AU{k})"/>'
    star = '' if mono else f'<path d="M78 26 Q78 29 81 29 Q78 29 78 32 Q78 29 75 29 Q78 29 78 26Z" fill="{CREAM}" opacity=".9"/>'
    ground = '' if mono else f'<path d="M0 82 Q54 77 108 83 V108 H0Z" fill="{near}"/>'
    return (f'<svg viewBox="0 0 108 108"><rect width="108" height="108" fill="{bg}"/>{aura}{star}'
            f'<circle cx="54" cy="48" r="25" fill="{sun}"/>'
            f'<path d="M0 72 Q54 63 108 72 V108 H0Z" fill="{hill}"/>'
            f'{bust(45, 75, 1.0, fig)}{bust(63.5, 74, 1.1, fig)}{ground}</svg>')
def icon2(mono=False, k='b'):
    fg = 'var(--mfg)' if mono else SUN; bar = 'var(--mfg)' if mono else CREAM; bg = 'var(--mbg)' if mono else IND
    return (f'<svg viewBox="0 0 108 108"><rect width="108" height="108" fill="{bg}"/>'
            f'<text x="54" y="84" text-anchor="middle" font-family="Anek Bangla" font-weight="800" font-size="76" fill="{fg}">b</text>'
            f'<rect x="0" y="0" width="108" height="43" fill="{bg}"/>'
            f'<rect x="22" y="43" width="64" height="7" rx="2" fill="{bar}"/></svg>')
def icon3(mono=False, k='c'):
    fg = 'var(--mfg)' if mono else SUN; bg = 'var(--mbg)' if mono else IND; hill = 'var(--mfg)' if mono else HILL
    aura = '' if mono else AURA.replace('{k}', k) + f'<circle cx="56" cy="50" r="40" fill="url(#AU{k})"/>'
    star = '' if mono else f'<path d="M30 28 Q30 31 33 31 Q30 31 30 34 Q30 31 27 31 Q30 31 30 28Z" fill="{CREAM}" opacity=".9"/>'
    return (f'<svg viewBox="0 0 108 108"><rect width="108" height="108" fill="{bg}"/>{aura}{star}'
            f'<path d="M56 26 A24 24 0 1 1 41.5 69.1 L33 75 L36.6 64.6 A24 24 0 0 1 56 26Z" fill="{fg}"/>'
            f'<path d="M0 80 Q54 70 108 80 V108 H0Z" fill="{hill}"/></svg>')

def mark1(c):  # Bol + sun
    return f'<span class="wm">Bol<span class="sundot" style="--c:{c}"></span></span>'
def mark2(c, bg='#f7f4fb', ink='#1f1433'):
    if c == SUN: bg, ink = IND, '#fbf5ff'
    return (f'<svg viewBox="0 0 220 90" style="height:96px"><text x="110" y="78" text-anchor="middle" font-family="Anek Bangla" font-weight="750" font-size="96" letter-spacing="-3" fill="{ink}">bolo</text>'
            f'<rect x="0" y="0" width="220" height="27" fill="{bg}"/><rect x="14" y="27" width="192" height="8" rx="2" fill="{c}"/></svg>')
def mark3(c):
    return f'<span class="wm">B<span class="bubble" style="--c:{c}"><svg viewBox="0 0 52 52"><path d="M28 2 A24 24 0 1 1 13.5 45.1 L4 51 L8.6 40.6 A24 24 0 0 1 28 2Z" fill="{SUN}"/></svg></span>lo</span>'

C = [
 dict(id='c1', n='Idea 1', name='Side by side', icon=icon1, mark=mark1,
  idea='Two friends seen from behind, sitting on a hill and watching the sun go down. It is Bolo\'s hilltop scene reduced to three shapes. In the wordmark, the last "o" of Bolo becomes the mustard sun: your voice is the sun.',
  good=['Tells the product\'s story in one glance: someone beside you, no one facing you', 'Matches the app you open into, so the icon and the first screen feel like one place', 'Nothing like it among language or messaging apps on a Bangladeshi phone'],
  risk='The two heads are small. At 48dp they still read as two bumps against the sun, but the idea needs the bigger sizes to be fully understood.'),
 dict(id='c2', n='Idea 2', name='মাত্রা · the matra', icon=icon2, mark=mark2,
  idea='English letters hanging from a Bangla headline stroke (মাত্রা), the line every Bangla word hangs from. It says "English, in a Bangla speaker\'s voice", which is exactly who Bolo is for. As in Bangla, the tall strokes of b and l stop at the line, so every letter hangs from it. The icon is a mustard "b" hanging from the line.',
  good=['Instantly local and proud: no imported-app feeling', 'Strongest as type: works on a business card, a T-shirt, a splash screen', 'A single shape, so it stays crisp at every size and in one colour'],
  risk='It tells nothing about talking or the Buddy. With the tall strokes cut, "bolo" is a little harder to read for anyone not used to Bangla, and the lowercase wordmark breaks the capital "Bolo" used everywhere else.'),
 dict(id='c3', n='Idea 3', name='The talking sun', icon=icon3, mark=mark3,
  idea='The setting sun with a small speech-bubble tail, rising over the violet hill. Talk and dusk in one shape. The first "o" of the wordmark becomes the sun-bubble.',
  good=['The clearest "speaking" signal of the three', 'Bold and simple: reads best at the smallest sizes', 'Easy to animate: the bubble can pulse when it\'s your turn'],
  risk='Speech bubbles are what WhatsApp, Messenger and imo use. On a crowded home screen it may look like another chat app.'),
]
def section(c):
    good = ''.join(f'<li>{g}</li>' for g in c['good'])
    ic = c['icon']
    return f'''<section class="opt" id="{c['id']}">
<div class="num">{c['n']}</div><h2>{c['name']}</h2>
<p class="story">{c['idea']}</p>
<div class="row">
  <figure><div class="ic big sq">{ic(k=c['id']+'1')}</div><figcaption>App icon</figcaption></figure>
  <figure><div class="ic mid circ">{ic(k=c['id']+'2')}</div><figcaption>Android, round mask</figcaption></figure>
  <figure><div class="ic s48 sq">{ic(k=c['id']+'3')}</div><figcaption>48dp, real size</figcaption></figure>
  <figure><div class="ic s48 circ mono">{ic(mono=True, k=c['id']+'4')}</div><figcaption>Themed icon</figcaption></figure>
  <figure><div class="ic s48 circ mono mono2">{ic(mono=True, k=c['id']+'5')}</div><figcaption>Themed, dark</figcaption></figure>
</div>
<div class="marks"><div class="mk day">{c['mark']('#9A6A0A')}</div><div class="mk night">{c['mark'](SUN)}</div></div>
<div class="pros"><ul>{good}</ul><p class="risk"><b>Watch out:</b> {c['risk']}</p></div>
</section>'''

css = f'''
*{{box-sizing:border-box}} body{{margin:0;background:#f7f4fb;color:#1f1433;font-family:'Anek Bangla',system-ui,sans-serif;-webkit-font-smoothing:antialiased}}
.wrap{{max-width:1040px;margin:0 auto;padding:48px 32px 80px}}
h1{{font-size:40px;line-height:44px;font-weight:750;letter-spacing:-.02em;margin:0 0 12px}}
.lead{{font-size:18px;line-height:28px;color:#5a4e6e;max-width:720px;margin:0 0 8px}}
.opt{{background:#fff;border-radius:28px;padding:36px 40px;margin-top:32px;box-shadow:0 1px 2px rgba(31,20,51,.06),0 8px 28px rgba(31,20,51,.06)}}
.num{{font-size:13px;font-weight:650;color:#5b3f8c}} h2{{font-size:30px;line-height:36px;font-weight:700;margin:2px 0 10px}}
.story{{font-size:17px;line-height:26px;color:#3a3048;max-width:760px;margin:0 0 28px}}
.row{{display:flex;flex-wrap:wrap;gap:28px;align-items:flex-end}} figure{{margin:0;display:flex;flex-direction:column;align-items:center;gap:10px}}
figcaption{{font-size:12px;font-weight:650;color:#5a4e6e}}
.ic{{overflow:hidden;line-height:0}} .ic svg{{width:100%;height:100%;display:block}}
.big{{width:168px;height:168px}} .mid{{width:96px;height:96px}} .s48{{width:48px;height:48px}}
.sq{{border-radius:24%}} .big.sq{{border-radius:40px}} .circ{{border-radius:50%}}
.mono{{--mbg:#e8def8;--mfg:#4a3a6e}} .mono2{{--mbg:#3a2f52;--mfg:#e6dcf5}}
.marks{{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-top:28px}}
.mk{{border-radius:20px;height:150px;display:flex;align-items:center;justify-content:center}}
.day{{background:#f7f4fb;color:#1f1433}} .night{{background:#1d1236;color:#fbf5ff}}
.wm{{font-family:'Anek Bangla';font-weight:750;font-size:84px;line-height:1;letter-spacing:-.03em;display:inline-flex;align-items:baseline;position:relative}}
.sundot{{display:inline-block;width:.5em;height:.5em;border-radius:50%;background:{SUN};box-shadow:inset 0 0 0 .02em var(--c);margin-left:.04em}}
.bubble{{display:inline-block;width:.54em;height:.54em;margin:0 .03em 0 .02em}} .bubble svg{{width:100%;height:100%;display:block}}
.wm--matra{{padding-top:.02em}} .wm--matra::before{{content:"";position:absolute;left:-.06em;right:-.06em;top:.30em;height:.075em;border-radius:.04em;background:var(--bar)}}
.pros{{display:grid;grid-template-columns:1.2fr 1fr;gap:28px;margin-top:24px}}
.pros ul{{margin:0;padding-left:18px;font-size:15px;line-height:22px;color:#3a3048}} .pros li{{margin-bottom:6px}}
.risk{{margin:0;font-size:15px;line-height:22px;color:#5a4e6e;background:#f7f4fb;border-radius:14px;padding:14px 16px}}
.verdict{{margin-top:40px;background:#1d1236;color:#fbf5ff;border-radius:28px;padding:36px 40px}}
.verdict h2{{color:#fbf5ff}} .verdict p{{font-size:17px;line-height:26px;color:#e3d8ca;max-width:780px}} .verdict b{{color:#f4b942}}
.home{{display:flex;gap:22px;margin-top:20px;padding:20px 24px;border-radius:20px;background:linear-gradient(160deg,#3a4a6e,#20283e);width:fit-content}}
.home figure figcaption{{color:#eef;font-weight:500}}
.app{{width:48px;height:48px;border-radius:50%;overflow:hidden}}
.fake{{width:48px;height:48px;border-radius:50%}}
'''
home = (f'<div class="home">'
        f'<figure><div class="fake" style="background:#25d366"></div><figcaption>Chat</figcaption></figure>'
        f'<figure><div class="app ic">{icon1(k="h1")}</div><figcaption>Idea 1</figcaption></figure>'
        f'<figure><div class="app ic">{icon2(k="h2")}</div><figcaption>Idea 2</figcaption></figure>'
        f'<figure><div class="app ic">{icon3(k="h3")}</div><figcaption>Idea 3</figcaption></figure>'
        f'<figure><div class="fake" style="background:#e2136e"></div><figcaption>Wallet</figcaption></figure>'
        f'<figure><div class="fake" style="background:#0866ff"></div><figcaption>Social</figcaption></figure></div>')
page = f'''<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Bolo: three logo ideas</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Anek+Bangla:wdth,wght@75..125,100..800&display=swap">
<style>{css}</style></head>
<body><div class="wrap">
<h1>Bolo: three logo ideas</h1>
<p class="lead">Each idea is shown as the app icon, in Android's round mask, at its real 48dp size, as Android's single-colour themed icon, and as the wordmark by day and by night. Colours are the Neon Dusk palette; the wordmark is Anek Bangla 750, always in English letters.</p>
{''.join(section(c) for c in C)}
<section class="verdict"><h2>On a real home screen</h2>
<p>All three next to the kind of icons they will sit beside. Plain coloured circles stand in for other apps.</p>
{home}
<h2 style="margin-top:36px">My pick: Idea 1, Side by side</h2>
<p>It is the only mark that carries Bolo's whole promise: <b>someone beside you, no one judging you</b>. It matches the scene the app opens into, so the icon, the splash and the first conversation feel like one place. Its weakness, small figures at 48dp, is real but small; the sun and the hill carry the icon at that size. Idea 2 is the strongest piece of type and could live on as a secondary mark for merchandise and Bangladesh campaigns. Idea 3 reads fastest, but risks looking like one more chat app.</p>
</section>
</div></body></html>'''
open(OUT, 'w').write(page); print('ok', len(page))
