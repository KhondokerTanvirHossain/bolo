import os, re, sys, runpy
S = sys.argv[1]; C = S + '/ds/project/components'
m = runpy.run_path(S + '/tools/mouth.py'); svg = m['svg']
def icon(n, fill=False, cls=''):
    return f'<span class="bo-icon{" bo-icon--fill" if fill else ""}{(" " + cls) if cls else ""}" aria-hidden="true">{n}</span>'

SOUNDS = [
 ('v', 'The v sound', 'v as in van', 'Top teeth touch your lower lip for v. Let it buzz.', 'উপরের দাঁত নিচের ঠোঁটে ছুঁইয়ে v বলুন।'),
 ('w', 'The w sound', 'w as in win', "Round your lips into a small circle, then open them. Your teeth don't touch your lips.", 'ঠোঁট গোল করে সামনে আনুন, তারপর খুলুন।'),
 ('th', 'The th sound', 'th as in three', 'Put your tongue tip between your teeth and blow gently.', 'জিভের ডগা দুই দাঁতের মাঝে রেখে আস্তে বাতাস ছাড়ুন।'),
 ('s', 'The s sound', 's as in sip', 'Tongue tip just behind your top teeth, not touching. Let the air hiss.', 'জিভের ডগা উপরের দাঁতের ঠিক পেছনে রাখুন, ছোঁবেন না।'),
 ('sh', 'The sh sound', 'sh as in ship', 'Pull your tongue back a little and push your lips forward: shh.', 'জিভ একটু পেছনে টেনে ঠোঁট সামনে আনুন।'),
 ('z', 'The z sound', 'z as in zero', 'Make the s shape, then add your voice. Feel the buzz in your throat.', 's-এর মতো রেখে গলায় আওয়াজ যোগ করুন।'),
 ('cluster', 'Starting with s', 's as in school', 'Start straight with the hiss: sss-chool. Nothing comes before the s.', 'শুরুতেই হিস্‌ শব্দ দিন, s-এর আগে কিছু নয়।'),
]
rows = ''.join(f'''<div class="bo-mouthref">{svg(k)}<div><p class="bo-mouthref__name">{n}</p><p class="bo-mouthref__as">{a}</p><p class="bo-mouthref__tip">{t}</p><p class="bo-mouthref__bn">{b}</p></div></div>\n''' for k, n, a, t, b in SOUNDS)
files = {}
files['MouthDiagram'] = (f'''<!-- @dsCard group="Coach" height=940 subtitle="Seven side views, one moving part each" -->
<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><title>MouthDiagram — Bolo</title></head>
<body>
<div class="bo-stage bo-stage--col" style="gap:12px;max-width:480px">
{rows}</div>
</body>
</html>
''', '''# MouthDiagram

Side views of the mouth for the six sound patterns Bangla speakers meet in English (ASM-2): v, w, th, s and sh, z, and starting a word with s. The s/sh pattern has a drawing for each sound, so there are seven drawings.

**Needs:** the sound (`v`, `w`, `th`, `s`, `sh`, `z`, `cluster`). Each drawing carries its own TalkBack description ("Side view: top teeth resting on the lower lip…").

**Drawing rules**
- A close-up cut-through of the mouth, facing right, the same frame for every sound so only the difference moves.
- Flesh (lips, gums, palate, a still tongue) is `mouth-flesh`; the inside of the mouth is `surface`; teeth are `mouth-tooth`; every edge is a 2px `ink` line.
- **Exactly one part moves** and only it is coloured: `mouth-move` with a 2.5px `hills-ink` edge. v moves the lower lip; w moves both lips; th, s, sh and z move the tongue.
- Air is a dashed `ink-secondary` line with an arrowhead, leaving the mouth at the right. z adds three small `hills-ink` buzz marks low in the throat; that is the only difference from s, which is the point.
- No labels inside the drawing. The words around it do the naming.

**Behaviour:** with `bo-mouth--play`, the moving part travels from rest into position, back, and in again (three 900 ms legs, `ease-out`), then holds. It plays once when the card appears and again from the replay button. Each drawing's rest offset is set with `--rest-x` and `--rest-y`. With reduced motion it shows the final position only. The final app builds these in Rive with the same frames.

**Copy:** each tip is one or two short lines in plain words, with no phonetics terms. The Bangla lines here are placeholders for the Bangla copywriter.
''')

# In-card tip
v_svg = svg('v', 'bo-mouth bo-mouth--play', '100 22 98 98')
mouthtip = f'''<div class="bo-mouthtip"><div class="bo-mouthtip__art">{v_svg}<button class="bo-mouthtip__again" aria-label="Show the movement again">{icon('replay')}</button></div><div class="bo-mouthtip__body"><p class="bo-mouthtip__text">Top teeth touch your lower lip for v. Let it buzz.</p><button class="bo-tip__bn">বাংলায়</button></div></div>'''
files['MouthTip'] = (f'''<!-- @dsCard group="Coach" height=300 subtitle="The fix card's tip, with the mouth drawn" -->
<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><title>MouthTip — Bolo</title></head>
<body>
<div class="bo-stage bo-stage--col">
{mouthtip}
</div>
</body>
</html>
''', '''# MouthTip

The fix card's tip, grown to include a MouthDiagram. It replaces the one-line tip on a sound fix card when the learner needs to see the sound, not only hear it.

**When it appears**
- After the same sound has been missed in 3 sessions (US-5.6).
- Always, for learners who said they are hard of hearing (US-9.3, AX-6).
- When the learner taps the plain tip's lightbulb to ask "how?". Then it stays open for that sound from then on.

**Needs:** the sound, its one-line tip, and the Bangla tip.

**Use**
- It sits where the plain tip sat, on `hills-soft`, so it still reads as the Coach speaking.
- The drawing is a 112dp square close-up of the front of the mouth (the full side view lives in MouthDiagram). The tip and **বাংলায়** sit beside it; a small replay button on the drawing's corner plays the movement again.
- This keeps the card only about 70dp taller than with the one-line tip, so Try it stays on screen at 360dp.
- The spelled-out sound ("v as in *van*") stays in the card title area for hard-of-hearing learners, as US-9.3 asks.

**Behaviour:** the drawing plays once when the card settles, never while a clip is playing, so the eye and ear aren't pulled two ways.
''')

# Screen: debrief fix card with mouth tip
deb = open(S + '/ds/debrief.html').read()
deb = deb.replace('<!-- @dsCard group="Screens" height=820 subtitle="One fix at a time, audio first" page -->',
                  '<!-- @dsCard group="Screens" height=820 subtitle="Third time on v: the card shows the mouth" page -->')
deb = deb.replace('<title>ScreenDebrief — Bolo</title>', '<title>ScreenFixMouth — Bolo</title>')
tip_old = re.search(r'<div class="bo-tip">.*?</div>', deb, re.S).group(0)
deb = deb.replace(tip_old, mouthtip)
# pre-attempt state: no result pill yet, primary is Try it
deb = re.sub(r'<span class="bo-result bo-result--closer".*?</span>Closer</span>\s*', '', deb, flags=re.S)
deb = deb.replace('Try again</button>', 'Try it</button>')
deb = deb.replace('<h2 class="bo-fix__title">The v sound</h2>', '<h2 class="bo-fix__title">The v sound <span style="font:500 15px/20px var(--font-sans);color:var(--ink-secondary);letter-spacing:0">· v as in <i>van</i></span></h2>')
assert 'bo-mouthtip' in deb and 'Try it</button>' in deb and 'bo-result--closer' not in deb
files['ScreenFixMouth'] = (deb, '''# ScreenFixMouth

A sound fix card on the debrief, the third session in a row that v was missed (US-5.6). Everything is as on ScreenDebrief, except the one-line tip has grown into a MouthTip and the title spells the sound out ("v as in *van*").

- Order stays audio first: your clip, the model clip, then the drawing. The drawing plays once the clips have finished.
- **Try it** is still the one primary action.
- To make room on a 360dp screen, the flexible space in the card shrinks first. On small screens with large text, the card scrolls; the Try it button stays pinned at the bottom.
''')

for name, (html, readme) in files.items():
    d = f'{C}/{name}'; os.makedirs(d, exist_ok=True)
    open(f'{d}/preview.html', 'w').write(html); open(f'{d}/README.md', 'w').write(readme)
print('ok', list(files))
