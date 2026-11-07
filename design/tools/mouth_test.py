import sys, runpy
S=sys.argv[1]; m=runpy.run_path(S+'/tools/mouth.py')
css='''
.bo-mouth{width:320px;height:240px;background:var(--surface);border-radius:18px;display:block}
.bo-mouth .line{fill:none;stroke:var(--ink);stroke-width:2;stroke-linecap:round;stroke-linejoin:round}
.bo-mouth .lip{fill:var(--surface);stroke:var(--ink);stroke-width:2;stroke-linejoin:round}
.bo-mouth .tooth{fill:var(--mouth-tooth);stroke:var(--ink);stroke-width:2;stroke-linejoin:round}
.bo-mouth .flesh{fill:var(--mouth-flesh);stroke:var(--ink);stroke-width:2;stroke-linejoin:round}\n.bo-mouth .tongue{fill:var(--mouth-flesh);stroke:var(--ink);stroke-width:2;stroke-linejoin:round}
.bo-mouth .move{fill:var(--hills-soft);stroke:var(--hills-ink);stroke-width:2;stroke-linejoin:round}
.bo-mouth .air{fill:none;stroke:var(--ink-secondary);stroke-width:1.75;stroke-dasharray:3 4;stroke-linecap:round}
.bo-mouth .airhead{fill:var(--ink-secondary)}
.bo-mouth .buzz path{fill:none;stroke:var(--hills-ink);stroke-width:2;stroke-linecap:round}
'''
P=S+'/ds/project'
tok=open(P+'/tokens.css').read()
body=''.join(f'<div style="margin:8px;font:600 14px sans-serif">{k}{m["svg"](k)}</div>' for k in ['v','w','th','s','z','sh'])
open(S+'/mouth/test.html','w').write(f'<!doctype html><html data-theme="{sys.argv[2]}"><head><style>{tok}body{{background:var(--canvas);display:flex;flex-wrap:wrap;margin:0;color:var(--ink)}}{css}</style></head><body>{body}</body></html>')
