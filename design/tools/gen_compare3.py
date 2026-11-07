import runpy, sys, os
OUT = sys.argv[1]
_argv = sys.argv[:]; sys.argv = ['x', OUT + '.tmp']
G = runpy.run_path(os.path.join(os.path.dirname(__file__), 'gen_compare.py'))
sys.argv = _argv; os.remove(OUT + '.tmp')

OPT = dict(id='g', name='Neon Dusk', tag='Most playful', line='An indigo night over a warm city, a soft rose glow, violet hills and a mustard sun going down: flat, saturated, mid-century colour.',
  story='The mood is borrowed from the colour world of BoJack Horseman: Los Angeles twilights, violet hills and neon pinks, painted flat like mid-century animation. Only the palette and the mood are borrowed; there is no character, no horse, no title type and none of the show\'s motifs. The mustard sun drives every action, so the Talk button and the mic ring glow like the sunset: your voice is the sun. Violet hills is the Coach, so the debrief feels like the twilight sky after the talk, and a dusty rose, softened from the show\'s neon pink, is the Buddy\'s accent: the streak and the last glow at the horizon. Star dust appears as the light fades, with the evening star (সন্ধ্যাতারা) twinkling brightest, and the sun carries a soft আভা (glow) that fades into the sky instead of sitting flat.',
  fonts='Gabarito + Hind Siliguri', fontEn="'Gabarito'", fontBn="'Hind Siliguri'", weightTitle=700, radius='20px', btnRadius='999px',
  home='dark', debrief='light',
  dark=dict(canvas='#1d1236', surface='#2a1d48', sunken='#3a2b5e', ink='#fbf5ff', ink2='#c9bde0', tint='#f4b942', ontint='#2a1a00', tintink='#ffd27a', tintsoft='#4a3a12', accent='#dcaabf', accentsoft='#2f2142', coach='#b8a2e8', oncoach='#160b2e', coachink='#c4b0f0', coachsoft='#33255a', closer='#f4b942', closersoft='#4a3a12'),
  light=dict(canvas='#f7f4fb', surface='#ffffff', sunken='#efedf2', ink='#1f1433', ink2='#5a4e6e', tint='#f4b942', ontint='#2a1a00', tintink='#6e4a00', tintsoft='#fdefcc', accent='#b0245f', accentsoft='#fde0ec', coach='#5b3f8c', oncoach='#ffffff', coachink='#5b3f8c', coachsoft='#e3d8f6', closer='#8a5a00', closersoft='#fdefcc'),
  scene=dict(hi='#2b1b4e', lo='#c98aa3', sun='#f4b942', far='#5b3f8c', near='#1a0f2e', plate='rgba(20,12,38,.62)'),
  swatches=[('Indigo night', '#1d1236'), ('Mustard sun · action', '#f4b942'), ('Violet hills · Coach', '#5b3f8c'), ('Dusty rose · Buddy accent', '#c98aa3'), ('Twilight violet · Coach at night', '#b8a2e8'), ('Lavender paper', '#f7f4fb')],
  score=[('Looks like Claude?', 'Very low', 'good'), ('Apple principles', 'Medium: more like Apple Arcade', 'mid'), ('Feels Bangladeshi', 'Low', 'mid'), ('Calm for nervous learners', 'Medium: moody, a little electric', 'mid'), ('Fits the PRD scenes', 'Strong for the evening drive', 'good')],
  risk='Mustard buttons sit next to the amber "Closer" result, so results must always carry their arrow icon and word. The rose is kept soft and blended through violet, which avoids the 80s neon cliché; don\'t push it back to full neon. Fans of the show will feel the reference: that\'s fine as mood, but never add a horse, the show\'s typography or its city sign. In light mode the mustard button almost disappears into the page (1.6:1), so it needs a visible edge; at night it glows (9.9:1).')


import random as _r
_rng = _r.Random(7)
_dust = []
# faint star dust: upper sky (conversation) and mid sky (also visible in the Home card crop); keep clear of the sun
for (x, y) in [(70,48),(118,92),(250,40),(300,110),(180,70),(88,150),(140,190),(286,176),(318,232),(64,236),(150,240),(226,128),(110,262),(300,268),(96,30),(206,24),(160,118),(268,152),(78,112),(196,170),(240,206),(124,226),(332,58),(48,184),(270,98),(136,58)]:
    rr = _rng.choice([1.0,1.2,1.4,1.7]); op = _rng.choice([.5,.6,.7,.85])
    _dust.append(f'<circle class="dust" cx="{x}" cy="{y}" r="{rr}" opacity="{op}"/>')
def _spark(cx, cy, s, delay):
    d = f'M{cx} {cy-s} Q{cx} {cy} {cx+s} {cy} Q{cx} {cy} {cx} {cy+s} Q{cx} {cy} {cx-s} {cy} Q{cx} {cy} {cx} {cy-s}Z'
    return f'<g class="twinkle" style="animation-delay:{delay}s"><circle cx="{cx}" cy="{cy}" r="{s*1.6:.1f}" class="starglow"/><path d="{d}" class="spark"/></g>'
_sparks = _spark(96, 206, 6.5, 0) + _spark(258, 166, 5, 1.6)
_glow = ('<defs><radialGradient id="g-aura" cx="50%" cy="50%" r="50%">'
         '<stop offset="0%" style="stop-color:var(--scene-sun);stop-opacity:.75"/>'
         '<stop offset="42%" style="stop-color:var(--scene-sun);stop-opacity:.32"/>'
         '<stop offset="100%" style="stop-color:var(--scene-sun);stop-opacity:0"/></radialGradient>'
         '<radialGradient id="g-star" cx="50%" cy="50%" r="50%"><stop offset="0%" stop-color="#fff8ef" stop-opacity=".7"/><stop offset="100%" stop-color="#fff8ef" stop-opacity="0"/></radialGradient></defs>')
_aura = '<circle cx="222" cy="300" r="140" fill="url(#g-aura)"/>'
_g = G['home'].__globals__
for key in ('SCENE', 'SCENE_TALL'):
    sv = _g[key]
    sv = sv.replace('aria-hidden="true">', 'aria-hidden="true">' + _glow + ''.join(_dust) + _sparks + _aura, 1)
    _g[key] = sv

body = G['section'](OPT, 0).replace('<div class="num">Option A</div>', '<div class="num">Option G</div>', 1)
fonts = 'https://fonts.googleapis.com/css2?family=Instrument+Sans:wght@400..700&family=Gabarito:wght@400..800&family=Hind+Siliguri:wght@400;500;600;700&family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@20..48,400..600,0..1,0&display=swap'
css = G['CSS'] + '\n#option-g .streak{color:var(--accent);background:var(--accentsoft)}\n.art .dust{fill:#fff8ef}\n.art .spark{fill:#fffdf6}\n.art .starglow{fill:url(#g-star)}\n.art .halo{fill:none;stroke:var(--scene-sun);stroke-width:1.2;opacity:.4}\n.art .halo2{opacity:.16}\n.twinkle{transform-box:fill-box;transform-origin:center;animation:bo-twinkle 4.2s ease-in-out infinite}\n@keyframes bo-twinkle{0%,100%{opacity:.55;transform:scale(.85)}50%{opacity:1;transform:scale(1)}}\n@media (prefers-reduced-motion:reduce){.twinkle{animation:none;opacity:.9}}\n#option-g .scene{background:linear-gradient(180deg,var(--sky-hi) 0%,var(--sky-hi) 12%,#5b3f8c 48%,var(--sky-lo) 80%,var(--sky-lo) 100%)}\n#option-g .phone:not(.scene) .talk{box-shadow:0 10px 30px color-mix(in srgb,var(--tint) 45%,transparent)}\n#option-g .fix .btn{box-shadow:inset 0 0 0 1.5px #c48a1a}\n.lightedge .btn,.lightedge .talk{box-shadow:inset 0 0 0 1px rgba(0,0,0,.16)}\n.given{font-size:13px;color:#55555b;margin-top:10px}\n'

page = f'''<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Bolo: Option G, Neon Dusk</title>
<link rel="stylesheet" href="{fonts}">
<style>{css}</style></head>
<body><div class="wrap">
<div class="intro"><h1>Bolo: Option G</h1>
<p>A seventh direction: the colour mood of BoJack Horseman, applied to Bolo. It shows the same Home, Conversation and Debrief screens as Options A–F, so all seven compare like for like.</p>
<p class="keep"><b>Kept, as in every option:</b> one colour for actions, a separate Coach colour, no red in feedback, the learner's own clip in neutral grey, the companion beside you, the waveform motif, and every text colour at WCAG AA contrast or better in both themes.</p></div>
<div class="lightedge">{body}</div>
<section class="verdict" id="verdict"><h2>Next to its nearest neighbours</h2>
<table><tr><th></th><th>A · Dusk</th><th>F · Plum</th><th>G · Neon Dusk</th></tr>
<tr><td><b>One-line feel</b></td><td>Cinematic calm</td><td>Dreamy after-sunset glow</td><td>Playful, moody city night</td></tr>
<tr><td><b>Action / Coach</b></td><td>Apricot / Tide teal</td><td>Peach / Mauve</td><td>Mustard sun / Violet hills (rose accent)</td></tr>
<tr><td><b>Energy</b></td><td>Lowest</td><td>Low</td><td>Medium to high</td></tr>
<tr><td><b>Calm for an anxious speaker</b></td><td>Highest</td><td>High</td><td>Medium</td></tr>
<tr><td><b>Memorable at a glance</b></td><td>Medium</td><td>Medium to high</td><td>Highest</td></tr>
<tr><td><b>Biggest risk</b></td><td>Looks like a meditation app</td><td>Purple-gradient cliché</td><td>Retro neon cliché, or too close to the show</td></tr>
</table></section>
</div></body></html>'''
open(OUT, 'w').write(page)
print('ok', len(page))
