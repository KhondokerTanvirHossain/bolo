import sys
from PIL import Image
out=sys.argv[1]; files=sys.argv[2:]
ims=[Image.open(f).convert('RGB') for f in files]
W=sum(i.width for i in ims); H=max(i.height for i in ims)
c=Image.new('RGB',(W,H),'white'); x=0
for i in ims: c.paste(i,(x,0)); x+=i.width
c.save(out); print(c.size)
