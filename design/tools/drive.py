import random
random.seed(7)
DEFS = ('<defs>'
 '<radialGradient id="bo-aura"><stop offset="0%" style="stop-color:var(--scene-sun);stop-opacity:.75"/><stop offset="42%" style="stop-color:var(--scene-sun);stop-opacity:.32"/><stop offset="100%" style="stop-color:var(--scene-sun);stop-opacity:0"/></radialGradient>'
 '<radialGradient id="bo-star"><stop offset="0%" style="stop-color:var(--scene-star);stop-opacity:.7"/><stop offset="100%" style="stop-color:var(--scene-star);stop-opacity:0"/></radialGradient>'
 '<radialGradient id="bo-bokeh"><stop offset="0%" style="stop-color:var(--scene-sun);stop-opacity:.55"/><stop offset="60%" style="stop-color:var(--scene-sun);stop-opacity:.18"/><stop offset="100%" style="stop-color:var(--scene-sun);stop-opacity:0"/></radialGradient>'
 '<radialGradient id="bo-tail"><stop offset="0%" style="stop-color:var(--scene-sky-low);stop-opacity:.9"/><stop offset="100%" style="stop-color:var(--scene-sky-low);stop-opacity:0"/></radialGradient>'
 '<linearGradient id="bo-dashglow" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" style="stop-color:var(--scene-sky-low);stop-opacity:.38"/><stop offset="100%" style="stop-color:var(--scene-sky-low);stop-opacity:0"/></linearGradient>'
 '</defs>')
# star dust: fewer than the hilltop (city glow), high in the windscreen only
DUST = [(84,74,1.2,.6),(122,104,1.0,.5),(150,66,1.4,.7),(196,92,1.0,.5),(232,70,1.2,.6),(262,112,1.4,.5),(108,140,1.0,.6),(214,136,1.2,.5),(286,150,1.0,.6),(140,176,1.0,.5),(176,160,1.4,.7),(250,186,1.0,.5),(96,198,1.2,.5),(196,214,1.0,.6),(272,222,1.0,.5),(124,236,1.0,.6)]
dust = ''.join(f'<circle class="dust" cx="{x}" cy="{y}" r="{r}" opacity="{o}"/>' for x,y,r,o in DUST)
def spark(x, y, s, delay):
    return (f'<g class="twinkle" style="animation-delay:{delay}s"><circle class="starglow" cx="{x}" cy="{y}" r="{s*1.6:.1f}"/>'
            f'<path class="spark" d="M{x} {y-s} Q{x} {y} {x+s} {y} Q{x} {y} {x} {y+s} Q{x} {y} {x-s} {y} Q{x} {y} {x} {y-s}Z"/></g>')
TW = spark(110, 120, 6.5, 0) + spark(250, 96, 5, 1.6)
# city skyline on the horizon, violet, with a few warm windows
SKY = 'M60 300 L60 276 L76 276 L76 264 L92 264 L92 282 L104 282 L104 252 L118 252 L118 270 L132 270 L132 286 L146 286 L146 276 L158 276 L158 292 L202 292 L202 270 L214 270 L214 258 L230 258 L230 280 L242 280 L242 246 L256 246 L256 268 L270 268 L270 284 L284 284 L284 262 L300 262 L300 300 Z'
WIN = [(66,282),(70,290),(84,270),(108,258),(112,266),(110,276),(122,276),(136,292),(206,276),(218,264),(222,272),(246,252),(250,262),(246,272),(260,274),(288,268),(292,278)]
win = ''.join(f'<rect class="window" x="{x}" y="{y}" width="2.5" height="3.5" rx=".6"/>' for x,y in WIN)
BOKEH = [(128,304,16),(232,300,20),(282,310,14),(86,310,12),(198,296,10)]
bokeh = ''.join(f'<circle class="bokeh" cx="{x}" cy="{y}" r="{r}"/>' for x,y,r in BOKEH)
def tail(x, y, gap, s):
    return ''.join(f'<circle class="tailglow" cx="{x+dx}" cy="{y}" r="{s*3}"/><circle class="tail" cx="{x+dx}" cy="{y}" r="{s}"/>' for dx in (0, gap))
TAILS = tail(150, 322, 14, 1.8) + tail(205, 314, 9, 1.3)
ROAD = 'M172 300 L188 300 L330 420 L30 420 Z'
LANES = ''.join(f'<path class="lane" d="M{180-0.5*w} {y} L{180+0.5*w} {y} L{180+0.5*w2} {y+h} L{180-0.5*w2} {y+h} Z"/>' for y,h,w,w2 in [(306,6,1.2,1.8),(320,10,2.2,3.2),(342,16,3.8,5.4)])
# car interior: roof, two pillars, mirror, dashboard with a soft glow
INTERIOR = ('<path class="cabin" d="M0 0 L360 0 L360 40 Q180 26 0 40 Z"/>'
 '<path class="cabin" d="M0 34 L74 34 Q66 120 26 360 L0 360 Z"/>'
 '<path class="cabin" d="M360 34 L286 34 Q294 120 334 360 L360 360 Z"/>'
 '<path class="cabin" d="M176 30 L184 30 L183 44 L177 44 Z"/><rect class="cabin" x="150" y="42" width="60" height="17" rx="8.5"/>'
 '<rect class="mirrorglint" x="156" y="46" width="48" height="4" rx="2"/>'
 '<path class="dashglow" d="M0 352 Q180 334 360 352 L360 392 L0 392 Z"/>'
 '<path class="cabin" d="M0 372 Q180 352 360 372 L360 520 L0 520 Z"/>')
# the Buddy drives (right-hand drive, Bangladesh): three-quarter from behind, rim-lit on the side facing the sun
DRIVER = ('<g transform="translate(244 300)">'
 '<path class="figure" d="M-66 240 L-60 112 C-60 74 -40 48 -6 44 C26 42 48 60 56 112 L62 240 Z"/>'
 '<path class="figure" d="M-14 44 L-12 30 L10 30 L12 44 Z"/>'
 '<ellipse class="figure" cx="0" cy="6" rx="24" ry="27"/>'
 '<path class="rim" d="M-21 -8 A24 27 0 0 1 -6 -20"/>'
 '<path class="rim" d="M-23 4 A24 27 0 0 0 -18 22"/>'
 '<path class="rim" d="M-52 96 C-50 72 -36 54 -12 46"/>'
 '</g>'
 '<ellipse class="wheel" cx="236" cy="392" rx="60" ry="20"/>'
 '<path class="rim" d="M182 384 Q196 372 220 370" style="opacity:.55"/>'
 '<path class="figure" d="M190 380 C196 368 206 364 214 368 L210 382 Z"/>'
 '<path class="figure" d="M286 382 C282 368 272 362 262 366 L266 380 Z"/>')

def drive_svg(view_aspect='xMidYMax slice'):
    return (f'<svg class="bo-scene__art" viewBox="0 0 360 520" preserveAspectRatio="{view_aspect}" aria-hidden="true">'
            f'{DEFS}{dust}{TW}<circle class="aura" cx="180" cy="300" r="120"/><circle class="sun" cx="180" cy="300" r="40"/>'
            f'<path class="far" d="{SKY}"/>{win}{bokeh}<path class="near" d="{ROAD}"/>{LANES}{TAILS}{INTERIOR}{DRIVER}</svg>')
