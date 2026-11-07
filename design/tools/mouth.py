# Bolo mouth diagrams: close-up side view, facing right. viewBox 0 0 200 150.
# Static anatomy = ink outlines. The one part that moves = hills-soft fill, hills-ink edge.
NOSE = 'M166 0 Q186 14 179 26 Q172 36 160 38'
PALATE = 'M141 47 Q137 36 128 34 Q108 26 80 26 Q50 28 32 40 Q24 46 20 56'
UP_TOOTH = 'M141 46 L150 47 Q156 60 154 75 Q147 71 143 59 Z'

def up_lip(f=0):
    # outer: subnasale → vermilion → stomion; inner back to sulcus
    return (f'M160 38 C{164+f} 46 {170+f} 55 {169+f} 64 Q{168+f} 71 {162+f*0.9} 72 '
            f'Q154 72 151 63 L150 47 Q155 41 160 38 Z')
def lo_lip(top=(162, 75), f=0, inner=(152, 82)):
    tx, ty = top; ix, iy = inner
    return (f'M{tx} {ty} Q{170+f} {ty+1} {169+f*0.7} {ty+11} Q{167+f*0.4} {ty+21} 158 100 '
            f'L151 101 Q{ix-2} {iy+8} {ix} {iy} Q{(ix+tx)/2-1} {ty} {tx} {ty} Z')
CHIN = 'M158 100 Q167 112 167 124 Q165 141 146 147'
FLOOR = 'M141 101 Q122 113 92 115 Q50 117 8 118'
def lo_tooth():
    return 'M140 101 L149 101 Q153 88 150 76 Q144 80 142 88 Z'

TONGUE = {
 'rest': 'M8 84 Q36 54 88 52 Q124 53 137 72 Q142 81 136 88 Q126 94 118 100 Q108 108 90 112 L8 116 Z',
 'w':    'M8 70 Q22 40 56 36 Q92 36 118 52 Q136 66 137 80 Q135 88 126 94 Q114 104 90 112 L8 116 Z',
 'th':   'M8 84 Q36 56 90 55 Q128 58 150 70 Q163 74 162 79 Q156 84 144 87 Q128 92 118 100 Q108 108 90 112 L8 116 Z',
 's':    'M8 80 Q34 48 80 42 Q114 38 132 44 Q141 48 140 54 Q137 60 130 66 Q122 76 118 90 Q112 106 90 112 L8 116 Z',
 'sh':   'M8 80 Q30 46 72 38 Q100 33 118 38 Q130 44 131 54 Q129 62 123 69 Q116 80 114 92 Q110 106 90 112 L8 116 Z',
}
def arrow(d, x, y, ang=0):
    return (f'<path class="air" d="{d}"/>'
            f'<path class="airhead" d="M0 0 L-7 -4 L-7 4 Z" transform="translate({x} {y}) rotate({ang})"/>')
BUZZ = ('<g class="buzz"><path d="M48 122 q4 -4 0 -8 q-4 -4 0 -8"/>'
        '<path d="M57 124 q5 -5 0 -10 q-5 -5 0 -10"/><path d="M66 122 q4 -4 0 -8 q-4 -4 0 -8"/></g>')

SOUNDS = {
 # kind: (tongue, jaw drop, upper-lip forward, lower-lip spec, moving part, rest offset, air)
 'v':  dict(tongue='rest', jaw=0, f=0, lo=dict(top=(155, 73), f=-1, inner=(151, 82)), move='lower', rest=(6, 8),
            air=arrow('M96 64 Q132 66 150 72 Q160 74 184 73', 190, 73)),
 'w':  dict(tongue='w', jaw=3, f=9, lo=dict(top=(170, 77), f=8, inner=(156, 86)), move='lips', rest=(-8, 0),
            air=arrow('M84 58 Q130 64 160 72 Q172 74 186 74', 192, 74)),
 'th': dict(tongue='th', jaw=7, f=0, lo=dict(top=(162, 75), f=0, inner=(152, 82)), move='tongue', rest=(-14, 6),
            air=arrow('M70 44 Q120 48 150 64 Q162 70 184 70', 190, 70)),
 's':  dict(tongue='s', jaw=0, f=0, lo=dict(top=(162, 75), f=0, inner=(152, 82)), move='tongue', rest=(0, 14),
            air=arrow('M44 38 Q90 30 124 36 Q140 42 146 58 Q152 72 160 74 Q172 75 184 75', 190, 75)),
 'sh': dict(tongue='sh', jaw=0, f=5, lo=dict(top=(165, 76), f=4, inner=(153, 83)), move='tongue', rest=(0, 14),
            air=arrow('M40 36 Q80 28 112 31 Q134 38 142 56 Q150 72 162 74 Q174 76 186 76', 192, 76)),
}
SOUNDS['z'] = dict(SOUNDS['s'], buzz=True)
SOUNDS['cluster'] = dict(SOUNDS['s'])

LABEL = {
 'v': 'Side view: top teeth resting on the lower lip, air passing between them',
 'w': 'Side view: both lips pushed forward into a small circle, not touching the teeth',
 'th': 'Side view: tongue tip between the top and bottom teeth, air passing over it',
 's': 'Side view: tongue tip just behind the top teeth with a narrow gap, air hissing out',
 'z': 'Side view: tongue as for s, with a buzz in the throat',
 'sh': 'Side view: tongue pulled slightly back, lips a little forward, air rushing out',
 'cluster': 'Side view: tongue ready for s, so the word starts straight with a hiss',
}

def up_mass(f=0):
    return (f'M28 4 L168 4 Q186 14 179 26 Q172 36 160 38 '
            f'C{164+f} 46 {170+f} 55 {169+f} 64 Q{168+f} 71 {162+f*0.9} 72 '
            f'Q154 72 151 63 L150 47 L141 47 Q137 36 128 34 Q108 26 80 26 Q50 28 32 40 L28 42 Z')
def lo_mass(top=(162, 75), f=0, inner=(152, 82)):
    tx, ty = top; ix, iy = inner
    return (f'M{tx} {ty} Q{170+f} {ty+1} {169+f*0.7} {ty+11} Q{167+f*0.4} {ty+21} 158 100 '
            f'Q167 112 167 124 Q165 141 146 147 L146 150 L28 150 L28 118 Q50 117 92 115 Q122 113 141 101 '
            f'L151 101 Q{ix-2} {iy+8} {ix} {iy} Q{(ix+tx)/2-1} {ty} {tx} {ty} Z')

def svg(kind, cls='bo-mouth', view='36 8 164 123'):
    c = SOUNDS[kind]; j = c['jaw']; mv = c['move']
    tongue = f'<path class="{"move" if mv == "tongue" else "tongue"}" d="{TONGUE[c["tongue"]]}"/>'
    ul = f'<path class="move" d="{up_lip(c["f"])}"/>' if mv == 'lips' else ''
    ll = f'<path class="move" d="{lo_lip(**c["lo"])}"/>' if mv in ('lips', 'lower') else ''
    lower = (f'<g transform="translate(0 {j})"><path class="flesh" d="{lo_mass(**c["lo"])}"/>'
             f'<path class="tooth" d="{lo_tooth()}"/>{ll}</g>')
    upper = f'<path class="flesh" d="{up_mass(c["f"])}"/><path class="tooth" d="{UP_TOOTH}"/>{ul}'
    body = f'{tongue}{lower}{upper}{c["air"]}{BUZZ if c.get("buzz") else ""}'
    return (f'<svg class="{cls}" viewBox="{view}" role="img" aria-label="{LABEL[kind]}" '
            f'style="--rest-x:{c["rest"][0]}px;--rest-y:{c["rest"][1]}px">{body}</svg>')
