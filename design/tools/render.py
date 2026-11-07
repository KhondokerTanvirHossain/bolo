import sys, os, re, subprocess
S=sys.argv[1]; theme=sys.argv[2]; names=sys.argv[3:]
P=S+'/ds/project'; css=open(P+'/tokens.css').read()+'\n'+open(P+'/components/bundle.css').read()
imp=re.search(r"@import url\('([^']+)'\);",css).group(1); css=css.replace(f"@import url('{imp}');",'')
os.makedirs(S+'/shots/new',exist_ok=True)
for n in names:
    h=open(f'{P}/components/{n}/preview.html').read()
    m=re.search(r'height=(\d+)',h); H=int(m.group(1))
    heads=''.join(re.findall(r'<style>.*?</style>',h,re.S))
    doc=f'<!doctype html><html data-theme="{theme}"><head><meta charset="utf-8"><link rel="stylesheet" href="{imp}"><style>{css}</style>{heads}</head><body>'+re.search(r'<body>(.*)</body>',h,re.S).group(1)+'</body></html>'
    f=f'{S}/shots/new/{n}.html'; open(f,'w').write(doc)
    out=f'{S}/shots/new/{n}-{theme}.png'
    subprocess.run(['/Applications/Google Chrome.app/Contents/MacOS/Google Chrome','--headless=new','--disable-gpu','--hide-scrollbars',f'--window-size=440,{H}','--virtual-time-budget=6000',f'--screenshot={out}','file://'+f],capture_output=True,timeout=90)
    print(out, os.path.exists(out))
