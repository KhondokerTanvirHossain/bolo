import sys, runpy
OUT = sys.argv[1]; T = sys.argv[2]
b = runpy.run_path(T + '/buddy.py'); icon = b['icon']; body = b['body']; face = b['face']; antenna = b['antenna']; waves = b['waves']
SUN, IND, CREAM, EDGE = b['SUN'], b['IND'], b['CREAM'], b['EDGE']
def mark(v, night):
    ink = '#fbf5ff' if night else '#1f1433'; edge = None if night else EDGE
    deco = antenna(36, 42, 21, SUN, CREAM if night else '#5B3F8C') if v in ('B', 'C') else ''
    vb = '7 2 47 66' if v in ('B', 'C') else '7 19 47 49'
    cls = 'bo-o bo-o--ant' if v in ('B', 'C') else 'bo-o'
    o = f'<svg viewBox="{vb}" class="{cls}">{deco}{body(30, 42, 21, SUN, edge)}{face(30, 42, 21, "open", "talk", ink=IND)}</svg>'
    return f'<span class="wm">B{o}lo</span>'
V = [
 ('A', 'Sunny face', 'The talking sun with a face: eyes glancing up at the sky, an open mouth mid-sentence, and rose cheeks, the Buddy\'s colour. It sits on the violet hill, so it reads as dusk, not as a chat bubble.',
  'Warmest and simplest; reads clearly at 48dp', 'Without anything bot-like, it may read as a sun mascot more than a buddy'),
 ('B', 'Little bot', 'The same face with a short antenna topped by the evening star from the scene. The antenna makes it a character you can talk to; the star keeps it soft and part of the dusk world rather than a robot.',
  'Clearly a buddy, not a chat icon; the star ties the logo to the scene', 'The antenna is thin; at 48dp it shows as a small spark above the head, which is enough'),
 ('C', 'Speaking', 'The little bot with two sound waves coming from its mouth, so the icon says "it talks" even before you read the name.',
  'The most explicit "voice" signal', 'Busiest of the three; the waves crowd the round mask and add little at small sizes'),
]
def section(v, name, idea, good, risk):
    return f'''<section class="opt" id="v{v}">
<div class="num">Version {v}</div><h2>{name}</h2><p class="story">{idea}</p>
<div class="row">
  <figure><div class="ic big sq">{icon(v, k=v+'1')}</div><figcaption>App icon</figcaption></figure>
  <figure><div class="ic mid circ">{icon(v, k=v+'2')}</div><figcaption>Android, round mask</figcaption></figure>
  <figure><div class="ic s48 sq">{icon(v, k=v+'3')}</div><figcaption>48dp, real size</figcaption></figure>
  <figure><div class="ic s48 circ mono">{icon(v, mono=True, k=v+'4')}</div><figcaption>Themed icon</figcaption></figure>
  <figure><div class="ic s48 circ mono mono2">{icon(v, mono=True, k=v+'5')}</div><figcaption>Themed, dark</figcaption></figure>
</div>
<div class="marks"><div class="mk day">{mark(v, False)}</div><div class="mk night">{mark(v, True)}</div></div>
<div class="pros"><p><b>Good:</b> {good}.</p><p class="risk"><b>Watch out:</b> {risk}.</p></div>
</section>'''
EXP = [('open', 'smile', 'Idle', 'Home, waiting'), ('side', 'small', 'Listening', 'Your turn'), ('open', 'talk', 'Talking', 'Bolo speaks'),
       ('happy', 'grin', 'Happy', 'Nailed it'), ('closed', 'smile', 'Resting', 'Rest day, night')]
def exp_svg(e, m, k):
    return (f'<svg viewBox="0 0 108 108"><rect width="108" height="108" fill="{IND}"/>{antenna(54, 54, 26, SUN, CREAM)}{body(54, 54, 26, SUN)}{face(54, 54, 26, e, m)}</svg>')
exps = ''.join(f'<figure><div class="ic exp">{exp_svg(e, m, i)}</div><figcaption><b>{n}</b><br>{u}</figcaption></figure>' for i, (e, m, n, u) in enumerate(EXP))
css = '''
*{box-sizing:border-box} body{margin:0;background:#f7f4fb;color:#1f1433;font-family:'Anek Bangla',system-ui,sans-serif;-webkit-font-smoothing:antialiased}
.wrap{max-width:1040px;margin:0 auto;padding:48px 32px 80px}
h1{font-size:40px;line-height:44px;font-weight:750;letter-spacing:-.02em;margin:0 0 12px}
.lead{font-size:18px;line-height:28px;color:#5a4e6e;max-width:760px;margin:0 0 8px}
.opt{background:#fff;border-radius:28px;padding:36px 40px;margin-top:32px;box-shadow:0 1px 2px rgba(31,20,51,.06),0 8px 28px rgba(31,20,51,.06)}
.num{font-size:13px;font-weight:650;color:#5b3f8c} h2{font-size:30px;line-height:36px;font-weight:700;margin:2px 0 10px}
.story{font-size:17px;line-height:26px;color:#3a3048;max-width:760px;margin:0 0 28px}
.row{display:flex;flex-wrap:wrap;gap:28px;align-items:flex-end} figure{margin:0;display:flex;flex-direction:column;align-items:center;gap:10px}
figcaption{font-size:12px;line-height:16px;font-weight:600;color:#5a4e6e;text-align:center} figcaption b{color:#1f1433;font-size:14px}
.ic{overflow:hidden;line-height:0} .ic svg{width:100%;height:100%;display:block}
.big{width:168px;height:168px;border-radius:40px} .mid{width:96px;height:96px} .s48{width:48px;height:48px} .sq{border-radius:24%} .big.sq{border-radius:40px} .circ{border-radius:50%}
.exp{width:120px;height:120px;border-radius:28px}
.mono{--mbg:#e8def8;--mfg:#4a3a6e} .mono2{--mbg:#3a2f52;--mfg:#e6dcf5}
.marks{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-top:28px}
.mk{border-radius:20px;height:160px;display:flex;align-items:center;justify-content:center}
.day{background:#f7f4fb;color:#1f1433} .night{background:#1d1236;color:#fbf5ff}
.wm{font-family:'Anek Bangla';font-weight:750;font-size:84px;line-height:1;letter-spacing:-.03em;display:inline-flex;align-items:baseline}
.bo-o{height:.66em;width:auto;margin:0 .01em 0 .02em;transform:translateY(.1em)} .bo-o--ant{height:.89em;transform:translateY(.1em)}
.pros{display:grid;grid-template-columns:1fr 1fr;gap:20px;margin-top:20px} .pros p{margin:0;font-size:15px;line-height:22px;color:#3a3048}
.risk{color:#5a4e6e;background:#f7f4fb;border-radius:14px;padding:14px 16px}
.dark{margin-top:32px;background:#1d1236;color:#fbf5ff;border-radius:28px;padding:36px 40px}
.dark h2{color:#fbf5ff} .dark p{font-size:17px;line-height:26px;color:#e3d8ca;max-width:800px} .dark b{color:#f4b942}
.dark figcaption{color:#c9bde0} .dark figcaption b{color:#fbf5ff}
.home{display:flex;gap:22px;margin-top:20px;padding:20px 24px;border-radius:20px;background:linear-gradient(160deg,#3a4a6e,#20283e);width:fit-content}
.home figcaption{color:#eef !important;font-weight:500}
.app{width:48px;height:48px;border-radius:50%} .fake{width:48px;height:48px;border-radius:50%}
'''
home = ('<div class="home"><figure><div class="fake" style="background:#25d366"></div><figcaption>Chat</figcaption></figure>'
        + ''.join(f'<figure><div class="app ic">{icon(v, k="h"+v)}</div><figcaption>{v}</figcaption></figure>' for v in 'ABC')
        + '<figure><div class="fake" style="background:#e2136e"></div><figcaption>Wallet</figcaption></figure><figure><div class="fake" style="background:#0866ff"></div><figcaption>Social</figcaption></figure></div>')
page = f'''<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Bolo: the talking-sun buddy</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Anek+Bangla:wdth,wght@75..125,100..800&display=swap">
<style>{css}</style></head><body><div class="wrap">
<h1>Bolo: the talking-sun buddy</h1>
<p class="lead">Idea 3, made into a character. The sun-bubble gets a face, so it reads as a friend who talks rather than a chat icon. Three versions, each shown as the app icon, in Android's round mask, at real 48dp size, as the themed icon, and in the wordmark, where the buddy becomes the first "o".</p>
{''.join(section(*x) for x in V)}
<section class="dark"><h2>One buddy, many moods</h2>
<p>Because it has a face, the logo can live inside the app as Bolo itself: on the caption, at your turn, on "Nailed it", on a rest day. These five expressions use version B.</p>
<div class="row" style="margin-top:20px">{exps}</div>
<h2 style="margin-top:40px">On a real home screen</h2>
<p>Next to the kind of icons it will sit beside. Plain coloured circles stand in for other apps.</p>
{home}
<h2 style="margin-top:40px">My pick: B, the little bot</h2>
<p>The antenna and the face make it <b>a buddy, not a chat bubble</b>, and the evening star on the antenna ties the icon to the scene the app opens into. A is a close second if the antenna feels too techy; C adds waves that crowd the round icon without saying much more than the open mouth already does. One rule carries over from the app: the eyes glance up at the sky, not at you, because Bolo sits beside you and never stares.</p>
</section></div></body></html>'''
open(OUT, 'w').write(page); print('ok', len(page))
