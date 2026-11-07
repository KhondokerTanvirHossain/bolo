import runpy, sys, os
OUT = sys.argv[1]
G = runpy.run_path(os.path.join(os.path.dirname(__file__), 'gen_compare.py'), run_name='lib') if False else None
# gen_compare.py writes to argv[1] on load; point it at a throwaway file first
_argv = sys.argv[:]; sys.argv = ['x', OUT + '.tmp']
G = runpy.run_path(os.path.join(os.path.dirname(__file__), 'gen_compare.py'))
sys.argv = _argv; os.remove(OUT + '.tmp')

OPTS = [
 dict(id='d', name='Spice', tag='Boldest', line='Turmeric, chilli and wine with a deep teal: a spice-market palette, warm and grown-up, with the wine-dark night as its face.',
  story='From the palette you shared (French Puce, Auburn, Hunyadi Yellow, Police Blue, Vanilla). Turmeric yellow drives every action and Police Blue is the Coach. Auburn red is kept for brand moments only (the Buddy mark, the streak), never for buttons or feedback, so red still never means "wrong".',
  fonts='Schibsted Grotesk + Tiro Bangla', fontEn="'Schibsted Grotesk'", fontBn="'Tiro Bangla'", weightTitle=700, radius='18px', btnRadius='999px',
  home='dark', debrief='light',
  dark=dict(canvas='#540b0e', surface='#6a1a1c', sunken='#7a2a2a', ink='#fff3b0', ink2='#e8cfa6', tint='#e09f3e', ontint='#2a0506', tintink='#f2c36f', tintsoft='#6e3a14', coach='#8fb5bf', oncoach='#0f2329', coachink='#a9c8d0', coachsoft='#1f3a42', closer='#f2c36f', closersoft='#6e3a14'),
  light=dict(canvas='#fffbea', surface='#ffffff', sunken='#fff3b0', ink='#540b0e', ink2='#7a4a3e', tint='#e09f3e', ontint='#540b0e', tintink='#7d4a00', tintsoft='#fde3c0', coach='#335c67', oncoach='#ffffff', coachink='#335c67', coachsoft='#dde9ec', closer='#8a5a00', closersoft='#fbeccb'),
  scene=dict(hi='#335c67', lo='#e09f3e', sun='#fff3b0', far='#9e2a2b', near='#540b0e', plate='rgba(40,5,6,.62)'),
  swatches=[('French Puce · given', '#540b0e'), ('Auburn · given', '#9e2a2b'), ('Hunyadi Yellow · given', '#e09f3e'), ('Police Blue · given', '#335c67'), ('Vanilla · given', '#fff3b0'), ('Warm paper · derived', '#fffbea')],
  score=[('Looks like Claude?', 'Low', 'good'), ('Apple principles', 'Medium: rich, editorial', 'mid'), ('Feels Bangladeshi', 'High: spice, wedding colours', 'good'), ('Calm for nervous learners', 'Medium: warm and intense', 'mid')],
  risk='Red carries "error" in most people\'s heads, so Auburn must never touch a button or a result. A wine-dark home is heavy for a 10 pm session. The light-theme turmeric button needs a thin edge to stand off the page (2.2:1).'),
 dict(id='e', name='Dawn', tag='Softest', line='Coral, apricot, pale lime and sage: a gentle morning palette that feels like the first light of a calm day.',
  story='From the Color Hunt palette #EA907A · #FBC687 · #F4F7C5 · #AACDBE. Coral drives action and sage is the Coach. The palette has no colour dark enough for text, so a deep sage ink is derived from the sage. The type is Lexend, a typeface designed to make reading easier, which fits learners well.',
  fonts='Lexend + Anek Bangla', fontEn="'Lexend'", fontBn="'Anek Bangla'", weightTitle=600, radius='24px', btnRadius='999px',
  home='light', debrief='light',
  dark=dict(canvas='#1c2a26', surface='#26362f', sunken='#31443c', ink='#f4f7c5', ink2='#b8c7ba', tint='#ea907a', ontint='#2a0f08', tintink='#f4ae9c', tintsoft='#4a2e27', coach='#aacdbe', oncoach='#13302a', coachink='#aacdbe', coachsoft='#2b4a40', closer='#fbc687', closersoft='#4a3a1c'),
  light=dict(canvas='#fbfcec', surface='#ffffff', sunken='#f4f7c5', ink='#1f3a33', ink2='#4d6660', tint='#ea907a', ontint='#2a0f08', tintink='#a3452e', tintsoft='#fbe1d9', coach='#3e7a68', oncoach='#ffffff', coachink='#2f6455', coachsoft='#dcebe4', closer='#8a5a00', closersoft='#fde9c8'),
  scene=dict(hi='#7fa597', lo='#fbc687', sun='#f4f7c5', far='#ea907a', near='#2f5048', plate='rgba(20,40,31,.62)'),
  swatches=[('Coral · given', '#ea907a'), ('Apricot · given', '#fbc687'), ('Pale lime · given', '#f4f7c5'), ('Sage · given', '#aacdbe'), ('Deep sage ink · derived', '#1f3a33'), ('Coach green · derived', '#3e7a68')],
  score=[('Looks like Claude?', 'Low to medium: coral on cream is closest to today', 'mid'), ('Apple principles', 'Medium to high: like Health, Journal', 'good'), ('Feels Bangladeshi', 'Low to medium', 'mid'), ('Calm for nervous learners', 'High: the gentlest', 'good')],
  risk='Pastels carry no text weight, so legibility depends entirely on the derived deep sage. It can drift toward a generic wellness app. Coral buttons need a thin edge on the light page (2.3:1).'),
 dict(id='f', name='Plum', tag='Most dreamy', line='Plum, mauve, peach and gold: the moment just after sunset, when the sky turns violet and the horizon still glows.',
  story='From the Color Hunt palette #522D60 · #80426E · #F2A67B · #FFD38B. It also reads as jamun and mango, two summer fruits every Bangladeshi knows. Peach drives action, mauve is the Coach, and gold is the setting sun. Plum is used flat, never as a purple-to-blue gradient.',
  fonts='Albert Sans + Noto Sans Bengali', fontEn="'Albert Sans'", fontBn="'Noto Sans Bengali'", weightTitle=700, radius='22px', btnRadius='999px',
  home='dark', debrief='light',
  dark=dict(canvas='#241329', surface='#33203a', sunken='#522d60', ink='#fff4e6', ink2='#d3bfd0', tint='#f2a67b', ontint='#2a1020', tintink='#f7be9c', tintsoft='#4a2a3a', coach='#e0a3cf', oncoach='#2a0f24', coachink='#e6b3d7', coachsoft='#4a2647', closer='#ffd38b', closersoft='#4a3a20'),
  light=dict(canvas='#fff8f0', surface='#ffffff', sunken='#f6ebe6', ink='#522d60', ink2='#6f5873', tint='#f2a67b', ontint='#3a1530', tintink='#a5502a', tintsoft='#fde3d3', coach='#80426e', oncoach='#ffffff', coachink='#80426e', coachsoft='#f1e1ec', closer='#8a5a00', closersoft='#fff0d2'),
  scene=dict(hi='#522d60', lo='#f2a67b', sun='#ffd38b', far='#80426e', near='#2e1836', plate='rgba(36,19,41,.62)'),
  swatches=[('Plum · given', '#522d60'), ('Mauve · given', '#80426e'), ('Peach · given', '#f2a67b'), ('Gold · given', '#ffd38b'), ('Night plum · derived', '#241329'), ('Cream · derived', '#fff8f0')],
  score=[('Looks like Claude?', 'Low', 'good'), ('Apple principles', 'High: like iOS sunset wallpapers', 'good'), ('Feels Bangladeshi', 'Low to medium: the jamun and mango story', 'mid'), ('Calm for nervous learners', 'High', 'good')],
  risk='Purple and peach sunsets are a popular look, and any purple gradient tips into the "AI app" cliché, so it has to stay flat. Peach buttons need a thin edge on the light page (1.9:1).'),
]

body = ''.join(G['section'](o, i) for i, o in enumerate(OPTS))
for a, b in (('Option A', 'Option D'), ('Option B', 'Option E'), ('Option C', 'Option F')):
    body = body.replace(f'<div class="num">{a}</div>', f'<div class="num">{b}</div>', 1)

fonts = 'https://fonts.googleapis.com/css2?family=Instrument+Sans:wght@400..700&family=Schibsted+Grotesk:wght@400..800&family=Tiro+Bangla&family=Lexend:wght@300..700&family=Anek+Bangla:wght@400..700&family=Albert+Sans:wght@400..800&family=Noto+Sans+Bengali:wght@400..700&family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@20..48,400..600,0..1,0&display=swap'
css = G['CSS'] + '\n.lightedge .btn,.lightedge .talk{box-shadow:inset 0 0 0 1px rgba(0,0,0,.16)}\n.given{font-size:13px;color:#55555b;margin-top:10px}\n'

page = f'''<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Bolo: three more directions (D–F)</title>
<link rel="stylesheet" href="{fonts}">
<style>{css}</style></head>
<body><div class="wrap">
<div class="intro"><h1>Bolo: three more directions</h1>
<p>Options D, E and F use the three palettes chosen for Bolo. They show the same Home, Conversation and Debrief screens as Options A–C, so all six compare like for like.</p>
<p class="given">The given hex values take the brand roles exactly. Where a palette has no colour dark enough for text, or soft enough for backgrounds, a shade is derived from it and marked “derived” beside its swatch.</p>
<div class="jump"><a href="#option-d">D · Spice</a><a href="#option-e">E · Dawn</a><a href="#option-f">F · Plum</a><a href="#verdict">Side by side</a></div>
<p class="keep"><b>Kept in every option:</b> one colour for actions, a separate Coach colour, no red in feedback, the learner's own clip in neutral grey, the companion beside you, the waveform motif, and every text colour at WCAG AA contrast or better in both themes.</p></div>
<div class="lightedge">{body}</div>
<section class="verdict" id="verdict"><h2>Side by side</h2>
<table><tr><th></th><th>D · Spice</th><th>E · Dawn</th><th>F · Plum</th></tr>
<tr><td><b>One-line feel</b></td><td>Warm, rich, grown-up</td><td>Gentle morning light</td><td>Dreamy after-sunset glow</td></tr>
<tr><td><b>Face of Home</b></td><td>Wine-dark night</td><td>Pale morning paper</td><td>Plum night</td></tr>
<tr><td><b>Action / Coach</b></td><td>Turmeric / Police Blue</td><td>Coral / Sage</td><td>Peach / Mauve</td></tr>
<tr><td><b>Looks like Claude?</b></td><td>Low</td><td>Low to medium</td><td>Low</td></tr>
<tr><td><b>Apple influence</b></td><td>Medium</td><td>Medium to high</td><td>High</td></tr>
<tr><td><b>Feels Bangladeshi</b></td><td>High</td><td>Low to medium</td><td>Low to medium</td></tr>
<tr><td><b>Calm for an anxious speaker</b></td><td>Medium</td><td>High</td><td>High</td></tr>
<tr><td><b>Fits the PRD's scenes</b></td><td>Yes, as a dramatic sunset</td><td>Needs a dawn scene, not sunset</td><td>Exactly (dusk, evening drive)</td></tr>
<tr><td><b>Biggest risk</b></td><td>Red reads as "error"</td><td>Washed out or generic wellness</td><td>Purple-gradient cliché if not kept flat</td></tr>
</table></section>
</div></body></html>'''
open(OUT, 'w').write(page)
print('ok', len(page))
