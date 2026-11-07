IND, SUN, HILL, ROSE, CREAM, EDGE = '#1D1236', '#F4B942', '#5B3F8C', '#C98AA3', '#FFF8EF', '#9A6A0A'
def star(x, y, s, fill, op=1):
    return f'<path d="M{x} {y-s} Q{x} {y} {x+s} {y} Q{x} {y} {x} {y+s} Q{x} {y} {x-s} {y} Q{x} {y} {x} {y-s}Z" fill="{fill}" opacity="{op}"/>'
def face(cx, cy, r, eyes='open', mouth='talk', ink=IND, cheek=ROSE, sun=SUN, mono=False):
    k = r / 24.0
    def X(v): return cx + v * k
    def Y(v): return cy + v * k
    out = ''
    ex = [-6.5, 6.5]; ey = -5; dx, dy = 1.4, -1.2   # glance up and to the right
    for e in ex:
        if eyes == 'open':
            out += f'<ellipse cx="{X(e+dx):.1f}" cy="{Y(ey+dy):.1f}" rx="{3.1*k:.1f}" ry="{4.3*k:.1f}" fill="{ink}"/>'
            if not mono: out += f'<circle cx="{X(e+dx+1.1):.1f}" cy="{Y(ey+dy-1.6):.1f}" r="{1.1*k:.1f}" fill="{CREAM}"/>'
        elif eyes == 'happy':
            out += f'<path d="M{X(e-3.4):.1f} {Y(ey+1):.1f} Q{X(e):.1f} {Y(ey-5):.1f} {X(e+3.4):.1f} {Y(ey+1):.1f}" fill="none" stroke="{ink}" stroke-width="{2.6*k:.1f}" stroke-linecap="round"/>'
        elif eyes == 'closed':
            out += f'<path d="M{X(e-3.4):.1f} {Y(ey-1):.1f} Q{X(e):.1f} {Y(ey+3):.1f} {X(e+3.4):.1f} {Y(ey-1):.1f}" fill="none" stroke="{ink}" stroke-width="{2.4*k:.1f}" stroke-linecap="round"/>'
        elif eyes == 'side':  # listening: eyes toward the learner at the left
            out += f'<ellipse cx="{X(e-1.6):.1f}" cy="{Y(ey):.1f}" rx="{3.1*k:.1f}" ry="{4.3*k:.1f}" fill="{ink}"/>'
            if not mono: out += f'<circle cx="{X(e-2.6):.1f}" cy="{Y(ey-1.6):.1f}" r="{1.1*k:.1f}" fill="{CREAM}"/>'
    if not mono and cheek:
        out += f'<circle cx="{X(-12.5):.1f}" cy="{Y(4):.1f}" r="{3.2*k:.1f}" fill="{cheek}" opacity=".9"/><circle cx="{X(13.5):.1f}" cy="{Y(3.5):.1f}" r="{3.2*k:.1f}" fill="{cheek}" opacity=".9"/>'
    if mouth == 'talk':
        out += f'<path d="M{X(-5.5):.1f} {Y(4):.1f} Q{X(1):.1f} {Y(15):.1f} {X(7.5):.1f} {Y(4):.1f} Z" fill="{ink}"/>'
        if not mono: out += f'<ellipse cx="{X(1):.1f}" cy="{Y(9.6):.1f}" rx="{3*k:.1f}" ry="{1.7*k:.1f}" fill="{cheek or ROSE}"/>'
    elif mouth == 'smile':
        out += f'<path d="M{X(-5):.1f} {Y(5):.1f} Q{X(1):.1f} {Y(11):.1f} {X(7):.1f} {Y(5):.1f}" fill="none" stroke="{ink}" stroke-width="{2.6*k:.1f}" stroke-linecap="round"/>'
    elif mouth == 'small':
        out += f'<ellipse cx="{X(1):.1f}" cy="{Y(7):.1f}" rx="{2.4*k:.1f}" ry="{2*k:.1f}" fill="{ink}"/>'
    elif mouth == 'grin':
        out += f'<path d="M{X(-7):.1f} {Y(3.5):.1f} Q{X(1):.1f} {Y(17):.1f} {X(9):.1f} {Y(3.5):.1f} Z" fill="{ink}"/>'
        if not mono: out += f'<ellipse cx="{X(1):.1f}" cy="{Y(10.5):.1f}" rx="{3.4*k:.1f}" ry="{2*k:.1f}" fill="{cheek or ROSE}"/>'
    return out
def body(cx, cy, r, fill, edge=None):
    # sun disc with a speech tail at the lower left
    import math
    a1 = math.radians(127); a2 = math.radians(150)
    p1 = (cx + r*math.cos(a1), cy + r*math.sin(a1)); p2 = (cx + r*math.cos(a2), cy + r*math.sin(a2))
    tip = (cx - r*1.0, cy + r*1.06)
    st = f' stroke="{edge}" stroke-width="{r*0.06:.2f}" stroke-linejoin="round"' if edge else ''
    return (f'<path d="M{cx} {cy-r} A{r} {r} 0 1 1 {p1[0]:.2f} {p1[1]:.2f} L{tip[0]:.2f} {tip[1]:.2f} L{p2[0]:.2f} {p2[1]:.2f} A{r} {r} 0 0 1 {cx} {cy-r}Z" fill="{fill}"{st}/>')
def antenna(cx, cy, r, col, starcol, mono=False):
    k = r/24
    x0, y0 = cx + 5*k, cy - r + 1*k; x1, y1 = cx + 8*k, cy - r - 9*k
    return (f'<path d="M{x0:.1f} {y0:.1f} L{x1:.1f} {y1:.1f}" stroke="{col}" stroke-width="{3*k:.1f}" stroke-linecap="round"/>'
            + star(round(x1 + 0.6*k, 1), round(y1 - 3.2*k, 1), round(5.2*k, 1), starcol))
def waves(cx, cy, r, col):
    k = r/24; x = cx + r + 5*k
    return (f'<g fill="none" stroke="{col}" stroke-linecap="round" stroke-width="{2.8*k:.1f}">'
            f'<path d="M{x:.1f} {cy+1*k:.1f} Q{x+3.6*k:.1f} {cy+6*k:.1f} {x:.1f} {cy+11*k:.1f}"/>'
            f'<path d="M{x+6*k:.1f} {cy-3*k:.1f} Q{x+12*k:.1f} {cy+6*k:.1f} {x+6*k:.1f} {cy+15*k:.1f}"/></g>')

def icon(v='A', mono=False, k='x', eyes='open', mouth='talk', hill=True):
    bg = 'var(--mbg)' if mono else IND; fg = 'var(--mfg)' if mono else SUN
    hillc = 'var(--mfg)' if mono else HILL; ink = bg if mono else IND
    cx, cy, r = (50, 50, 23) if v == 'C' else (54, 52, 24)
    aura = '' if mono else (f'<defs><radialGradient id="AU{k}"><stop offset="0%" stop-color="{SUN}" stop-opacity=".5"/><stop offset="100%" stop-color="{SUN}" stop-opacity="0"/></radialGradient></defs>'
                            f'<circle cx="{cx}" cy="{cy}" r="40" fill="url(#AU{k})"/>')
    deco = ''
    if not mono and v == 'A': deco += star(30, 28, 3, CREAM, .9)
    if v in ('B', 'C'): deco += antenna(cx, cy, r, fg, 'var(--mfg)' if mono else CREAM, mono)
    if v == 'C': deco += waves(cx, cy, r, 'var(--mfg)' if mono else CREAM)
    h = f'<path d="M0 84 Q54 75 108 84 V108 H0Z" fill="{hillc}"/>' if hill else ''
    return (f'<svg viewBox="0 0 108 108"><rect width="108" height="108" fill="{bg}"/>{aura}{deco}{body(cx, cy, r, fg)}'
            f'{face(cx, cy, r, eyes, mouth, ink=ink, mono=mono)}{h}</svg>')
