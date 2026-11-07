import os, re, sys, runpy
S = sys.argv[1]; C = S + '/ds/project/components'
d = runpy.run_path(S + '/tools/drive.py'); drive_svg = d['drive_svg']
conv = open(S + '/ds/conv.html').read()
HILL_SVG = re.search(r'<svg class="bo-scene__art".*?</svg>', conv, re.S).group(0)
def icon(n, fill=False, cls=''):
    return f'<span class="bo-icon{" bo-icon--fill" if fill else ""}{(" " + cls) if cls else ""}" aria-hidden="true">{n}</span>'
# the drive SVG must not redefine ids when shown next to the hilltop: suffix its ids
def uniq(svg, suf):
    for i in ('bo-aura', 'bo-star', 'bo-bokeh', 'bo-tail', 'bo-dashglow'):
        svg = svg.replace(f'id="{i}"', f'id="{i}-{suf}"').replace(f'url(#{i})', f'url(#{i}-{suf})')
    return svg
files = {}

# 1. Conversation in the drive
out = conv.replace(HILL_SVG, drive_svg())
out = out.replace('subtitle="Scene, caption, mic ring, Pause, End. Nothing else."', 'subtitle="Evening drive: the Buddy drives, you ride along"')
out = out.replace('<title>ScreenConversation — Bolo</title>', '<title>ScreenConversationDrive — Bolo</title>').replace('>landscape<', '>directions_car<')
files['ScreenConversationDrive'] = (out, '''# ScreenConversationDrive

The conversation screen in the second scene, Evening drive (PRS-2). The controls are identical to ScreenConversation; only the place changes.

- **The Buddy drives; the learner rides beside them.** A driver looks at the road, so the Buddy never looks at the learner while talking, which is the point of sitting side by side (E9). It also means Bolo never shows the learner driving while using a phone. Bangladesh drives on the left, so the Buddy sits on the right.
- **Layers, far to near:** three-step sky with fewer stars (city glow), the low sun on the road's vanishing point, a violet skyline with warm windows, blurred street lights, the road with tail lights ahead, the car's roof, pillars and dashboard in `scene-sky-high`, and the Buddy and wheel in `scene-hill-near`, rim-lit on the side facing the sun.
- The caption sits over the windscreen sky; the mic ring, Pause and Done sit over the dashboard. Nothing bright sits under text.
- The scene button shows a car icon in this scene, and a landscape icon on the hilltop.
- **Motion (Rive):** the two bright stars twinkle; tail lights drift very slowly; street-light blurs pass every 8–12 s; the Buddy's shoulders breathe. The road itself never scrolls. With reduced motion or on a 2 GB phone it is a still picture.
- **Sound:** a soft engine hum and the occasional indicator tick, lowered under speech (PRS-4).
''')

# 2. Scene overview (replaces CompanionScene preview)
def tile(inner, cap, cls='bo-scene'):
    return f'<div><div class="{cls}" style="width:168px;height:268px;border-radius:22px;position:relative;overflow:hidden">{inner}</div><div class="bo-cap">{cap}</div></div>'
voice = ('<div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center"><div class="bo-mic bo-mic--buddy" role="status" aria-label="Buddy is speaking">'
         '<svg class="arc" viewBox="0 0 96 96" aria-hidden="true"><circle class="track" cx="48" cy="48" r="46"/><circle class="left" cx="48" cy="48" r="46" stroke-dasharray="202.3 289.0"/></svg>'
         f'<div class="bo-mic__core">{icon("graphic_eq", cls="bo-icon--lg")}</div></div></div>')
files['CompanionScene'] = (f'''<!-- @dsCard group="Conversation" height=380 subtitle="Art placeholder: the two places and voice-only" -->
<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><title>CompanionScene — Bolo</title></head>
<body>
<div class="bo-stage" style="gap:16px;justify-content:center">
  {tile(HILL_SVG, 'Hilltop · default')}
  {tile(uniq(drive_svg(), 'tile'), 'Evening drive')}
  {tile(voice, 'Voice-only', 'bo-voiceonly')}
</div>
</body>
</html>
''', '''# CompanionScene

The place you sit in while you talk: dusk layers with the companion beside you, never facing you.

**Needs:** the scene id (`hilltop` · `drive` · `voice-only`), the companion id, the companion's state (idle, speaking, listening, glance, facing), and the reduced-motion flag.

**Use**
- Fill the conversation screen edge to edge, and keep the top 40% calm for the caption.
- **Hilltop** ships inside the app and always works offline. **Evening drive** downloads once (≤ 3 MB); the Buddy drives and the learner rides beside them.
- These previews are **art placeholders** showing layering, palette and composition; final art comes from the illustrator in Rive (see *Presence*). Keep everything that matters between x 60 and 300 of the 360 × 520 frame: a tall phone crops the sides.
- Voice-only mode is `scene-sky-high` with the mic ring centred.

**Don't:** show the companion face-on while talking, animate the sky or the road, or put UI over the companion's head.
''')

# 3. Scene and companion picker (sheet over the conversation)
def thumb(inner, cls='bo-scene'):
    return f'<span class="bo-pick__thumb {cls}">{inner}</span>'
AV = {
 'man': '<svg viewBox="0 0 64 64" aria-hidden="true"><circle cx="32" cy="27" r="11"/><path d="M12 66 C12 47 21 41 32 41 C43 41 52 47 52 66Z"/></svg>',
 'woman': '<svg viewBox="0 0 64 64" aria-hidden="true"><path d="M19 30 C18 13 46 13 45 30 L47 45 C40 48 24 48 17 45 Z"/><path d="M12 66 C12 48 21 42 32 42 C43 42 52 48 52 66Z"/></svg>',
 'friend': '<svg viewBox="0 0 64 64" aria-hidden="true"><path d="M15 26 Q13 10 19 6 Q25 10 29 17 Z"/><path d="M49 26 Q51 10 45 6 Q39 10 35 17 Z"/><circle cx="32" cy="29" r="14"/><path d="M13 66 C13 49 22 43 32 43 C42 43 51 49 51 66Z"/></svg>',
}
vo_thumb = f'<span style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;color:var(--on-scene)"><span style="width:56px;height:56px;border-radius:999px;box-shadow:inset 0 0 0 2px rgba(255,248,239,.35);display:flex;align-items:center;justify-content:center">{icon("graphic_eq")}</span></span>'
sheet = f'''<div class="bo-sheet-scrim" style="z-index:5"></div>
  <div class="bo-sheet" role="dialog" aria-modal="true" aria-labelledby="sp-t" style="gap:0;z-index:6;color:var(--ink)">
    <div class="bo-sheet__grabber"></div>
    <h2 class="bo-sheet__title" id="sp-t">Where shall we talk?</h2>
    <p class="bo-pickset__title" style="margin-top:var(--space-4)">Place</p>
    <div class="bo-picks" role="radiogroup" aria-label="Place">
      <button class="bo-pick" role="radio" aria-checked="false">{thumb(uniq(HILL_SVG, 'p1').replace('xMidYMax slice', 'xMidYMid slice'))}Hilltop</button>
      <button class="bo-pick" role="radio" aria-checked="true">{thumb(uniq(drive_svg('xMidYMid slice'), 'p2') + '<span class="bo-pick__badge">' + icon('download', cls='bo-icon--sm') + '2.8 MB</span>')}Evening drive</button>
      <button class="bo-pick" role="radio" aria-checked="false">{thumb(vo_thumb, 'bo-voiceonly')}Voice only</button>
    </div>
    <div class="bo-dlnote" role="status">
      <p class="bo-dlnote__text">Evening drive is 2.8 MB, and you're on mobile data.</p>
      <div class="bo-dlnote__actions"><button class="bo-btn bo-btn--primary">Download now</button><button class="bo-btn bo-btn--secondary">Wait for Wi-Fi</button></div>
    </div>
    <p class="bo-pickset__title" style="margin-top:var(--space-6)">Companion</p>
    <div class="bo-picks" role="radiogroup" aria-label="Companion">
      <button class="bo-pick" role="radio" aria-checked="true"><span class="bo-pick__avatar">{AV['man']}</span>Man</button>
      <button class="bo-pick" role="radio" aria-checked="false"><span class="bo-pick__avatar">{AV['woman']}</span>Woman</button>
      <button class="bo-pick" role="radio" aria-checked="false"><span class="bo-pick__avatar">{AV['friend']}</span>Character</button>
    </div>
  </div>'''
pick = conv.replace('subtitle="Scene, caption, mic ring, Pause, End. Nothing else."', 'subtitle="Two places, voice-only and three companions, one tap each"')
pick = pick.replace('<title>ScreenConversation — Bolo</title>', '<title>ScreenScenePicker — Bolo</title>')
pick = pick.replace('</div></div>\n</body>', sheet + '\n</div></div>\n</body>')
assert 'Where shall we talk?' in pick
files['ScreenScenePicker'] = (pick, '''# ScreenScenePicker

The sheet behind the small scene button on the conversation screen, and behind Me → Settings → Scene and companion (PRS-2, PRS-6, PRS-7, US-P4, US-P5).

- **Place:** Hilltop, Evening drive and Voice only, as portrait thumbnails of the real scenes. **Companion:** man, woman and a friendly character, as silhouettes on the dusk sky. Both are radio groups; the selected item gets a `sun-ink` ring.
- **One tap applies.** There is no Save or Done; the scene cross-fades behind the sheet so the learner sees the change at once. Drag down to close.
- **Not downloaded yet:** the thumbnail carries its size ("2.8 MB"). On Wi-Fi, tapping it downloads and applies it. On mobile data, a note opens under the places with **Download now** and **Wait for Wi-Fi** (US-P5). While waiting, the current scene stays.
- The first conversation never shows this sheet; it uses the default scene and companion (US-P4). Opening it mid-conversation pauses the Buddy, and closing it resumes.
- Companion silhouettes are placeholders until the three characters are designed. Keep all three clearly friends: relaxed, open, never romantic.
''')
for name, (html, readme) in files.items():
    dd = f'{C}/{name}'; os.makedirs(dd, exist_ok=True)
    open(f'{dd}/preview.html', 'w').write(html); open(f'{dd}/README.md', 'w').write(readme)
print('ok', list(files))
