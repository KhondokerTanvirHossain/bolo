import json,sys,re
jl,tag,out=sys.argv[1],sys.argv[2],sys.argv[3]
for line in open(jl):
    if tag not in line: continue
    o=json.loads(line)
    def walk(x):
        if isinstance(x,str):
            if '<'+tag+'>' in x: yield x
        elif isinstance(x,dict):
            for v in x.values(): yield from walk(v)
        elif isinstance(x,list):
            for v in x: yield from walk(v)
    for s in walk(o):
        m=re.search('<'+tag+'>(.*?)</'+tag+'>',s,re.S)
        if m:
            open(out,'w').write(m.group(1)); print('ok',len(m.group(1))); sys.exit()
print('not found')
