import json, re, sys
S = sys.argv[1]; P = S + '/ds/project'
SC = json.load(open(S + '/desk/screens.json')); F = json.load(open(S + '/fontdl/final.json'))
tokens = open(P + '/tokens.css').read(); bundle = open(P + '/components/bundle.css').read()
imp = re.search(r"@import url\('([^']+)'\);", bundle).group(1); bundle = bundle.replace(f"@import url('{imp}');", '')
desk = open(S + '/tools/desk.css').read()
PROTO_D = 'https://claude.ai/artifact/4Ra7kdHhKA9JPGWNznSU6b'; PROTO_M = 'https://claude.ai/artifact/RffVZje2wveRZQTAcDz1Qk'; DS = 'https://claude.ai/artifact/EWmxzbHCfzhn5b1q65zspd'
FAV = F['notif'].replace('<svg xmlns="http://www.w3.org/2000/svg"', '<svg width="14" height="14"').replace('#FFFFFF', '#F4B942')
n = [0]
def frame(sid, cap, theme='daylight', title=None):
    n[0] += 1; h = SC[sid]
    for i in set(re.findall(r'\bid="([^"]+)"', h)):
        h = h.replace(f'id="{i}"', f'id="{i}-f{n[0]}"').replace(f'url(#{i})', f'url(#{i}-f{n[0]})')
    return (f'<figure class="shot"><div class="chrome"><span class="dots"><i></i><i></i><i></i></span><span class="tab">{FAV}Bolo</span><span class="url">bolo.app</span></div>'
            f'<div class="fw"><div class="frame dk-root" data-theme="{theme}">{h}</div></div><figcaption><b>{title or ""}</b>{cap}</figcaption></figure>')
css = f'''{tokens}
{bundle}
{desk}
body {{ margin: 0; background: #f2eef7; color: #1f1433; font-family: 'Anek Bangla', system-ui, sans-serif; -webkit-font-smoothing: antialiased; }}
.doc {{ max-width: 1040px; margin: 0 auto; padding: 56px 32px 96px; }}
.doc > h1 {{ font: 750 46px/50px 'Anek Bangla'; letter-spacing: -.02em; margin: 0 0 14px; }}
.doc > h2 {{ font: 700 30px/36px 'Anek Bangla'; letter-spacing: -.01em; margin: 64px 0 14px; }}
.doc > h3 {{ font: 650 20px/26px 'Anek Bangla'; margin: 28px 0 8px; }}
.doc > p, .doc > ul > li, .doc > ol > li, .note li {{ font: 400 17px/27px 'Anek Bangla'; color: #3a3048; }}
.doc > p.lead {{ font-size: 20px; line-height: 31px; color: #5a4e6e; max-width: 820px; }}
.doc > p b, .doc > ul > li b, .doc > ol > li b {{ color: #1f1433; }}
.links {{ display: flex; gap: 12px; flex-wrap: wrap; margin: 22px 0 8px; }}
.links a {{ display: inline-flex; align-items: center; height: 44px; padding: 0 18px; border-radius: 999px; background: #1d1236; color: #fff8ef; font: 650 15px/1 'Anek Bangla'; text-decoration: none; }}
.links a.alt {{ background: #fff; color: #1f1433; box-shadow: inset 0 0 0 1.5px #d9d1e6; }}
.doc table {{ width: 100%; border-collapse: collapse; margin: 14px 0; background: #fff; border-radius: 16px; overflow: hidden; box-shadow: 0 1px 2px rgba(31,20,51,.06); }}
.doc th {{ text-align: left; font: 650 13px/18px 'Anek Bangla'; color: #5b3f8c; padding: 12px 16px; background: #faf8fc; border-bottom: 1px solid #ebe6f2; }}
.doc td {{ padding: 12px 16px; border-bottom: 1px solid #f1edf6; font: 400 15px/22px 'Anek Bangla'; color: #3a3048; vertical-align: top; }}
.doc td b {{ font-weight: 650; }}
.kbd {{ display: inline-block; min-width: 22px; padding: 1px 7px; border-radius: 6px; background: #fff; box-shadow: inset 0 -2px 0 #e2dcea, 0 0 0 1px #e2dcea; font: 650 13px/20px 'Anek Bangla'; text-align: center; }}
.decisions {{ counter-reset: d; list-style: none; padding: 0; display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }}
.decisions li {{ background: #fff; border-radius: 18px; padding: 18px 20px 18px 62px; position: relative; box-shadow: 0 1px 2px rgba(31,20,51,.06); font-size: 15px; line-height: 23px; }}
.decisions li::before {{ counter-increment: d; content: counter(d); position: absolute; left: 18px; top: 18px; width: 30px; height: 30px; border-radius: 50%; background: #f4b942; color: #2a1a00; font: 750 15px/30px 'Anek Bangla'; text-align: center; }}
.decisions li b {{ display: block; font-size: 17px; margin-bottom: 2px; }}
.shot {{ margin: 28px 0 0; }}
.chrome {{ height: 38px; background: #e4dfeb; border-radius: 14px 14px 0 0; display: flex; align-items: center; gap: 14px; padding: 0 14px; font: 600 13px/1 'Anek Bangla'; color: #3a3048; }}
.dots i {{ display: inline-block; width: 11px; height: 11px; border-radius: 50%; background: #c9c1d6; margin-right: 6px; }}
.tab {{ display: inline-flex; align-items: center; gap: 8px; background: #f7f4fb; height: 28px; padding: 0 14px; border-radius: 9px 9px 0 0; margin-top: 10px; }}
.url {{ flex: 1; background: #f7f4fb; height: 26px; border-radius: 999px; display: flex; align-items: center; padding: 0 14px; color: #5a4e6e; max-width: 420px; }}
.fw {{ width: 1040px; height: 650px; overflow: hidden; border-radius: 0 0 14px 14px; box-shadow: 0 18px 50px rgba(31,20,51,.16); position: relative; }}
.frame {{ position: absolute; top: 0; left: 0; width: 1440px; height: 900px; transform: scale(.7222); transform-origin: top left; }}
.frame .dk-screen {{ display: block; }}

.shot figcaption {{ font: 400 15px/23px 'Anek Bangla'; color: #5a4e6e; margin-top: 12px; max-width: 860px; }}
.shot figcaption b {{ color: #1f1433; font-weight: 650; margin-right: 6px; }}
.note {{ background: #1d1236; color: #e3d8ca; border-radius: 20px; padding: 22px 26px; margin-top: 18px; }}
.lock {{ display: inline-block; background: #fff; border-radius: 14px; padding: 10px 16px; font: 400 15px/22px 'Anek Bangla'; color: #3a3048; box-shadow: inset 0 0 0 1.5px #f4b942; margin: 4px 0 0; }} .lock b {{ color: #1f1433; }}
.note p, .note li {{ color: #e3d8ca; }} .note b {{ color: #f4b942; }}
@media (max-width: 1100px) {{ .fw {{ width: 100%; height: auto; aspect-ratio: 16/10; }} .frame {{ transform: scale(calc(100vw / 1440 * .92)); }} .decisions {{ grid-template-columns: 1fr; }} }}
'''
T = lambda rows, head: '<table><tr>' + ''.join(f'<th>{h}</th>' for h in head) + '</tr>' + ''.join('<tr>' + ''.join(f'<td>{c}</td>' for c in r) + '</tr>' for r in rows) + '</table>'
body = f'''<div class="doc">
<h1>Bolo on desktop</h1>
<p class="lead">How Bolo should work in a desktop browser, and why it should not be the phone app stretched wide. The same Neon Dusk system, voice and rules, rebuilt for a big screen, a keyboard, a headset and an office.</p>
<p class="lock"><b>Locked v1 · 7 Nov 2026.</b> Approved by the founder. Desktop browser ships first (beta), then the mobile browser, then the Android app. Changes from here go into v2.</p>
<div class="links"><a href="{PROTO_D}">Open the desktop prototype</a><a class="alt" href="{PROTO_M}">Mobile prototype</a><a class="alt" href="{DS}">Design system</a></div>

<h2>Who opens Bolo on a computer</h2>
<p>The PRD names the first desktop user directly: Kamrul, who "practises in his car or private cabin", "values discretion above everything", and leaves "Web version timing … for the Kamrul persona (desktop at work)" as an open question. Three moments shape the desktop design.</p>
{T([['<b>Kamrul</b>, 36, bank manager', 'His private cabin, before the branch opens, door half open', 'Nobody may hear or see "English practice". Tab title, sound and screen must stay quiet.'],
    ['<b>Rafi</b>, 27, engineer', 'His desk, 10 minutes before the client standup, headset on', 'Fast start, keyboard-only, rehearse the standup update. Laptop mic and speakers may echo.'],
    ['<b>Nusrat</b>, 22, student', 'Library or hostel laptop, longer IELTS sessions', 'More time per sitting: Progress and patterns matter more; a shared or noisy room.']], ['Who', 'Where and when', 'What it means for design'])}

<h2>Ten decisions</h2>
<ol class="decisions">
<li><b>A different layout, not a wide phone</b>The phone's bottom tabs become a sidebar; swiped cards become two panes; sheets become centred dialogs. Content never stretches past 1120px.</li>
<li><b>The scene goes cinematic</b>On a big screen the dusk is the reason to open the app. The talk fills the whole window with no sidebar, like a video call with no faces.</li>
<li><b>Keyboard first, mouse always</b>Space starts the talk and ends your turn, Esc ends, P pauses, R repeats, ↑↓ moves through feedback, 1–4 answers. Every key has a visible button too.</li>
<li><b>One click before Bolo speaks</b>Browsers block sound until the user clicks. Desktop opens on "Meet Bolo"; that click unlocks audio and starts the welcome.</li>
<li><b>Choose your microphone</b>Computers have several: laptop, headset, webcam. The mic check lists them, shows a live level, and remembers the choice.</li>
<li><b>Headphones, said kindly</b>Laptop speakers feed Bolo's voice back into the mic. The mic check suggests headphones once, for clarity and for privacy.</li>
<li><b>Discreet by default</b>The tab always says "Bolo", the tab icon has no face, nothing plays until Talk is pressed, and the talk pauses when you switch tabs.</li>
<li><b>Feedback you can read side by side</b>Strength, three fixes and skills sit in a list on the left; the chosen card opens on the right. Your clip and the model clip sit next to each other.</li>
<li><b>Reminders in the browser, asked for kindly</b>After the first talk, Bolo shows exactly what the notification will say, then the browser asks once. "Keep it private" hides what you talked about. The calendar and the phone stay as alternatives.</li>
<li><b>Same account, same Bolo</b>Talks, feedback, Day 1 clip and plan follow you between phone and computer. Plans compare side by side; bKash shows a QR code to scan.</li>
</ol>

<h2>Layout system</h2>
{T([['<b>1280px and wider</b>', 'Sidebar (248) + content up to 1120, centred', 'The main desktop layout; the talk and feedback take the whole window'],
    ['<b>1024–1279px</b>', 'Sidebar + content, smaller margins (32)', 'Laptops; the dashboard grid drops to 2 columns'],
    ['<b>640–1023px</b>', 'Top bar with the 3 tabs, one column up to 640', 'Narrow windows and tablets; dialogs stay centred'],
    ['<b>Under 640px</b>', 'The mobile layout', 'Also what 400% browser zoom becomes (WCAG 1.4.10 reflow)']], ['Window width', 'Layout', 'Notes'])}
<p>Grid: 12 columns, 24px gutters, the same 4dp spacing scale and radii as mobile. The scene art is drawn wide (about 21:9) with the important parts kept in the middle, so one master works for the desktop hero, the full-window talk and the phone crop.</p>

<h2>Keyboard and pointer</h2>
{T([['<span class="kbd">Space</span>', 'Home: start talking · Talk: done with my turn · Day 1: stop recording · Feedback: play my clip'],
    ['<span class="kbd">Enter</span>', 'Talk: done with my turn · Setup: next question'],
    ['<span class="kbd">Esc</span>', 'Talk: ask to end · Dialogs: close (the safe choice) · Feedback: back to Home'],
    ['<span class="kbd">P</span> <span class="kbd">R</span>', 'Pause and repeat Bolo\'s last line'],
    ['<span class="kbd">↑</span> <span class="kbd">↓</span>', 'Move between feedback cards'],
    ['<span class="kbd">T</span>', 'Try the fix on the open feedback card'],
    ['<span class="kbd">1</span>–<span class="kbd">4</span>', 'Pick an answer chip or a word-pair card'],
    ['<span class="kbd">?</span>', 'Show all shortcuts']], ['Key', 'What it does'])}
<p>Every interactive element has a hover state and the 2px <code>focus-ring</code> outline, a pointer cursor, and a tooltip if it is icon-only. Nothing depends on hover or on a gesture: swiping becomes a list, long-press does not exist. Tab order follows the visual order; dialogs trap focus and return it on close.</p>

<h2>Voice in a browser</h2>
<ul>
<li><b>Permission once, explained first.</b> The consent card says why, then the browser asks for the mic. If it is blocked, Bolo shows where to unblock it in the address bar, with a picture of the browser's own icon.</li>
<li><b>Capture settings.</b> Request the mic with echo cancellation, noise suppression and automatic gain on. Record in Opus (WebM) on Chrome, Edge and Firefox, and AAC (MP4) on Safari; stream to the server in short chunks so scoring can start before the talk ends.</li>
<li><b>Autoplay.</b> No sound before a click, ever. After "Meet Bolo" or "Talk", Bolo's voice and the ambient sound may play.</li>
<li><b>Tabs and sleep.</b> When the tab is hidden, the talk pauses and the mic closes; coming back shows "Paused while you were away". A laptop lid closing is treated the same way.</li>
<li><b>Latency.</b> Desktop users notice lag more, because they are sitting still. Keep the thinking arc rule (show it only after 600 ms) and target Bolo's reply within 1.5 s.</li>
</ul>

<h2>Discretion at work</h2>
<div class="note"><ul style="margin:0;padding-left:20px">
<li>The tab title is always <b>Bolo</b>, never "English practice" or the learner's goal; the tab icon is the faceless sun.</li>
<li>Nothing makes a sound until the learner presses a button; ambient sound is off on desktop until turned on.</li>
<li>Switching tabs pauses the talk and closes the mic.</li>
<li>Bolo never asks for notification permission when the page opens; it asks only after the first talk, from its own explanation card.</li>
<li>A notification shows "Bolo" and one third-person line. "Keep it private" turns that line into "Time for today's talk", never what you talked about.</li>
<li>The calendar event is titled "Talk with Bolo" with no other detail, so a shared calendar gives nothing away.</li>
</ul></div>

<h2>The screens</h2>
{frame('start', 'Desktop opens on one calm screen and a single button, because the browser needs a click before Bolo can speak. The logo face appears here and in the splash only, never inside the app.', title='Meet Bolo.')}
{frame('welcome', 'The same voice welcome as on the phone, on a wide dusk. Captions sit at the top centre at a comfortable reading width (760px), never across the whole screen.', title='Welcome.')}
{frame('consent', 'Onboarding runs as one centred card over the blurred scene, with a step count. The two consent answers are equal and side by side.', title='Consent.')}
{frame('mic', 'Desktop-only: choose the microphone, see a live level, and a one-line headphones tip. This replaces the phone\'s mic check.', title='Microphone.')}
{frame('home', 'Sidebar with the three tabs, the free counter and a code to open Bolo on the phone. The scene is a wide hero with the Talk button inside it; Space starts the talk. Below: the one next step, and the week.', title='Home.')}
{frame('talk', 'The talk fills the window: no sidebar, no cursor targets near the caption. Controls sit in a floating dock at the bottom centre, with the four keys written underneath.', title='Talking.')}
{frame('debrief', 'Feedback as two panes: the strength, three fixes and skills on the left; the chosen card on the right, clips side by side. Arrow keys move through it; nothing needs a swipe.', title='Feedback.')}
{frame('drill', 'Drills are a single card on a quiet page with Close, progress and Skip above it. Keys 1 and 2 answer.', title='Drill.')}
{frame('reminder', 'The same four time slots as the phone, a preview of the exact notification, and a "Keep it private" switch. "Turn on reminders" opens the browser\'s own permission box; the calendar and the phone are quiet alternatives.', title='Reminders.')}
{frame('progress', 'Progress becomes a dashboard: the voice comparison and effort side by side, four skill trends in one row, patterns and history next to each other.', title='Progress.')}
{frame('me', 'Me becomes a settings page with its own section list. A desktop group adds the microphone, how a turn ends, pause on tab switch, and shortcuts.', title='Me.')}
{frame('plans', 'Plans compare side by side, with the price as the biggest number. bKash shows a QR code on a computer; cancelling still takes 2 clicks.', title='Plans.')}
{frame('home', 'Night theme follows the system, as on the phone. Every colour pair keeps its contrast.', theme='dusk', title='Home, night.')}

<h2>What stays the same, what changes</h2>
{T([['Colours, type, spacing, radii, icons', 'Identical tokens', '—'],
    ['Voice and copy rules, no red, max 3 fixes', 'Identical', '—'],
    ['Navigation', 'Bottom tab bar', 'Sidebar (top bar under 1024px)'],
    ['Conversation', 'Scene, caption and mic ring on a tall screen', 'Full-window wide scene, floating dock, keyboard'],
    ['Feedback', 'One card per screen, swipe', 'Two panes, list + card, arrow keys'],
    ['Sheets', 'Bottom sheets', 'Centred dialogs, Esc closes'],
    ['First run', 'Bolo speaks straight away', '"Meet Bolo" click first (autoplay rule), mic picker'],
    ['Reminders', 'Phone notifications', 'Browser notification (asked after the first talk), or a calendar event'],
    ['Payment', 'bKash app switch', 'bKash QR code, or card'],
    ['Progress', 'One scrolling column', '12-column dashboard']], ['Part', 'Phone', 'Desktop browser'])}

<h2>Build notes</h2>
<ul>
<li><b>Tech.</b> The same Rive files for the companion and scenes (Rive has a web runtime); the scene needs a wide master. Web Audio for levels; MediaRecorder for capture.</li>
<li><b>Notifications.</b> Web Push with a service worker. The server sends at most one a day, only if there was no talk that day and, on the free plan, only when free talks are left. On a computer they arrive only while the browser is running, which is why the calendar stays as a choice. On iPhone, web notifications need Bolo added to the Home Screen (iOS 16.4+).</li>
<li><b>Install.</b> Make Bolo an installable web app (its own window and dock icon, no browser tabs), which also keeps it out of a shared browser history.</li>
<li><b>Performance.</b> First screen interactive within 2.5 s on an office laptop; the scene as one animated canvas, not dozens of elements.</li>
<li><b>Accessibility.</b> Keyboard-only and screen-reader runs (NVDA, VoiceOver) are part of every release, and the layout must reflow at 200% and 400% zoom.</li>
</ul>

<h2>Decided and still open</h2>
<ul>
<li><b>Decided (7 Nov):</b> desktop is in the closed beta; the build order is desktop browser, then mobile browser, then the Android app; browser notifications are allowed.</li>
<li><b>Open:</b> a "Rehearse a presentation" mode for desktop. Recommendation: not in the beta. Test demand first with a "Meeting rehearsal" scenario in the existing talk, and decide from how often testers pick it.</li>
</ul>
</div>'''
page = f'''<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Bolo on desktop</title>
<link rel="stylesheet" href="{imp}"><style>{css}</style></head><body>{body}</body></html>'''
open(S + '/desk/doc.html', 'w').write(page); print('doc ok', len(page) // 1024, 'KB')
