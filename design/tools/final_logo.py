import json, math, os, sys
S = sys.argv[1]; OUT = sys.argv[2]
G = json.load(open(S + '/fontdl/glyphs.json'))
IND, SUN, HILL, CREAM, EDGE, INK, INK_N = '#1D1236', '#F4B942', '#5B3F8C', '#FFF8EF', '#9A6A0A', '#1F1433', '#FBF5FF'
def bubble(cx, cy, r):
    a1, a2 = math.radians(127), math.radians(150)
    p1 = (cx + r*math.cos(a1), cy + r*math.sin(a1)); p2 = (cx + r*math.cos(a2), cy + r*math.sin(a2))
    tip = (cx - r*1.0, cy + r*1.06)
    f = lambda v: f'{v:.2f}'.rstrip('0').rstrip('.')
    return (f'M{f(cx)} {f(cy-r)} A{f(r)} {f(r)} 0 1 1 {f(p1[0])} {f(p1[1])} L{f(tip[0])} {f(tip[1])} '
            f'L{f(p2[0])} {f(p2[1])} A{f(r)} {f(r)} 0 0 1 {f(cx)} {f(cy-r)}Z')
def star(x, y, s):
    return f'M{x} {y-s} Q{x} {y} {x+s} {y} Q{x} {y} {x} {y+s} Q{x} {y} {x-s} {y} Q{x} {y} {x} {y-s}Z'
def face(cx, cy, r, ink=IND, glint=CREAM):
    # light face: two eyes glancing up-right, one open smile; nothing else
    k = r / 24.0
    X = lambda v: f'{cx + v*k:.2f}'; Y = lambda v: f'{cy + v*k:.2f}'
    out = ''
    for e in (-5.1, 7.9):
        out += f'<ellipse cx="{X(e)}" cy="{Y(-6.2)}" rx="{3.0*k:.2f}" ry="{4.2*k:.2f}" fill="{ink}"/>'
        if glint: out += f'<circle cx="{X(e+1.0)}" cy="{Y(-7.8)}" r="{1.05*k:.2f}" fill="{glint}"/>'
    out += f'<path d="M{X(-4.6)} {Y(3.6)} Q{X(1.4)} {Y(12.6)} {X(7.4)} {Y(3.6)} Q{X(1.4)} {Y(6.2)} {X(-4.6)} {Y(3.6)}Z" fill="{ink}"/>'
    return out
# ---------- icon (108 × 108 adaptive canvas, safe zone = 66 circle) ----------
CX, CY, R = 55, 50, 24
BUB = bubble(CX, CY, R)
HILLP = 'M0 80 Q54 70 108 80 V108 H0Z'
AURA = (f'<defs><radialGradient id="bo-glow"><stop offset="0%" stop-color="{SUN}" stop-opacity=".5"/>'
        f'<stop offset="100%" stop-color="{SUN}" stop-opacity="0"/></radialGradient></defs>')
def svg(body, vb='0 0 108 108', w=None):
    wh = f' width="{w}" height="{w}"' if w else ''
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{vb}"{wh}>{body}</svg>'
background = svg(f'{AURA}<rect width="108" height="108" fill="{IND}"/><circle cx="{CX}" cy="{CY}" r="40" fill="url(#bo-glow)"/><path d="{star(31, 29, 3)}" fill="{CREAM}" opacity=".9"/>')
foreground = svg(f'<path d="{BUB}" fill="{SUN}"/>{face(CX, CY, R)}<path d="{HILLP}" fill="{HILL}"/>')
full = svg(f'{AURA}<rect width="108" height="108" fill="{IND}"/><circle cx="{CX}" cy="{CY}" r="40" fill="url(#bo-glow)"/>'
           f'<path d="{star(31, 29, 3)}" fill="{CREAM}" opacity=".9"/><path d="{BUB}" fill="{SUN}"/>{face(CX, CY, R)}<path d="{HILLP}" fill="{HILL}"/>')
mono = svg(f'<path d="{BUB}" fill="#000"/><path d="M0 86 Q54 78 108 86 V108 H0Z" fill="#000"/><path d="{star(31, 29, 3.5)}" fill="#000"/>')
# notification: the bubble alone, white, on a 24 grid
notif = svg(f'<path d="{bubble(12.6, 11.2, 9.2)}" fill="#FFFFFF"/>', '0 0 24 24')
mark = svg(f'<path d="{bubble(26, 24, 21)}" fill="{SUN}"/>{face(26, 24, 21)}', '0 0 50 50')
# ---------- wordmark from Anek Bangla 750 outlines ----------
U = G['upm']; gl = G['glyphs']; TRACK = -0.03 * U
def wordmark(ink, edge):
    x = 0; parts = []
    parts.append(f'<path transform="translate({x:.0f} 0)" d="{gl["B"]["d"]}" fill="{ink}"/>'); x += gl['B']['adv'] + TRACK + 110
    ob = gl['o']['bounds']; r = (ob[3] - ob[1]) / 2 * 0.95; cy = -(ob[3] + ob[1]) / 2 - 12; cx = x + gl['o']['adv'] / 2
    st = f' stroke="{edge}" stroke-width="30" stroke-linejoin="round"' if edge else ''
    parts.append(f'<path d="{bubble(cx, cy, r)}" fill="{SUN}"{st}/>' + face(cx, cy, r, IND, CREAM)); x += gl['o']['adv'] + TRACK + 10
    parts.append(f'<path transform="translate({x:.0f} 0)" d="{gl["l"]["d"]}" fill="{ink}"/>'); x += gl['l']['adv'] + TRACK
    parts.append(f'<path transform="translate({x:.0f} 0)" d="{gl["o"]["d"]}" fill="{ink}"/>'); x += gl['o']['adv']
    vb = f'60 -1400 {x - 20:.0f} 1520'
    return svg(''.join(parts), vb)
wm_day = wordmark(INK, EDGE); wm_night = wordmark(INK_N, None)
os.makedirs(OUT, exist_ok=True)
for n, c in [('bolo-icon.svg', full), ('bolo-icon-background.svg', background), ('bolo-icon-foreground.svg', foreground),
             ('bolo-icon-monochrome.svg', mono), ('bolo-notification.svg', notif), ('bolo-mark.svg', mark),
             ('bolo-wordmark-day.svg', wm_day), ('bolo-wordmark-night.svg', wm_night)]:
    open(f'{OUT}/{n}', 'w').write(c)
json.dump({'full': full, 'mono': mono, 'notif': notif, 'mark': mark, 'wm_day': wm_day, 'wm_night': wm_night}, open(S + '/fontdl/final.json', 'w'))
print('ok', os.listdir(OUT))
