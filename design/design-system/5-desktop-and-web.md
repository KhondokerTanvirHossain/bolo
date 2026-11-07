# Desktop and web

Bolo ships in the browser first. **Build order (decided 7 Nov 2026): desktop browser (closed beta), then mobile browser, then the Android app.** Every token, component, copy rule and the no-red rule are the same on every platform. This page covers what changes on a computer and in a browser.

Clickable references: the **Desktop prototype** and **Phone prototype** cards (group Prototypes), locked v1. The full design write-up with personas and reasoning: https://claude.ai/artifact/2VWsurjGAndsSYPPE35dNx

## A different layout, not a wide phone

| Window width | Layout | Notes |
| --- | --- | --- |
| 1280px and up | Sidebar (248) + content up to 1120, 48 margins | Office monitors and most laptops |
| 1024–1279px | Sidebar + content, 32 margins | The dashboard grid drops to 2 columns |
| 640–1023px | Top bar with the 3 tabs, one column up to 640 | Narrow windows and tablets; dialogs stay centred |
| Under 640px | The phone layout | Also what 400% browser zoom becomes (WCAG 1.4.10 reflow) |

- **Navigation.** The phone's bottom tab bar becomes a sidebar with the same three tabs (Talk, Progress, Me), the free-talk counter and a code to open Bolo on the phone.
- **Grid.** 12 columns, 24px gutters, the same 4dp spacing scale and radii. Use `dk-span-4/6/8/12`.
- **Sheets become dialogs.** Bottom sheets turn into centred cards (`dk-card`, 480px; `dk-card--wide`, 600px) over a dimmed, blurred scene. Esc closes them with the safe choice.
- **Swipes become lists.** Feedback is two panes: the list on the left, the chosen card on the right. Nothing depends on hover, swipe or long-press.
- **The scene goes cinematic.** The talk fills the window with no sidebar; captions sit top centre at 760px reading width; the controls float in a dock (`dk-dock`) at the bottom centre. The scene art is drawn wide (about 21:9) with the important parts in the middle, so one master serves the desktop hero, the full-window talk and the phone crop.

## Keyboard first, mouse always

| Key | What it does |
| --- | --- |
| Space | Home: start talking · Talk: done with my turn · Day 1: stop recording · Feedback: play my clip |
| Enter | Talk: done with my turn · Setup: next question |
| Esc | Talk: ask to end · Dialogs: close · Feedback: back to Home |
| P / R | Pause, and repeat Bolo's last line |
| ↑ ↓ | Move between feedback cards |
| T | Try the fix on the open card |
| 1–4 | Pick an answer or a word-pair card |
| ? | Show all shortcuts |

Write the keys under the dock buttons (`dk-kbd`). Every key has a visible button too. Hover states, a pointer cursor and the 2px `focus-ring` on everything; tooltips on icon-only buttons; tab order follows visual order; dialogs trap focus and return it.

## Voice in a browser

- **One click before sound.** Browsers block audio until the user clicks, so a first visit opens on "Meet Bolo". After that click, or "Talk", Bolo's voice may play.
- **Microphone permission, explained first.** The consent card says why; then the browser asks. If it is blocked, show where to unblock it beside the address bar.
- **Choose the microphone.** List the devices, show a live level, remember the choice. Suggest headphones once: laptop speakers feed Bolo's voice back into the mic.
- **Capture.** Echo cancellation, noise suppression and auto gain on. Opus (WebM) on Chrome, Edge and Firefox; AAC (MP4) on Safari. Stream short chunks so scoring starts before the talk ends.
- **Tabs and sleep.** A hidden tab pauses the talk and closes the mic; coming back shows "Paused while you were away".

## Browser notifications

Allowed (decided 7 Nov 2026), with these rules:

- **Ask late, ask once.** Never on page load. Ask only from the reminder card after the first talk, where Bolo first shows the time slots and a preview of the exact notification. "Turn on reminders" then opens the browser's own box.
- **One a day, at most.** Only if there was no talk that day, and on the free plan only when free talks are left.
- **Discreet copy.** The title is always "Bolo"; the line is third person ("Bolo wants to know how the demo went."). **Keep it private** turns it into "Time for today's talk." and never mentions what was said.
- **Alternatives stay.** "Add to my calendar instead" (event titled only "Talk with Bolo") and "Remind me on my phone". On a computer, notifications only arrive while the browser runs.
- **If blocked:** no nagging. One line in Me → Reminders explains how to unblock.
- **Mobile browser:** Android Chrome supports web notifications directly; on iPhone they need Bolo added to the Home Screen (iOS 16.4+), so offer that step there.

## Discretion at work

- The tab title is always **Bolo**; the tab icon is the faceless sun. Never "English practice" or the learner's goal.
- No sound until a button is pressed; ambient sound is off on desktop until turned on.
- Make Bolo an installable web app (its own window, out of a shared browser history).

## Payment on a computer

Plans compare side by side with the price as the biggest number. bKash shows a QR code to scan with the bKash app; card is the other choice. Cancelling takes two clicks, as on the phone.

## Mobile browser (next)

Use the phone layout and the phone screens as they are. Differences to design for: no status bar of our own, the browser's address bar takes height (use `100dvh`), the "Meet Bolo" click rule applies here too, and the share sheet is the Web Share API.
