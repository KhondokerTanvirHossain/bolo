import json,re,sys
p=sys.argv[1]; t=json.load(open(p+'/tokens.json'))
themes=[th['id'] for th in t['color']['themes']]
def v(val,th):
    x=val if isinstance(val,str) else val.get(th)
    if isinstance(val,str) and th!=themes[0]: x=val
    if x is None: return None
    m=re.fullmatch(r'\{(.+)\}',x); return f'var(--{m.group(1)})' if m else x
out=['/* Bolo — generated from tokens.json */']
for i,th in enumerate(themes):
    sel=f':root, [data-theme="{th}"]' if i==0 else f'[data-theme="{th}"]'; L=[]
    for fam in ('color','shadow'):
        for tok in t[fam]['tokens']:
            val=tok['value']
            if i>0 and isinstance(val,str) and not re.fullmatch(r'\{.+\}',val): continue
            if i>0 and isinstance(val,dict) and th not in val: continue
            x=v(val,th)
            if x: L.append(f'  --{tok["name"]}: {x};')
    out.append(sel+' {\n'+'\n'.join(L)+'\n}')
L=[f'  --{tok["name"]}: {tok["value"]};' for fam in ('spacing','radius','size') for tok in t[fam]['tokens']]
L+= [f'  --font-{k}: {s};' for k,s in t['type']['families'].items()]
out.append(':root {\n'+'\n'.join(L)+'\n}')
for g in t['type']['groups']:
    for st in g['styles']:
        out.append(f'.{st["name"]} {{ font-family: var(--font-{st.get("family",g["family"])}); font-size: {st["fontSize"]}; line-height: {st["lineHeight"]}; font-weight: {st["fontWeight"]}; letter-spacing: {st["letterSpacing"]}; }}')
open(p+'/tokens.css','w').write('\n'.join(out)+'\n')
# contrast audit
def lum(h):
    h=h.lstrip('#'); r,g,b=[int(h[i:i+2],16)/255 for i in (0,2,4)]
    f=lambda c: c/12.92 if c<=0.03928 else ((c+0.055)/1.055)**2.4
    return 0.2126*f(r)+0.7152*f(g)+0.0722*f(b)
def cr(a,b):
    la,lb=lum(a),lum(b); hi,lo=max(la,lb),min(la,lb); return (hi+0.05)/(lo+0.05)
C={tok['name']:tok['value'] for tok in t['color']['tokens']}
def get(n,th):
    val=C[n]; x=val if isinstance(val,str) else val.get(th, val.get(themes[0]))
    m=re.fullmatch(r'\{(.+)\}',x); return get(m.group(1),th) if m else x
checks=[('ink',['canvas','surface','surface-sunken'],4.5),('ink-secondary',['canvas','surface','surface-sunken'],4.5),('ink-tertiary',['canvas','surface'],4.5),
('line-strong',['canvas','surface','surface-sunken'],3),('on-sun',['sun'],4.5),('sun-edge',['canvas','surface'],3),('sun-ink',['canvas','surface','sun-soft'],4.5),
('on-hills',['hills'],4.5),('hills-ink',['canvas','surface','hills-soft'],4.5),('on-rose',['rose'],4.5),('rose-ink',['canvas','surface','rose-soft'],4.5),
('nailed',['canvas','surface','nailed-soft'],4.5),('closer',['canvas','surface','closer-soft'],4.5),('critical',['canvas','surface'],4.5),('focus-ring',['canvas','surface'],3),('hills',['surface-sunken'],3)]
bad=0
for th in themes:
    for fg,bgs,need in checks:
        for bg in bgs:
            r=cr(get(fg,th),get(bg,th))
            if r<need: bad+=1; print('FAIL',th,fg,'on',bg,round(r,2))
print('sun-edge daylight',round(cr(get('sun-edge','daylight'),get('canvas','daylight')),2),'checks failing:',bad)
