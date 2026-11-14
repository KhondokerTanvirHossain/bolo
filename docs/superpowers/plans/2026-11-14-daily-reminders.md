# Daily reminders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One kind, personal Web Push reminder a day at the learner's chosen time (PRG-5/6/7), with the iPhone Home Screen path, Me → Daily reminder, and a test button for the founder.

**Architecture:** A new hexagonal `reminder` module in the API owns settings, push subscriptions, the due rules, the
line, the scheduler and a dependency-free Web Push sender (RFC 8291 aes128gcm + RFC 8292 VAPID with JDK crypto). It
reads other modules only through their `application.port.in` use cases (memory, talk, learner) and publishes events
that analytics records. The web gets a service worker, a manifest with icons, a small push client, and the
screens from design system v43.

**Tech Stack:** Spring Boot 4 / Java 21, JdbcClient, Flyway V31, JDK `java.security` (EC P-256, ECDH, AES-GCM,
`SHA256withECDSAinP1363Format`), JDK `HttpClient`; Next.js 15 / React 19, Vitest, Playwright (Firefox).

**Spec:** `docs/superpowers/specs/2026-11-14-daily-reminders-design.md` (incl. "Builder check — settled").
Design: DS v43 — ScreenReminder, ScreenReminderIphone, ScreenReminderTurnOn, ScreenReminderSettings, ScreenMe,
DesktopMe, DesktopReminder, ReminderSlots, NotificationPreview (`design/design-system/components/`).

## Global Constraints

- Never more than 1 reminder per learner per local day, across all devices; never on a day they started a talk.
- Window: local now ≥ chosen time and < chosen time + 2 h; a window crossing midnight belongs to the day it started.
- Back-off: 3 ignored in a row → stopped; 7 days after stopping → one "We're here whenever you're ready."; then nothing until the learner opens Bolo (any `POST /api/reminder/opened`), which clears the count and the stop.
- No talk available that day (free or paid) → no reminder; the day talks return → "Your 2 free talks are back." (free) instead of the normal line.
- Lines ≤ 80 characters, no emoji, never the words English, course, lesson, practise/practice, streak, missed. Keep it private → always "Time for today's talk."
- Plain rotating lines: "Time for today's talk.", "Bolo has a question for you today.", "Bolo is on the hilltop. Got 5 minutes?"
- Slots: Morning commute 08:00, Lunch 13:30, Evening commute 18:30, Night 22:00. Time stored as local `HH:MM`, IANA time zone from the browser.
- Never ask for notification permission on page load; only on a tap.
- iPhone in a browser tab → ScreenReminderIphone; iOS < 16.4 → no push ("This iPhone can't get reminders from a website. Update iOS to get them.").
- VAPID private key lives only in `~/bolo/bolo.env` on the box (generated there, never printed); env names `BOLO_VAPID_PUBLIC_KEY`, `BOLO_VAPID_PRIVATE_KEY`, subject `mailto:k.tanvir.hossain@gmail.com` (approved public contact).
- Hexagonal rules (`LayerRulesTest`): modules meet only via `application.port.in` or `common`. Stage `api/src` only, never `git add api`.
- Copy and screens follow DS v43 exactly; Bangla lines only where the DS already has them.

## Review Focus

1. A learner in a non-Dhaka zone with a 23:30 time: sent once, on the right local day, even when the window crosses midnight (Task 3 test `lateTimeCrossesMidnightOnce`).
2. The job runs late (server down 3 h): nothing sent outside the 2 h window (Task 3 test `missedWindowSendsNothing`).
3. Two devices, one subscription gone (410): the other still gets it, the gone one is deleted, and it counts as one reminder (Task 4 test `goneDeviceIsRemovedOthersStillGetIt`).
4. Remembering switched off or a line removed after the line was written: the old story line is never sent (Task 4 test `staleStoryLineFallsBackToPlain`).
5. Two job passes at once: no double send — the send is claimed with a conditional update on `last_sent_on` (Task 4 test `concurrentPassesSendOnce`).

---

### Task 1: Web Push sender (crypto, VAPID, HTTP)

**Files:**
- Create: `api/src/main/java/app/bolo/reminder/domain/WebPushCrypto.java`, `.../reminder/domain/Vapid.java`
- Create: `.../reminder/application/port/out/PushPort.java`, `.../reminder/adapter/out/push/HttpPushAdapter.java`, `.../reminder/adapter/out/push/FakePushAdapter.java`, `.../reminder/adapter/out/push/PushConfiguration.java`
- Modify: `api/src/main/resources/application.yml` (`bolo.push.vendor: fake`, `bolo.push.vapid.public-key`, `private-key`, `subject`), `deploy/compose.prod.yml` (pass `BOLO_VAPID_*`, `BOLO_PUSH_VENDOR=webpush`), `deploy/bolo.env.example`
- Test: `api/src/test/java/app/bolo/reminder/domain/WebPushCryptoTest.java`, `VapidTest.java`, `.../adapter/out/push/HttpPushAdapterTest.java`

**Interfaces:**
- Produces: `record Subscription(String endpoint, String p256dh, String auth)` (in `PushPort`); `enum Outcome { SENT, GONE, FAILED }`; `PushPort.send(Subscription s, byte[] payload) -> Outcome`; `FakePushAdapter.sent() -> List<Sent(Subscription, String json)>`, `FakePushAdapter.gone(String endpoint)` to make an endpoint answer GONE; `PushPort.publicKey() -> String` (base64url, 65-byte point).
- `WebPushCrypto.encrypt(byte[] plaintext, ECPublicKey uaPublic, byte[] authSecret, KeyPair asKeys, byte[] salt) -> byte[]` (aes128gcm body: header salt‖rs=4096‖idlen=65‖as_public‖ciphertext, single record, padding delimiter 0x02); a public overload picks a random key pair and 16-byte salt.
- `Vapid.authorization(String endpoint, Instant now) -> String` → `vapid t=<jwt>, k=<publicKey>`; JWT ES256 `{"typ":"JWT","alg":"ES256"}`, claims `aud` = endpoint origin, `exp` = now + 12 h, `sub`.

- [ ] **Step 1: Write the failing tests**
  - `WebPushCryptoTest.rfc8291AppendixA`: with the RFC 8291 Appendix A keys, auth secret and salt, `encrypt("When I grow up, I want to be a watermelon")` equals the RFC's body (base64url `DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN`).
  - `VapidTest.headerVerifies`: the JWT's signature verifies with the public key (`SHA256withECDSAinP1363Format`), `aud` is `https://fcm.googleapis.com` for an FCM endpoint, `exp` is now + 12 h, `k=` equals `publicKey()`.
  - `HttpPushAdapterTest` (JDK `HttpServer` stub): request is POST with `TTL: 86400`, `Content-Encoding: aes128gcm`, `Urgency: normal`, `Authorization: vapid t=…, k=…`; 201 → SENT; 404 and 410 → GONE; 500 and a timeout → FAILED (never throws).
- [ ] **Step 2: Run** `cd api && ./gradlew test --tests 'app.bolo.reminder.*'` — Expected: FAIL (classes missing).
- [ ] **Step 3: Implement** the four classes. Keys: public from base64url uncompressed point, private from base64 PKCS#8. HKDF-SHA256 by hand (two HMACs). Adapter: JDK `HttpClient`, 10 s timeout. `PushConfiguration` picks `HttpPushAdapter` when `bolo.push.vendor=webpush` (fails fast if keys missing), else `FakePushAdapter`.
- [ ] **Step 4: Run** the same command — Expected: PASS.
- [ ] **Step 5: Commit** `feat(api): Web Push sender — RFC 8291 encryption and VAPID with JDK crypto, fake for tests`.

### Task 2: Reminder settings, devices and the Home card (API)

**Files:**
- Create: `api/src/main/resources/db/migration/V31__reminders.sql`
- Create: `.../reminder/application/port/in/ReminderUseCase.java`, `.../application/port/out/ReminderPort.java`, `.../adapter/out/persistence/JdbcReminderAdapter.java`, `.../application/service/ReminderService.java`, `.../adapter/in/web/ReminderController.java`
- Modify: `api/src/main/java/app/bolo/home/domain/HomePlan.java`, `.../home/application/service/HomeService.java`, `.../home/application/port/in/HomeUseCase.java` (card kind `reminder`)
- Modify: `scripts/smoke.sh` (GET `/api/reminder` without a session → 401)
- Test: `api/src/test/java/app/bolo/reminder/ReminderSettingsAcceptanceTest.java`, `api/src/test/java/app/bolo/home/domain/HomePlanTest.java` (add)

**Schema (V31):**
- `reminder_settings(learner_id uuid pk references learner on delete cascade, on boolean not null, local_time text not null check (local_time ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$'), time_zone text not null, keep_private boolean not null default false, asked_at timestamptz, ignored int not null default 0, stopped_at timestamptz, comeback_sent boolean not null default false, last_sent_on date, last_sent_at timestamptz, last_sent_id uuid, tapped boolean not null default false, card_days int not null default 0, card_last_on date, card_dismissed boolean not null default false, line text, line_source text, updated_at timestamptz not null)`
- `push_subscription(id bigserial pk, learner_id uuid not null references learner on delete cascade, endpoint text not null unique, p256dh text not null, auth text not null, user_agent text, created_at timestamptz not null, last_ok_at timestamptz)`

**Interfaces:**
- Produces `ReminderUseCase`:
  - `record ReminderView(boolean asked, boolean on, String time, String timeZone, boolean keepPrivate, String preview, int devices, String publicKey)`
  - `ReminderView view(LearnerId)`; `ReminderView save(LearnerId, Boolean on, String time, String timeZone, Boolean keepPrivate)` (null keeps; first save sets `asked_at`; validation → `RefusedException("reminder")` → 422)
  - `void notNow(LearnerId, String timeZone)` (asked, off); `void dismissCard(LearnerId)`
  - `void subscribe(LearnerId, String endpoint, String p256dh, String auth, String userAgent)` (upsert by endpoint, moves an endpoint to this learner); `void unsubscribe(LearnerId, String endpoint)`
  - `void opened(LearnerId, String timeZone, String reminderId)` (clears `ignored`, `stopped_at`, `comeback_sent`; updates a valid zone; a matching `reminderId` sets `tapped` and publishes `ReminderOpened`)
  - `boolean cardDue(LearnerId, LocalDate today)` (no row or `asked_at` null, not dismissed, `card_days < 3`; counts a new day when it says yes)
- HTTP (all need a session; guests → 403 code `sign-in`): `GET /api/reminder`, `PUT /api/reminder`, `POST /api/reminder/not-now`, `POST /api/reminder/card/dismiss`, `POST /api/reminder/subscriptions` `{endpoint, keys:{p256dh, auth}}` → 204, `DELETE /api/reminder/subscriptions` `{endpoint}` → 204, `POST /api/reminder/opened` `{timeZone, reminder}` → 204.
- `preview` = the line the next reminder would use (Task 4 fills it; until then the first plain line).
- `HomePlan.card(last, introDue, reminderDue)`: order feedback → intro → reminder → practice. Card `Card("reminder", null, null, null, null)`.
- `LearnerMerged`: settings and subscriptions move to the account (keep the account's row if both exist). Sign-out deletes the subscription the browser sends in `DELETE /api/reminder/subscriptions` (web does it before signing out, Task 6).

- [ ] **Step 1: Write the failing tests** — `ReminderSettingsAcceptanceTest`: `noSessionIs401`; `newLearnerIsNotAsked` (`asked=false`, `on=false`, `time="18:30"`, `devices=0`, `publicKey` non-empty); `saveTurnsOnAndAsks`; `badTimeOrZoneIs422` (`"25:00"`, `"Mars/Base"` → 422 `reminder`); `nullKeepsValues`; `notNowIsAskedAndOff`; `subscribeCountsDevicesAndMovesEndpoint` (same endpoint from a second learner moves it); `unsubscribeRemoves`; `openedClearsBackOff` (seed `ignored=3, stopped_at=now`); `openedWithReminderIdMarksTapped`; `guestIs403`; `cardDueAtMostThreeDaysAndNeverAfterAnswer`. `HomePlanTest.reminderCardRanksAfterIntroBeforePractice`.
- [ ] **Step 2: Run** `./gradlew test --tests 'app.bolo.reminder.ReminderSettingsAcceptanceTest' --tests 'app.bolo.home.*'` — Expected: FAIL.
- [ ] **Step 3: Implement** migration, port, adapter, service, controller, Home wiring, smoke line.
- [ ] **Step 4: Run** the same — Expected: PASS; also `LayerRulesTest`.
- [ ] **Step 5: Commit** `feat(api): reminder settings, devices and the Get a daily reminder card (PRG-5)`.

### Task 3: The due rules (pure domain)

**Files:**
- Create: `api/src/main/java/app/bolo/reminder/domain/ReminderRules.java`
- Test: `api/src/test/java/app/bolo/reminder/domain/ReminderRulesTest.java`

**Interfaces:**
- `record State(boolean on, LocalTime time, ZoneId zone, LocalDate lastSentOn, Instant lastSentAt, boolean tapped, int ignored, Instant stoppedAt, boolean comebackSent)`
- `record Facts(boolean talkedToday, boolean talkSinceLastSent, boolean talkAvailable, boolean talksBackToday)` (today = the window's local day)
- `enum Kind { NONE, NORMAL, TALKS_BACK, COMEBACK }`; `record Decision(Kind kind, LocalDate day, int ignored, boolean stop)`
- `static Optional<LocalDate> windowDay(State s, Instant now)` — the local day whose window contains now, if any.
- `static Decision decide(State s, Facts f, Instant now)`: the previous reminder counts as ignored when it was sent, not tapped and no talk since; ignored reaching 3 → stop (no send). Stopped: only COMEBACK, once, on the first window ≥ 7 days after `stoppedAt`. Otherwise: not in window, already sent that day, talked today or no talk available → NONE; talks back today → TALKS_BACK (free plan only; a paid plan's new month is NORMAL); else NORMAL.

- [ ] **Step 1: Write the failing tests** (Asia/Dhaka unless named): `beforeTimeNothing` (18:29), `atTimeNormal` (18:30), `missedWindowSendsNothing` (20:31), `alreadySentToday`, `talkedTodayNothing`, `noTalkAvailableNothing`, `talksBackLine`, `thirdIgnoredStops` (ignored 2 → previous not tapped, no talk → stop), `tappedResetsIgnored`, `talkSinceResetsIgnored`, `stoppedSilentUntilDaySeven` (day 6 NONE, day 7 COMEBACK, after comebackSent NONE), `lateTimeCrossesMidnightOnce` (America/New_York, 23:30, now 00:45 next day → NORMAL with `day` = the earlier date; second call after recording → NONE), `offNothing`.
- [ ] **Step 2: Run** `./gradlew test --tests 'app.bolo.reminder.domain.ReminderRulesTest'` — Expected: FAIL.
- [ ] **Step 3: Implement** `ReminderRules`.
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `feat(api): reminder due rules — window, once a day, back-off and comeback (PRG-5, PRG-6)`.

### Task 4: Lines, the scheduler, sending, events and the admin test

**Files:**
- Create: `.../reminder/domain/ReminderLines.java`, `.../reminder/domain/ReminderPrompt.java`, `.../reminder/application/service/ReminderSendService.java`, `.../reminder/application/service/ReminderLineService.java`, `.../reminder/adapter/in/scheduler/ReminderJob.java`, `.../reminder/application/port/in/ReminderSent.java`, `.../reminder/application/port/in/ReminderOpened.java`
- Modify: `.../memory/application/port/in/MemoryUseCase.java` (`Optional<String> nextFollowUp(LearnerId)`), `.../memory/application/service/MemoryService.java` (implement; publish `MemoryChanged(LearnerId, TalkSessionId)` after a saved update and after remove/off/forget), create `.../memory/application/port/in/MemoryChanged.java`
- Modify: `.../talk/application/port/in/TalkQueryUseCase.java` if needed: `boolean startedTalkSince(LearnerId, Instant)`
- Modify: `.../analytics/domain/EventRules.java` (SERVER += `reminder_sent`, `reminder_opened`), `.../analytics/application/service/AnalyticsService.java` (listen), `.../admin/...` (`POST /api/admin/reminder-test` → `{devices}`; 404 for non-admins)
- Modify: `application.yml` (`bolo.reminder.job-ms: 60000`, `bolo.reminder.model`, `bolo.reminder.max-tokens: 120`)
- Test: `ReminderLinesTest.java`, `ReminderSendAcceptanceTest.java`, `ReminderLineAcceptanceTest.java`, `AdminAcceptanceTest` (add `reminderTest`)

**Interfaces:**
- `ReminderLines.text(Kind kind, boolean keepPrivate, String storyLine, int rotation) -> String`; `ReminderLines.valid(String line) -> boolean` (≤ 80 chars, no emoji, no banned word, non-blank).
- `ReminderPrompt.system()` / `input(String followUp)`: "Write one push notification line from Bolo, a friend the learner talks with, that picks up this thread: … ≤ 80 characters, third person 'Bolo …', no emoji, never mention English, lessons or practice." Reply plain text.
- `ReminderLineService` `@EventListener MemoryChanged` (virtual thread): `nextFollowUp` empty → clear `line`; else LLM (TalkContext of the talk, 20 s) → `valid` → store `line` + `line_source` = follow-up text; invalid/failure → clear.
- `ReminderSendService.sendDue(Instant now) -> int` (count sent): candidates = on with ≥ 1 device; per learner build `State`/`Facts` (talk module + `FreeTalksUseCase`: `allowance` empty = unlimited = available; `left > 0` = available; `resetsOn` = today and before-send left was 0 → talks back), `decide`, claim with `update … set last_sent_on = :day … where learner_id = :l and last_sent_on is distinct from :day` (0 rows → skip), story line only if `line_source` equals the current `nextFollowUp`, payload `{"title":"Bolo","body":…,"url":"/?from=reminder&r=<uuid>","tag":"bolo-daily"}` to every device, GONE → delete, SENT → `last_ok_at`. Publishes `ReminderSent(learner, slot, hookType)` (slot = morning|lunch|evening|night|exact, hookType = story|plain|private|talks_back|comeback). Never throws.
- `ReminderUseCase.view().preview` = `ReminderLines.text(NORMAL, keepPrivate, freshStoryLine, 0)`.
- `ReminderUseCase.sendTest(LearnerId) -> int` devices reached (ignores rules, does not touch `last_sent_*`).

- [ ] **Step 1: Write the failing tests** — `ReminderLinesTest` (private, story, plain rotation, talks back, comeback exact copy; `valid` rejects 81 chars, "🙂", "your English"); `ReminderSendAcceptanceTest` with `FakePushAdapter`, a fixed `BoloClock` and `bolo.reminder.job-ms` huge (call `sendDue` directly): `sendsAtTimeToEveryDevice`, `goneDeviceIsRemovedOthersStillGetIt`, `talkedTodayNothing`, `noFreeTalkLeftNothing`, `concurrentPassesSendOnce` (two threads → 1 send), `staleStoryLineFallsBackToPlain` (line written, then memory off → plain), `privateUsesPrivateLine`, `sentEventRecorded` (`event` row `reminder_sent` with slot/hook_type), `openedEventRecorded`; `ReminderLineAcceptanceTest` (FakeLlm): `memoryChangeWritesLine`, `invalidModelLineIsDropped`, `noFollowUpClearsLine`; `AdminAcceptanceTest.reminderTest` (admin → 200 `{devices:1}`, other → 404).
- [ ] **Step 2: Run** `./gradlew test --tests 'app.bolo.reminder.*' --tests 'app.bolo.admin.*'` — Expected: FAIL.
- [ ] **Step 3: Implement** as above; `ReminderJob` `@Scheduled(fixedDelayString = "${bolo.reminder.job-ms}")`.
- [ ] **Step 4: Run** the same, then the full API suite `./gradlew test` — Expected: PASS.
- [ ] **Step 5: Commit** `feat(api): send daily reminders — story line from Bolo's notes, every device, events, admin test`.

### Task 5: Web push plumbing — service worker, manifest, icons, client

**Files:**
- Create: `web/public/sw.js`, `web/src/app/manifest.ts`, `web/public/icons/icon-192.png`, `icon-512.png`, `apple-touch-icon.png` (180), `badge-96.png` (rendered from `brand/logo/bolo-icon.svg` / `bolo-icon-monochrome.svg` with a one-off Playwright script `scripts/render-icons.mjs`, committed)
- Create: `web/src/reminder/push.ts`, `web/src/reminder/api.ts`, `web/src/reminder/slots.ts`
- Modify: `web/src/app/layout.tsx` (manifest, `appleWebApp: { capable: true, title: "Bolo", statusBarStyle: "black-translucent" }`, apple-touch-icon, theme color)
- Test: `web/src/reminder/push.test.ts`, `web/src/reminder/slots.test.ts`

**Interfaces:**
- `pushSupport(env = {ua, maxTouchPoints, standalone, hasPush}) -> "ok" | "iphone-tab" | "old-iphone" | "unsupported"` (iPadOS desktop UA with touch counts as iPhone; iOS major.minor < 16.4 → old-iphone).
- `enablePush(publicKey: string) -> Promise<"on" | "denied" | "failed">` (register `/sw.js`, `Notification.requestPermission()`, `pushManager.subscribe({userVisibleOnly: true, applicationServerKey})`, POST subscription).
- `disablePushHere() -> Promise<void>` (unsubscribe + DELETE); `pushState() -> Promise<"here" | "not-here" | "blocked">`.
- `slots.ts`: `SLOTS` (4 slots exact names/times), `nearestSlot(now: Date) -> SlotId`, `formatTime("18:30") -> "6:30 pm"`, `icsFor(time: string) -> string` (daily RRULE, summary "Talk with Bolo").
- `api.ts`: `fetchReminder()`, `saveReminder(patch)`, `notNow(tz)`, `dismissCard()`, `opened(tz, reminderId?)`, `sendTestReminder()` (admin), all with the existing `X-Bolo` write header helper.
- `sw.js`: `push` → `showNotification(title, {body, icon: "/icons/icon-192.png", badge: "/icons/badge-96.png", tag, data: {url}})`; `notificationclick` → focus an open Bolo window and navigate, else `clients.openWindow(url)`.

- [ ] **Step 1: Write the failing tests** — `push.test.ts`: iPhone Safari tab → iphone-tab; iPhone standalone 17.4 → ok; iOS 16.3 → old-iphone; iPadOS desktop UA + touch → iphone-tab; Android Chrome → ok; no PushManager → unsupported. `slots.test.ts`: `nearestSlot` 17:10 → evening, 09:00 → morning, 23:50 → night; `formatTime("08:00")` → "8:00 am"; `icsFor("18:30")` contains `RRULE:FREQ=DAILY` and `DTSTART` at 18:30 local.
- [ ] **Step 2: Run** `cd web && npx vitest run src/reminder` — Expected: FAIL.
- [ ] **Step 3: Implement** the files and render the icons.
- [ ] **Step 4: Run** the same and `npx tsc --noEmit` — Expected: PASS.
- [ ] **Step 5: Commit** `feat(web): service worker, manifest, icons and push client for reminders`.

### Task 6: Web screens and the first-loop step

**Files:**
- Create: `web/src/reminder/ReminderStep.tsx` (ScreenReminder; desktop ≥ 900 px = DesktopReminder card with "Add to my calendar instead"), `ReminderIphone.tsx`, `ReminderTurnOn.tsx` (sheet), `ReminderSettings.tsx` (ScreenReminderSettings; desktop dialog from DesktopMe row), `web/src/reminder/useReminderOpen.ts` (calls `opened(tz, r)` once per page load; reads `?from=reminder&r=`, then cleans the URL)
- Modify: `web/src/onboarding/screens/FirstTalkScreen.tsx` (view kinds `reminder`, `reminderIphone`, `reminderSettings`; first loop: after the first talk's feedback/drills, Home is preceded by ReminderStep when `asked=false`; then "See you tomorrow"; denied → note "Come back around 6:30 pm"; TurnOn sheet when standalone, saved time, not subscribed here and not shown before — localStorage `bolo.reminder.turnon.v1`), `web/src/account/loop.ts` (`askReminder` flag), `web/src/home/HomeDashboard.tsx` (card kind `reminder`: "Get a daily reminder" → ReminderStep; dismiss ×), `web/src/me/MeScreen.tsx` + desktop Me (Daily reminder row: time or "Off"), sign-out path (call `disablePushHere()` first), `web/src/admin/AdminApp.tsx` ("Send me a test reminder" → toast "Sent to N devices"), `web/src/styles/bolo.css` (only what v43 needs beyond existing `.bo-slots`, `.bo-notif`, `.dk-notifbox`)
- Test: `web/e2e/reminder.spec.ts`, `web/src/account/loop.test.ts` (add)

**Interfaces:** consumes Task 2/4 HTTP and Task 5 `push.ts`/`slots.ts`/`api.ts`.

- [ ] **Step 1: Write the failing tests** — `loop.test.ts`: the first finished talk sets `askReminder`; answering clears it and sets `seeYou`. `reminder.spec.ts` (Firefox; `page.addInitScript` stubs `Notification.requestPermission` → "granted"/"denied" and `PushManager.prototype.subscribe` → a fake subscription): `firstTalkEndsWithReminderThenSeeYou` (after first feedback → "When should Bolo check in?", pick Evening commute, "Remind me at 6:30 pm" → Home "See you tomorrow", server devices=1); `notNowGoesHomeAndNeverAsksAgain`; `deniedSavesTimeAndShowsComeBack` ("Come back around 6:30 pm"); `iphoneTabShowsHomeScreenSteps` (iPhone UA → "One more step on iPhone", "Your time is saved: 6:30 pm"); `meDailyReminderChangesTimeAndPrivate` (row shows "6:30 pm"; Keep it private → preview "Time for today's talk."; switch off → row "Off"); `existingLearnerSeesCardOnce` ("Get a daily reminder" → step; dismissed → gone); `adminSendsTestReminder`.
- [ ] **Step 2: Run** `npx vitest run src/account && npx playwright test e2e/reminder.spec.ts --project=firefox` — Expected: FAIL.
- [ ] **Step 3: Implement** the screens from DS v43 (copy exactly as the previews and READMEs).
- [ ] **Step 4: Run** the same, then `npx vitest run`, `npx tsc --noEmit`, `npx eslint .` and the whole e2e suite — Expected: PASS.
- [ ] **Step 5: Commit** `feat(web): daily reminder step, iPhone Home Screen steps, Me → Daily reminder (DS v43)`.

### Task 7: Docs, keys on the box, ship

**Files:**
- Modify: `Bolo-PRD-MVP.md` (PRG-5 changed 2026-11-14: iPhone Home Screen step, Keep it private, calendar on desktop, phone code P1; PRG-6: opening Bolo resets; US-6.5: no save step, "talks are back" on the day talks return; US-6.8 drop save step), `docs/RUNBOOK.md` ("Reminders": VAPID keys on the box, how to send a test, `reminder_sent`/`reminder_opened` SQL, how to stop all reminders: `update reminder_settings set on=false`), `scripts/smoke.sh` (already Task 2)
- Box (no repo change): generate the VAPID pair **on the box** with `openssl ecparam -name prime256v1 -genkey`, append `BOLO_VAPID_PUBLIC_KEY` / `BOLO_VAPID_PRIVATE_KEY` / `BOLO_PUSH_VENDOR=webpush` to `~/bolo/bolo.env` without printing the private key; verify with `grep -c`.

- [ ] **Step 1:** Update the docs; run the full API and web suites — Expected: PASS.
- [ ] **Step 2:** Commit `docs: PRD PRG-5/6, US-6.5/6.8 and RUNBOOK Reminders`.
- [ ] **Step 3:** Generate the keys on the box; `grep -c BOLO_VAPID_ ~/bolo/bolo.env` → 2.
- [ ] **Step 4:** Final whole-branch review (fresh reviewer, most capable model), one fix pass, then `scripts/ship.sh` — Expected: CI ok, deploy ok, smoke ok, 4 live probes exit 0; then `POST /api/admin/reminder-test` from the founder's session reaches a device (founder confirms).
