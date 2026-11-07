import json, re, sys
S = sys.argv[1]; P = S + '/ds/project'
SC = json.load(open(S + '/desk/screens.json'))
# endsheet repeats the talk scene: give its gradients their own ids
SC['endsheet'] = SC['endsheet'].replace('id="au-t"', 'id="au-te"').replace('url(#au-t)', 'url(#au-te)').replace('id="st-t"', 'id="st-te"').replace('url(#st-t)', 'url(#st-te)')
tokens = open(P + '/tokens.css').read(); bundle = open(P + '/components/bundle.css').read()
imp = re.search(r"@import url\('([^']+)'\);", bundle).group(1); bundle = bundle.replace(f"@import url('{imp}');", '')
desk = open(S + '/tools/desk.css').read()
CAP = '.bo-caption__text .w { opacity: .5; transition: opacity 140ms ease; } .bo-caption__text .w.on { opacity: 1; }'
GROUPS = [('First visit', [('start', 'Meet Bolo'), ('welcome', 'Welcome'), ('consent', 'Consent'), ('mic', 'Microphone'), ('setup', 'Setup questions'), ('day1', 'Day 1 clip')]),
          ('Talk and feedback', [('talk', 'Conversation'), ('endsheet', 'End dialog'), ('scoring', 'Listening back'), ('debrief', 'Feedback'), ('drill', 'Drill'), ('drilldone', 'Nice work')]),
          ('After the first talk', [('save', 'Save progress'), ('reminder', 'Reminders'), ('home', 'Home')]),
          ('App', [('progress', 'Progress'), ('me', 'Me'), ('plans', 'Plans')])]
nav = ''.join(f'<div class="pg"><div class="pg__t">{g}</div>' + ''.join(f'<a href="#{i}" data-jump="{i}">{t}</a>' for i, t in it) + '</div>' for g, it in GROUPS)
PCSS = '''
html, body { margin: 0; height: 100%; overflow: hidden; background: #0b0816; }
#root { position: absolute; top: 0; left: 0; transform-origin: top left; }
#pbtn { position: fixed; right: 16px; bottom: 16px; z-index: 300; height: 40px; padding: 0 16px; border-radius: 999px; border: 0; background: rgba(20,12,38,.85); color: #fff8ef; font: 650 14px/1 'Anek Bangla', sans-serif; cursor: pointer; box-shadow: 0 6px 20px rgba(0,0,0,.3); }
#ppanel { position: fixed; right: 16px; bottom: 66px; z-index: 300; width: 340px; max-height: calc(100vh - 100px); overflow: auto; background: #fff; color: #1f1433; border-radius: 20px; padding: 18px 20px; box-shadow: 0 20px 60px rgba(0,0,0,.35); font: 500 14px/20px 'Anek Bangla', sans-serif; display: none; }
#ppanel.open { display: block; }
#ppanel b.t { font-size: 20px; } #ppanel p { color: #5a4e6e; margin: 6px 0 12px; }
.prow { display: flex; gap: 8px; margin-bottom: 10px; } .prow button { flex: 1; height: 36px; border: 0; border-radius: 999px; background: #f3eff8; font: 650 13px/1 'Anek Bangla', sans-serif; cursor: pointer; }
.pg { margin-top: 12px; } .pg__t { font-size: 12px; font-weight: 650; color: #5b3f8c; margin-bottom: 6px; }
.pg a { display: inline-block; margin: 0 4px 6px 0; padding: 5px 11px; border-radius: 999px; background: #f3eff8; color: #3a3048; font-size: 13px; font-weight: 600; text-decoration: none; } .pg a.cur { background: #f4b942; color: #2a1a00; }
'''
page = f'''<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Bolo</title>
<link rel="stylesheet" href="{imp}">
<style>{tokens}
{bundle}
{desk}
{CAP}
{PCSS}</style></head>
<body>
<div id="root" class="dk-root" data-theme="daylight">{''.join(SC.values())}</div>
<div class="dk-toast" id="toast" role="status"></div>
<button id="pbtn">Prototype</button>
<div id="ppanel"><b class="t">Bolo on desktop</b><p>Prototype for testing. Bolo's lines play by themselves; on your turn, speak out loud and press <b>Space</b> or click <b>Done</b>. It doesn't listen. Press <b>?</b> for shortcuts.</p>
<div class="prow"><button id="restart">Restart</button><button id="theme">Night theme</button></div>
<label style="display:flex;gap:8px;align-items:center;font-weight:600"><input type="checkbox" id="voice"> Bolo speaks aloud (browser voice)</label>{nav}</div>
<script>{open(S + '/tools/desk.js').read()}</script>
</body></html>'''
open(S + '/desk/index.html', 'w').write(page); print('proto ok', len(page) // 1024, 'KB')
