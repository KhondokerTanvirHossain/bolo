import sys, runpy, re
S=sys.argv[1]; d=runpy.run_path(S+'/tools/drive.py')
conv=open(S+'/ds/conv.html').read()
svg_old=re.search(r'<svg class="bo-scene__art".*?</svg>',conv,re.S).group(0)
out=conv.replace(svg_old,d['drive_svg']()).replace('Scene, caption, mic ring, Pause, End. Nothing else.','Evening drive: the Buddy drives, you ride along')
out=out.replace('<title>ScreenConversation — Bolo</title>','<title>ScreenConversationDrive — Bolo</title>').replace('>landscape<','>directions_car<')
import os; os.makedirs(S+'/ds/project/components/ScreenConversationDrive',exist_ok=True)
open(S+'/ds/project/components/ScreenConversationDrive/preview.html','w').write(out)
