import json, os, re, sys
S = sys.argv[1]; P = S + '/ds/project'; C = P + '/components'
SC = json.load(open(S + '/desk/screens.json'))
# 1) desk.css joins bundle.css under its own section (replace if already there)
b = open(C + '/bundle.css').read(); mark = '/* ===== Desktop and web (dk-) ===== */'
if mark in b: b = b[:b.index(mark)].rstrip() + '\n'
b += '\n' + mark + '\n' + open(S + '/tools/desk.css').read()
open(C + '/bundle.css', 'w').write(b)
FIT = ('<style>.dk-fit{position:relative;width:100%;aspect-ratio:16/10;overflow:hidden;background:#0b0816}'
       '.dk-fit>.dk-root{position:absolute;top:0;left:0;width:1440px;height:900px;transform-origin:top left;transform:scale(.6)}'
       '.dk-fit .dk-screen{display:block}.dk-fit .dk-perm{display:none}</style>')
JS = ("<script>(function(){function f(){document.querySelectorAll('.dk-fit').forEach(function(w){w.firstElementChild.style.transform='scale('+(w.clientWidth/1440)+')'})}"
      "f();addEventListener('resize',f);new ResizeObserver(f).observe(document.body)})()</script>")
CARDS = [
 ('DesktopMeetBolo', 'start', 'One click before Bolo speaks (browser autoplay rule)',
  'The first screen on a computer. Browsers block sound until the user clicks, so desktop opens on one calm screen with the logo and a single "Meet Bolo" button; that click unlocks audio and starts the voice welcome. The logo face appears here and in the splash only.'),
 ('DesktopConsent', 'consent', 'Onboarding as one centred card over the blurred scene',
  'Every onboarding step is a centred card (480px, or 600px wide) over the blurred dusk, with a step count. The two consent answers are equal and side by side. The browser asks for the microphone right after this card explains why.'),
 ('DesktopMicPicker', 'mic', 'Choose the microphone, live level, headphones tip',
  'Desktop only. Computers have several microphones (laptop, headset, webcam), so the mic check lists them, shows a live level and remembers the choice. One kind line suggests headphones: laptop speakers feed Bolo\'s voice back into the mic.'),
 ('DesktopHome', 'home', 'Sidebar, wide scene hero, Space starts the talk',
  'The phone\'s bottom tabs become a sidebar with the same three tabs, the free-talk counter and a code to open Bolo on the phone. The scene is a wide hero with the Talk button inside it. Content never stretches past 1120px.'),
 ('DesktopTalk', 'talk', 'Full-window talk with a floating keyboard dock',
  'The talk fills the window: no sidebar, captions at the top centre at 760px reading width, and the controls in a floating dock with their keys written underneath: Space (done), P (pause), R (repeat), Esc (end). Switching tabs pauses the talk and closes the mic.'),
 ('DesktopFeedback', 'debrief', 'Two panes: the list on the left, the chosen card on the right',
  'Strength, three fixes and skills sit in a list on the left; the chosen card opens on the right with your clip and the model clip side by side. ↑↓ moves through the list, T tries the fix. Nothing needs a swipe.'),
 ('DesktopDrill', 'drill', 'One drill card on a quiet page, keys 1 and 2 answer',
  'Drills keep the phone\'s one-card rule on a quiet page, with Close, progress and Skip above the card. Number keys pick an answer.'),
 ('DesktopSaveProgress', 'save', 'Save progress after the first talk',
  'The same save step as the phone, as a centred card: what gets kept, Google or phone number, and "Not now" (progress then stays in this browser only).'),
 ('DesktopReminder', 'reminder', 'Browser notifications, asked for kindly',
  'After the first talk: four time slots, a preview of the exact notification, and a "Keep it private" switch that shortens it to "Time for today\'s talk". "Turn on reminders" opens the browser\'s own permission box; the calendar and the phone are quiet alternatives. Never ask for permission when the page opens.'),
 ('DesktopProgress', 'progress', 'Progress as a 12-column dashboard',
  'Voice comparison and effort side by side, four skill trends in one row, patterns and history next to each other. The grid drops to 2 columns from 1024 to 1279px.'),
 ('DesktopMe', 'me', 'Me as a settings page with its own section list',
  'A section list on the left, settings on the right. A desktop group adds the microphone, how a turn ends, pause on tab switch, and keyboard shortcuts.'),
 ('DesktopPlans', 'plans', 'Plans compared side by side; bKash shows a QR code',
  'All plans side by side with the price as the biggest number. On a computer bKash shows a QR code to scan with the bKash app; card payment is the other choice. Cancelling still takes two clicks.'),
]
for name, sid, sub, about in CARDS:
    d = f'{C}/{name}'; os.makedirs(d, exist_ok=True)
    open(d + '/preview.html', 'w').write(
        f'<!-- @dsCard group="Desktop" height=600 subtitle="{sub}" page -->\n<!doctype html>\n<html lang="en">\n<head><meta charset="utf-8"><title>{name} — Bolo</title>{FIT}</head>\n<body>\n'
        f'<div class="dk-fit"><div class="dk-root">{SC[sid]}</div></div>\n{JS}\n</body>\n</html>\n')
    open(d + '/README.md', 'w').write(f'# {name}\n\n{about}\n\n- Canvas: 1440 × 900 (the preview scales it down to fit). Layout rules, breakpoints and the keyboard map are in *Desktop and web*.\n- Classes: `dk-*` in `bundle.css` (section "Desktop and web"), built on the same `bo-*` components and tokens as the phone.\n- Clickable version: the Desktop prototype card (screen "{sid}").\n')
# 2) prototypes as live cards
PROTOS = [('PrototypePhone', S + '/proto/index.html', 860, 'Clickable phone prototype, 45 screens · locked v1, 7 Nov 2026',
  'The full phone journey for Phase 0 testing: first launch, the conversation, feedback, drills, save progress, reminders, Home, Progress and Me, plans and invites, and edge cases.',
  ['On a real phone it opens full screen. The small dot at the top opens the facilitator panel: six test tasks, a jump list to every screen, Day and Night, an optional browser voice for Bolo, and a first-word timer for the 60-second target.',
   'It does not listen. Testers speak out loud and tap Done; the facilitator watches what they say.',
   'Standalone link: https://claude.ai/artifact/RffVZje2wveRZQTAcDz1Qk']),
 ('PrototypeDesktop', S + '/desk/index.html', 760, 'Clickable desktop browser prototype, 18 screens · locked v1, 7 Nov 2026',
  'The desktop browser journey: Meet Bolo, onboarding with the mic picker, the full-window talk with keyboard shortcuts, two-pane feedback, a drill, save progress, browser reminders, Home, Progress, Me and plans.',
  ['Keyboard: Space talks or ends your turn, Esc ends, P pauses, R repeats, ↑↓ moves through feedback, T tries a fix, 1–4 answer, ? lists them all. Every key also has a button.',
   'The "Prototype" button (bottom right) has Restart, Night theme, an optional browser voice and a jump list. Windows smaller than 1180 × 700 see the 1280 × 800 canvas scaled to fit.',
   'Standalone links: prototype https://claude.ai/artifact/4Ra7kdHhKA9JPGWNznSU6b · design write-up https://claude.ai/artifact/2VWsurjGAndsSYPPE35dNx'])]
for name, src, h, sub, about, notes in PROTOS:
    d = f'{C}/{name}'; os.makedirs(d, exist_ok=True)
    html = open(src).read()
    assert html.startswith('<!DOCTYPE html>')
    open(d + '/preview.html', 'w').write(f'<!-- @dsCard group="Prototypes" height={h} subtitle="{sub}" page -->\n' + html)
    open(d + '/README.md', 'w').write(f'# {name}\n\n{about}\n\n' + '\n'.join('- ' + n for n in notes) +
        '\n\n**Locked.** This is the approved v1 baseline (7 Nov 2026). Change a screen in its own card first; the prototype is regenerated from those screens as v2, never edited by hand.\n')
print('ok', len(CARDS), 'desktop cards,', len(PROTOS), 'prototypes')
