import runpy, sys, os
OUT = sys.argv[1]
here = os.path.dirname(__file__)
_argv = sys.argv[:]; sys.argv = ['x', OUT + '.tmp']
g3 = runpy.run_path(os.path.join(here, 'gen_compare3.py'))
sys.argv = _argv; os.remove(OUT + '.tmp')
G = g3['G']; OPT = dict(g3['OPT']); CSS3 = g3['css']

FONTS = [
 dict(id='f1', num='Font option 1', name='Mid-century', pair='Gabarito + Hind Siliguri', en="'Gabarito'", bn="'Hind Siliguri'", tw=700, dw=800, track='-.03em',
  idea='Geometric and confident, with round letters and a poster-like voice. It is the face of mid-century animation and signage, so it suits Neon Dusk closely.',
  why=['Closest to the Neon Dusk mood; the Talk button and titles feel like a vintage sign', 'Gabarito\'s heavy weights make numbers ("11 → 4", "Day 21") feel like a badge', 'Hind Siliguri is a clear, widely used Bangla UI face'],
  watch='Gabarito is drawn for headlines: at 13–15px in long text it is wider and less refined than a text face. The two scripts are drawn by different designers, so Bangla looks heavier and more traditional than the English.',
  score=[('Readable at 360dp', 'Good', 'mid'), ('Bangla and English match', 'Fair', 'mid'), ('Fits Neon Dusk', 'Best', 'good'), ('Feels like Apple', 'Low', 'mid'), ('Character', 'Highest', 'good'), ('Extra download on Android', '2 families', 'mid')]),
 dict(id='f2', num='Font option 2', name='Precise', pair='Instrument Sans + Noto Sans Bengali', en="'Instrument Sans'", bn="'Noto Sans Bengali'", tw=650, dw=700, track='-.025em',
  idea='A calm, exact grotesque in the spirit of Apple\'s San Francisco: neutral, slightly narrow and very legible. The colour does the talking and the type gets out of the way.',
  why=['Closest to Apple\'s typography: clear at 13px and elegant at 40px', 'Noto Sans Bengali is the Bangla font Android already uses, so Bangla needs no download and matches the keyboard and notifications', 'Its narrow width fits longer captions and Bangla helper lines on a 360dp screen'],
  watch='The most neutral of the three, so the personality has to come from the colour, the scene and the motion. On its own it could belong to any well-made app.',
  score=[('Readable at 360dp', 'Best', 'good'), ('Bangla and English match', 'Good', 'good'), ('Fits Neon Dusk', 'Good', 'mid'), ('Feels like Apple', 'Highest', 'good'), ('Character', 'Quiet', 'mid'), ('Extra download on Android', '1 family (Bangla is built in)', 'good')]),
 dict(id='f3', num='Font option 3', name='One voice', pair='Anek Bangla (Bangla + English)', en="'Anek Bangla'", bn="'Anek Bangla'", tw=650, dw=750, track='-.02em',
  idea='One family drawn for both scripts at once, so a Bangla line and an English line read as the same voice. It is warm, modern and slightly condensed, and has a width axis that can tighten on small screens.',
  why=['English and Bangla were designed together, so the switch between languages is seamless', 'One variable font file for both scripts keeps the app small', 'Feels made for this region rather than imported'],
  watch='Its English letters are good but less polished than a dedicated Latin face, especially in bold display sizes. It is also less familiar to Western-trained eyes, which some will read as charm and others as unusual.',
  score=[('Readable at 360dp', 'Good', 'mid'), ('Bangla and English match', 'Best', 'good'), ('Fits Neon Dusk', 'Good', 'mid'), ('Feels like Apple', 'Medium', 'mid'), ('Character', 'Warm, local', 'good'), ('Extra download on Android', '1 family for both', 'good')]),
]

def specimen(f):
    st = f"--fen:{f['en']}, system-ui, sans-serif;--fbn:{f['bn']}, sans-serif;--dw:{f['dw']};--tw:{f['tw']};--trk:{f['track']}"
    return f'''<div class="spec2" style="{st}">
 <div class="sp-hero">Bolo</div>
 <div class="sp-row"><span class="sp-k">Display 40</span><span class="sp-display">Day 1 → Day 21</span></div>
 <div class="sp-row"><span class="sp-k">Title 26</span><span class="sp-title">What do you need English for most?</span></div>
 <div class="sp-row"><span class="sp-k">Caption 22</span><span class="sp-cap">Oh nice. What was the bug doing before you fixed it?</span></div>
 <div class="sp-row"><span class="sp-k">Body 17</span><span class="sp-body">Bolo listens only while you practise. Talk first, and your coach will help you after.</span></div>
 <div class="sp-row"><span class="sp-k">Bangla 17</span><span class="sp-bn">বোলো শুধু অনুশীলনের সময় শোনে। আগে কথা বলুন, পরে শিখুন।</span></div>
 <div class="sp-row"><span class="sp-k">Mixed</span><span class="sp-body">Top teeth touch your lower lip for v. <span class="sp-bnin">উপরের দাঁত নিচের ঠোঁটে ছুঁইয়ে v বলুন।</span></span></div>
 <div class="sp-row"><span class="sp-k">Numbers</span><span class="sp-num">"actually" × 11 → 4 · 0:42 · 6-day run</span></div>
 <div class="sp-row"><span class="sp-k">Small 13</span><span class="sp-small">Some feedback was skipped because of background noise.</span></div>
</div>'''

def phones(f):
    o = dict(OPT); o['fontEn'] = f['en']; o['fontBn'] = f['bn']; o['weightTitle'] = f['tw']
    return f'<div class="phones">{"".join(f"<figure>{fn(o)}<figcaption>{cap}</figcaption></figure>" for fn, cap in ((G["home"], "Home"), (G["convo"], "Conversation"), (G["fix"], "Debrief")))}</div>'

def section(f, i):
    why = ''.join(f'<li>{w}</li>' for w in f['why'])
    sc = ''.join(f'<div class="sc"><span>{a}</span><b class="{k}">{b}</b></div>' for a, b, k in f['score'])
    return f'''<section class="opt" id="{f['id']}">
<header class="oh"><div><div class="num">{f['num']}</div><h2>{f['name']}</h2><p class="line">{f['pair']}</p></div></header>
<div class="fgrid">
 <div><p class="story">{f['idea']}</p><ul class="why">{why}</ul><div class="scs">{sc}</div><p class="risk"><b>Watch out:</b> {f['watch']}</p></div>
 <div>{specimen(f)}</div>
</div>
<div class="option-g" id="option-g-{f['id']}">{phones(f)}</div>
</section>'''

extra = r'''
.fgrid{display:grid;grid-template-columns:320px 1fr;gap:40px;margin:28px 0 32px;align-items:start}
.why{margin:0 0 20px;padding-left:18px;color:#3a3a3f;font-size:15px}.why li{margin-bottom:6px}
.spec2{background:#1d1236;color:#fbf5ff;border-radius:24px;padding:28px 32px;font-family:var(--fen)}
.sp-hero{font-size:96px;line-height:.9;font-weight:var(--dw);letter-spacing:-.04em;color:#f4b942;margin-bottom:20px}
.sp-row{display:grid;grid-template-columns:96px 1fr;gap:16px;align-items:baseline;padding:10px 0;border-top:1px solid rgba(255,255,255,.08)}
.sp-k{font:600 11px/1.3 'Instrument Sans',sans-serif;color:#c9bde0;letter-spacing:.02em}
.sp-display{font-size:40px;line-height:44px;font-weight:var(--dw);letter-spacing:var(--trk)}
.sp-title{font-size:26px;line-height:32px;font-weight:var(--tw);letter-spacing:-.015em}
.sp-cap{font-size:22px;line-height:30px;font-weight:500}
.sp-body{font-size:17px;line-height:26px;font-weight:400}
.sp-bn{font-family:var(--fbn);font-size:17px;line-height:28px;font-weight:500}
.sp-bnin{font-family:var(--fbn);color:#c9bde0}
.sp-num{font-size:22px;line-height:28px;font-weight:var(--tw);color:#f4b942;font-variant-numeric:tabular-nums}
.sp-small{font-size:13px;line-height:18px;font-weight:500;color:#c9bde0}
'''
# apply Option G scene/streak overrides to every phone group
css = CSS3.replace('#option-g ', '.option-g ') + extra

fonts = ('https://fonts.googleapis.com/css2?family=Instrument+Sans:wght@400..700&family=Gabarito:wght@400..900'
         '&family=Hind+Siliguri:wght@400;500;600;700&family=Noto+Sans+Bengali:wght@400..800&family=Anek+Bangla:wght@300..800'
         '&family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@20..48,400..600,0..1,0&display=swap')

page = f'''<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Bolo: three font options</title>
<link rel="stylesheet" href="{fonts}">
<style>{css}</style></head>
<body><div class="wrap">
<div class="intro"><h1>Bolo: three font options</h1>
<p>Each pairing is shown on the chosen Neon Dusk screens, with a specimen of the type scale, Bangla, mixed lines and numbers. Colours, layout and sizes are identical across the three, so the only difference is the type.</p>
<div class="jump"><a href="#f1">1 · Mid-century</a><a href="#f2">2 · Precise</a><a href="#f3">3 · One voice</a><a href="#verdict">Side by side</a></div>
<p class="keep"><b>Every option is free (SIL Open Font License), works offline once bundled, covers Bangla properly, and has variable weights</b> so one file serves every size.</p></div>
{''.join(section(f, i) for i, f in enumerate(FONTS))}
<section class="verdict" id="verdict"><h2>Side by side</h2>
<table><tr><th></th><th>1 · Mid-century</th><th>2 · Precise</th><th>3 · One voice</th></tr>
<tr><td><b>Pairing</b></td><td>Gabarito + Hind Siliguri</td><td>Instrument Sans + Noto Sans Bengali</td><td>Anek Bangla for both</td></tr>
<tr><td><b>Personality</b></td><td>Retro, bold, poster</td><td>Calm, exact, Apple-like</td><td>Warm, local, unified</td></tr>
<tr><td><b>Readable on a 360dp phone</b></td><td>Good</td><td>Best</td><td>Good</td></tr>
<tr><td><b>Bangla and English match</b></td><td>Fair</td><td>Good</td><td>Best</td></tr>
<tr><td><b>Fits Neon Dusk</b></td><td>Best</td><td>Good</td><td>Good</td></tr>
<tr><td><b>App size</b></td><td>Two families</td><td>One family (Bangla built in)</td><td>One family</td></tr>
<tr><td><b>Biggest risk</b></td><td>Clunky in small text</td><td>Too anonymous</td><td>English less polished</td></tr>
</table></section>
</div></body></html>'''
open(OUT, 'w').write(page)
print('ok', len(page))
