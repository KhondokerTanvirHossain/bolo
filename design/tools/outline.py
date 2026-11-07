import sys, json
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen
f = TTFont(sys.argv[1])
inst = instancer.instantiateVariableFont(f, {"wght": 750, "wdth": 100})
gs = inst.getGlyphSet(); cmap = inst.getBestCmap(); upm = inst['head'].unitsPerEm
out = {'upm': upm, 'glyphs': {}}
for ch in 'Blo':
    gn = cmap[ord(ch)]; g = gs[gn]
    pen = SVGPathPen(gs)
    g.draw(TransformPen(pen, (1, 0, 0, -1, 0, 0)))  # flip y: baseline at 0, up is negative
    bp = BoundsPen(gs); g.draw(bp)
    out['glyphs'][ch] = {'d': pen.getCommands(), 'adv': g.width, 'bounds': bp.bounds}
os2 = inst['OS/2']; out['xh'] = os2.sxHeight; out['cap'] = os2.sCapHeight
json.dump(out, open(sys.argv[2], 'w')); print(upm, out['xh'], out['cap'], {k: (v['adv'], v['bounds']) for k, v in out['glyphs'].items()})
