# Logo

**The talking sun:** Bolo's setting sun with a speech-bubble tail and a light face, rising over the violet hill under the evening star. The face is two eyes glancing up at the sky (Bolo sits beside you and never stares) and one open smile, mid-sentence. It is the logo's face only: inside the app, Bolo is the companion beside you, and no app screen shows the face.

**Parts**
- **App icon:** indigo sky, a soft sun glow, the evening star, the mustard sun-bubble with its face, and the violet hill. Built as an Android adaptive icon on a 108dp canvas: background layer (sky, glow, star) and foreground layer (sun-bubble, hill), everything that matters inside the 66dp safe circle.
- **Themed icon (Android 13+):** the monochrome layer, faceless: the sun-bubble, the star and the hill with a gap between them, so they never merge into one shape. The system tints it.
- **Notification icon:** the sun-bubble alone, faceless, white, 24dp. Android draws it in one colour, and it keeps the lock screen discreet.
- **Wordmark:** "Bolo" in Anek Bangla 750 drawn as shapes, with the first "o" as the sun-bubble and its face. Always English letters. Day version: ink letters, mustard bubble with a 1.5px `sun-edge` outline. Night version: light letters, mustard bubble.

**Use**
- Clear space around the wordmark: the height of the sun-bubble on every side. Smallest wordmark: 72dp wide; below that, use the icon.
- Keep the face exactly as drawn: no antenna, cheeks, sound waves or other moods, and the eyes always glance up, never at the viewer. Don't recolour the sun, rotate it, or put it on a busy photo.
- **Where it appears:** the launcher, the Play Store, the splash, notifications, Me → About, and the invite link preview. It does not appear inside conversations, feedback or drills, and it is never a loading spinner.

**Splash (Android 12+ SplashScreen)**
- Background `#1D1236` (night canvas), icon = the adaptive foreground. The sun-bubble rises from behind the hill (400 ms, `spring-standard`), the star twinkles once, and the app opens on Home. Total under 1 s; on a warm start there is no splash.
- On Home, the scene's sun sits where the splash sun was: the face and the tail fade, and the sun simply stays. The voice becomes the sun of your hilltop, and no app screen ever shows the face.
- With "Remove animations" on, the splash is a still icon and a 150 ms cross-fade.

**Files:** SVG sources for every part are kept with the project in `brand/logo/`; the Play Store icon is a 512 × 512 PNG rendered from `bolo-icon.svg`.
