# Bolo runbook

## Where it runs

| What | Value |
| --- | --- |
| URL | https://bolo.eliooo.org (A record to the box; the first address, bolo.13-205-14-249.sslip.io, redirects here permanently) |
| Box | the shared elioo EC2 `ec2-13-205-14-249.ap-south-1.compute.amazonaws.com`, user `ec2-user` (tenants: medioo, drugdb, n8n, Bolo) |
| SSH key | `~/Documents/000_office/umr/aws/bdemr-text-analysis.pem` (see `scripts/box.sh`) |
| Containers | `bolo-api` (512 MB cap), `bolo-web` (256 MB), `bolo-db` Postgres 17 (256 MB, volume `bolo_pgdata`); compose project `bolo` in `~/bolo/` |
| Config | `~/bolo/bolo.env`, mode 600 (template: `deploy/bolo.env.example`) |
| Routing | Bolo's site block in the shared `~/n8n/Caddyfile` (copy: `deploy/Caddyfile.bolo`); Caddy reaches the containers by name on `n8n_edge` |

## Deploy

```bash
scripts/deploy.sh            # builds amd64 images locally, ships them over SSH, restarts, waits for health, runs smoke
scripts/smoke.sh             # any time: Bolo's checks plus the other tenants' status codes
```

Nothing is built on the box (it has 2 vCPU, no swap and other tenants). The deploy keeps the current and previous
image tags; older `bolo-*` images are removed. It never touches another tenant's images or containers.

## Rollback

```bash
ssh … 'cat ~/bolo/TAG; docker images bolo-api'          # find the previous tag
ssh … 'cd ~/bolo && IMAGE_TAG=<prev> docker-compose --env-file bolo.env -p bolo -f compose.yml up -d'
```

## Editing Bolo's Caddy block

The Caddyfile is shared by four tenants and bind-mounted as a single file. Follow the elioo runbook procedure exactly:
record the status codes of medioo.eliooo.org, drugdb.eliooo.org and n8n.eliooo.org; `cp Caddyfile Caddyfile.bak-<date>`;
write **in place** (`cat new > Caddyfile`, never `sed -i`/`mv`); `docker exec n8n-caddy-1 cat /etc/caddy/Caddyfile | diff - Caddyfile`
prints nothing; `caddy validate`; `caddy reload`; compare the three status codes; restore the backup on any change.

## Talking with Bolo (voice loop)

- Vendors: Azure AI Speech (STT + TTS, free F0 tier, `centralindia`) and Claude (`claude-haiku-5-5`). Keys live only in
  `~/bolo/bolo.env` as `BOLO_AZURE_SPEECH_KEY`, `BOLO_AZURE_SPEECH_REGION` and `BOLO_ANTHROPIC_API_KEY`. Add or rotate
  one without it touching chat, history or the repo:
  `ssh -t … 'read -rsp "key: " k; echo; sed -i "/^BOLO_ANTHROPIC_API_KEY=/d" ~/bolo/bolo.env; printf "BOLO_ANTHROPIC_API_KEY=%s\n" "$k" >> ~/bolo/bolo.env'`,
  then `docker-compose … up -d bolo-api` (or the next deploy) picks it up.
- `compose.prod.yml` pins `BOLO_SPEECH_VENDOR=azure` and `BOLO_LLM_VENDOR=anthropic`; the app's defaults are fakes.
- Live talks at once: `BOLO_TALK_MAX_LIVE` (default 1; production 4 in `compose.prod.yml` since the Speech resource moved to
  S0 on 2026-11-09). Above the cap a learner is told "Bolo is in another talk right now". bolo-api has 512 MB: raise with care.
- `scripts/talk-probe.sh` runs a real turn against the live site (a new guest, the fixture sentence, real Azure and
  Claude) and prints turn end → first reply audio.
- Latency over the last day (NFR-1: median ≤ 1.5 s, p90 ≤ 2.5 s), from the browser's own measure:
  `docker exec bolo-db psql -U bolo -d bolo -c "select count(*), percentile_cont(array[0.5,0.9]) within group (order by heard_ms) from bolo.talk_turn where heard_ms is not null and at > now() - interval '1 day'"`
- Cost per talk: `select session_id, vendor, kind, unit_type, sum(units) from bolo.usage group by 1,2,3,4`.

## The Coach (debrief after a talk)

- When a talk with at least one learner turn ends, the Coach composes its debrief (`debrief` table: scoring → ready |
  too_short | failed). Claude `claude-sonnet-5-5` writes the strength and sentence upgrades; Azure's live pronunciation
  scores (en-US, IPA) find sound patterns; the Coach's voice is `en-US-AndrewNeural`.
- Learner audio lives in the `bolo_media` volume, encrypted with `BOLO_MEDIA_KEY` (generated on the box, in bolo.env;
  losing it makes old clips unreadable, never garbage). Retention: hourly sweep, 24 h after the talk if the learner
  said "No" to keeping recordings, 90 days otherwise.
- `scripts/coach-probe.sh` runs a real ~2-minute talk (4 spoken turns) and prints the debrief and its time to ready.
- Disputed fixes: `select kind, count(*) from bolo.fix_dispute group by 1`.

## Pilot safety (TLK-14) and talk help

- Crisis content is fixed, never generated: `api/src/main/java/app/bolo/talk/domain/CrisisScript.java`. Numbers
  verified 2026-11-08: Kaan Pete Roi 09612-119911 (3 PM–3 AM), national emergency 999. **Founder approved 2026-11-09
  for the pilot.** Before the beta, NFR-21 needs a qualified local mental-health partner to approve the wording and
  numbers (Kaan Pete Roi itself, or a clinical psychologist). Re-verify the numbers every quarter.
- A keyword screen (CrisisScreen) plus a Haiku classifier (SafetyGuard) on every learner turn; a crisis stops Bolo,
  speaks the script, shows the help card and ends the talk (reason `crisis`, no debrief, its audio deleted).
- Live check: `cd api && BOLO_LIVE_URL=https://bolo.eliooo.org ./gradlew liveProbe --tests '*LiveCrisisProbe*'`.
- Silence help (8 s / 15 s / 45 s), short-answer nudges, Bangla/unclear nudges, "Sorry?"/"Slower please", pause and
  resume are all in `TalkConversation`; thresholds in application.yml under `bolo.talk.*`.

## Practice and "Try it" (scripted pronunciation)

- `POST /api/attempts` (multipart WAV 16 kHz mono ≤ 15 s + `reference`, optional `mode=upgrade`) runs Azure's scripted assessment (REST,
  en-US, miscues on) and returns Nailed it / Closer / Let's try once more with the words to work on. The s-cluster
  extra vowel ("ischool") is found from Azure's Insertion miscue. 20 attempts per learner per minute.
- `attempt` table keeps scores only (no audio yet) — the start of the accuracy-gate data.
- Live check: `cd api && BOLO_LIVE_URL=https://bolo.eliooo.org ./gradlew liveProbe --tests '*LivePracticeProbe*'`.

## Logs and health

```bash
ssh … 'docker logs --tail 100 bolo-api'
ssh … 'docker stats --no-stream bolo-api bolo-web bolo-db; free -m'     # "available" should stay ≥ 1 GB
```

## Database

`docker exec -it bolo-db psql -U bolo -d bolo` (schema `bolo`). Backups: not yet automated; add a nightly `pg_dump` to S3
before the closed beta (plan 1c).

## Accuracy harness (Gate 1)

The harness measures whether Bolo's sound flags agree with a pronunciation teacher (PRD NFR-10; Architecture §10,
Phase 0). It uses the same rule as practice and Try it: `SoundFlags` in the `assessment` domain.

- **Run:** `scripts/golden-set.sh golden-sets/<name>`.
  - The script reads the Azure key from the box into this run only. It is never printed or saved.
  - Each recording goes to Azure once. Its result is saved under `cache/azure/`, so reruns cost nothing.
  - `--offline` uses only saved results. `--refresh` scores everything again.
  - Run it while no talk is live: Azure F0 allows one recognition at a time.
- **Without the script:** `cd api && ./gradlew goldenSet -Pset=<abs dir> [-Pmode=offline|refresh]`, with
  `BOLO_AZURE_SPEECH_KEY` set. Gradle turns any non-zero exit into 1, so use the script when the exit code matters.
- **Output:** `report.md` in the set's folder, also printed. Saved results remember the audio and text they were scored on; if either changes, the run stops and asks for `--refresh`.
  - **Exit codes:** 0 means the gate passed or isn't decided yet; 1 means it failed; 2 means a set or vendor error (the message names the recording).
  - **Per sound:**
    - teacher issues;
    - Bolo flags;
    - right, wrong and missed;
    - "flags right" (precision);
    - "issues caught" (recall);
    - false flags on clear words;
    - the verdict.
  - **Also included:** plain agreement (NFR-10), a sweep of the sound threshold (40/50/60/70) and every
    disagreement, for the teacher's review.
- **Bar (founder-approved 2026-11-08):**
  - **Per sound:** flags right ≥ 80% and issues caught ≥ 60%, judged only with ≥ 5 teacher issues.
  - **Gate passes:** at least 4 sounds pass and none fail.
  - **Gate fails:** any judged sound fails.
  - **Otherwise:** NOT DECIDED.
- **Sets:** `golden-sets/` (see its README for the folder layout and the teacher's label format).
  - **Only `synthetic/` is committed:** AI voices plus Bolo's practice clips.
  - **Its baseline (2026-11-09):** 0 wrong flags in 108 chances on clear speech; the extra vowel caught 2 of 2;
    gate NOT DECIDED (no accented recordings yet).
- **Limit:** the harness measures scripted reading. The Coach scores free talk with unscripted recognition and a
  sound threshold of 50, which can behave differently, so that path needs its own check.
- **Next:**
  - GMU Bengali speakers, once permission is settled (convert mp3 to 16 kHz mono WAV);
  - 5–8 local speakers;
  - the teacher's labels;
  - a second pronunciation vendor, as another vendor function in `GoldenSetRunner`.


## Home and settings (Phase 1a)

- **Home** (`GET /api/home`, PRD US-6.9) shows the **Start talking** button and at most one card:
  - "Finish your feedback": the latest talk's debrief is ready, but the learner hasn't opened it;
  - otherwise "Practise the v sound": the first sound fix in that feedback.
- **Marking feedback as seen:** opening a ready debrief calls `POST /api/talk/{id}/debrief/viewed`, recorded in `debrief.viewed_at`.
- **Settings** (gear button on Home; `PUT /api/me/settings`) apply from the **next** talk:
  - Bolo's speed: normal or slower (`learner_settings`);
  - captions: on or off.
- **"Slower please"** during a talk slows Bolo for that talk, and asks "Keep Bolo slower next time?"; **Yes** saves it.

## Funnel and time to first word

- **Events** (`event` table, PRD §10) carry no audio and no text: only numbers, booleans and short keys. They are allow-listed and limited to 60 per learner per minute.
  - **From the browser:** `app_first_open`, `mic_permission_result`, `onboarding_step_completed`, `first_word_spoken`, `debrief_viewed`.
  - **From the server:** `session_started`, `session_ended`.
- **Before consent** there is no learner, so events wait in the browser and are sent right after consent. The funnel can't see people who leave before consent.
- **`first_word_spoken`** fires once per browser: when the Day 1 clip is saved, or at the first talk turn. It carries `seconds_since_first_open`.
- **Read the funnel:** `scripts/metrics.sh [days]` (default 30). It is read-only and prints opens, mic allowed, onboarding finished, first word (median and slowest 1 in 10), talks and feedback viewed. **Target: first word ≤ 60 s median** (PRD R4, Gate 1).

## Stranger test (Phase 1a exit)

The goal: someone who has never seen Bolo gets from opening it to talk to feedback **with no help**.

1. **Set up:**
   - use the person's own laptop, in a **fresh private window**, at https://bolo.eliooo.org;
   - they need sound on and a working mic;
   - no live talk can be running, because Azure F0 allows one at a time.
2. **Say only:** "This is an app to practise speaking English. Try it; I'll just watch." Then don't help.
3. **Note as they go:**
   - every pause longer than about 5 s;
   - every question they ask;
   - anything they click that does nothing;
   - where they almost gave up.
4. **They're done when:** they reach the Coach's feedback, or give up. Ask afterwards: "What was confusing? Would you use it again tomorrow?"
5. **Afterwards:** run `scripts/metrics.sh 1`.
   - Their first word should be within **60 s** of opening, and their first talk turn within **3 min**.
   - Write the notes in `docs/product/feedback-log.md`.

## Accounts (Google sign-in) and the closed beta

- **Sign-in:** Firebase Auth with Google, popup only (desktop beta), project `bolo-d44c2`.
  - The four public values `BOLO_FIREBASE_API_KEY`, `_AUTH_DOMAIN`, `_PROJECT_ID` and `_APP_ID` live in `~/bolo/bolo.env`.
  - The API serves them at `GET /api/auth/config`, and the web reads them at runtime.
  - No service account: the API verifies ID tokens against Google's securetoken keys (iss and aud = the project; email verified; a sign-in within the last 5 minutes).
  - Without the four values sign-in is off: nobody can make an account, and guests still can't talk or practise (only
    unlimited learners can). Guests talk freely only where `bolo.guests.may-talk=true` (tests and local development).
- **Firebase console checklist:**
  - Authentication → Sign-in method → Google is **enabled**.
  - Authentication → Settings → Authorized domains includes **bolo.eliooo.org**. Without it the popup fails with `auth/unauthorized-domain`.
- **What happens on "Continue with Google"** (`POST /api/auth/google`):
  - **A guest with no account yet:** the guest becomes the account, keeping everything.
  - **A guest signing into an existing account** (for example on a new browser): the guest's talks, practice attempts, events and usage move to the account, and the guest is deleted. The account keeps its own consent, profile and settings, and takes the guest's only where it has none.
  - **No guest on this browser** ("I already have an account"): signs straight in.
  - **Every time:** the session cookie is rotated.
  - **Sign-out:** `POST /api/auth/sign-out`. It's in Settings for now; Me comes next.
- **Guests** (P1d, open sign-up): no talk and no practice without Google sign-in (`422 sign-in`); the web shows
  "Sign in to start talking". See "Open sign-up and the free plan" below.
- **Day 1 clip:** uploaded after sign-in only if the learner chose to keep recordings. It's stored encrypted as `learner/<id>/day1.webm` (or `.mp4`), and downloaded on a new browser.
- **Closed beta** (`BOLO_INVITES_REQUIRED=true` in production):
  - A new guest or a new Google account needs an invite link; returning learners never do.
  - **Make one:** `scripts/invite.sh "<who it's for>" <max-uses>` prints `https://bolo.eliooo.org/?invite=CODE`.
  - **See them:** `scripts/box.sh` then `box "docker exec bolo-db psql -U bolo -d bolo -c 'select code,label,max_uses,uses from invite'"`.
  - **The checks' own invite:** the smoke test and live probes use `~/bolo/probe-invite` on the box (`scripts/probe-invite.sh` creates it).

## Me, Privacy and deletion

- **Me** (the **Me** button on Home, inside the app shell's sidebar) shows:
  - the account email, or "Guest" with **Save your progress**;
  - my goals (read-only);
  - Speaking: Bolo's speed and captions, applying from the next talk;
  - Privacy;
  - Sign out.
  Sign-out clears this browser's Bolo state and the Day 1 clip.
- **Me → Privacy:**
  - **Keep my recordings** appends a consent record (`POST /api/consent`) and applies from the next talk.
  - **Delete everything:** typing DELETE enables the button, which calls `POST /api/me/delete {"confirm":"DELETE"}`. It deletes at once (the PRD allows up to 7 days):
    - every audio file of the learner's talks (`talk/<id>/`);
    - the Day 1 clip (`learner/<id>/`);
    - then every database row, by cascade.
    The cookie is cleared, and the browser returns to the welcome screen. There's no confirmation email yet, because Bolo can't send email.

## Progress and History

- **Progress** (sidebar → Progress, `GET /api/progress`), from the learner's debriefs:
  - **effort:** talks and minutes of speaking;
  - **4 skill trends:** the last 30 days, in words, after 3 finished talks;
  - **my patterns:** up to 5, tagged New, Improving or Steady, plus Fixed;
  - **the latest 3 talks**, with **See all**.
  It never shows a score or a percentage.
- **Pattern rules:**
  - **Fixed:** not seen in the last 5 finished talks.
  - **New:** first seen in the last 2.
  - **Improving:** seen in fewer of the last 3 talks than the 3 before, or the count fell.
  - **Steady:** otherwise.
  New debriefs store every pattern found (`patterns`) and the Coach's 2–5 word `topic`. Older debriefs fall back to their fixes and "Free talk".
- **History** (`GET /api/history`): every talk with a debrief, newest first, grouped by week. Opening one shows its feedback with **Practise these (3 min)** and **Delete recording** (`POST /api/talk/{id}/recording/delete`). That deletes the talk's clips and model lines at once; the feedback text stays.

## Open sign-up and the free plan

- **Open to everyone** (`BOLO_INVITES_REQUIRED: "false"` in `deploy/compose.prod.yml`; that file beats `bolo.env`).
  - Anyone can do welcome, consent, setup, the mic check and the Day 1 clip as a guest.
  - **Starting a talk needs Google sign-in** (422 `sign-in`). Guests are only a browser cookie, so free talks belong to a Google account.
  - To close sign-up again, set it to `"true"` in `deploy/compose.prod.yml` and redeploy. Invite links (`scripts/invite.sh`) then work as before.
- **Free plan** (ACC-5, US-8.7; `talk/domain/FreePlan.java`):
  - **7 talks in the first 7 days**, counted from the first talk; then **2 a month**.
  - The month resets on the 1st at 00:00 Asia/Dhaka.
  - Free talks are 5 minutes.
  - A talk counts once its socket opened, unless its debrief was `too_short` or `failed`.
  - The limit is checked only at talk start (422 `free-limit`). A running talk and its feedback always finish.
  - Practice stays free.
  - Home shows "4 of 7 free talks left in your first week" or "1 of 2 free talks left this month", and Me shows the plan.
- **Unlimited learners** (no counter, no limit):
  - the founder and staff: `scripts/unlimited.sh <email>`. Sign in once first; the script prints how many accounts changed.
  - the smoke test and live probes, which use the probes invite (`scripts/probe-invite.sh`, `invite.unlimited = true`).
  - The live-talk cap (`BOLO_TALK_MAX_LIVE`) still applies to everyone.
- **Known gap:** deleting everything and signing in again starts a new free week. Preventing that would mean keeping a one-way code of the Google account after deletion, which is a founder decision.

## Full chat

- **See full chat** (DBF-6, US-4.10) is in the debrief's card list, live and from History. It shows the whole talk as bubbles: Bolo on the left, the learner on the right.
- **`GET /api/talk/{id}/chat`** returns `{topic, turns[{speaker, text}], marks[{turn, start, end, fix, title}]}`.
  - Only the talk's owner gets it, and only once its feedback is ready; otherwise 404.
- **Marks:**
  - Each mark underlines the words a fix is about: a sound fix's word, or an upgrade's phrase.
  - Tapping a mark opens that fix card, where its clip plays.
  - New debriefs store each fix's learner `turn`. Older ones find their spot by searching the learner's turns for the words.
- **No audio in the chat:** the whole talk's audio is deleted once feedback is ready (privacy and disk).
- The text stays after "Delete recording" ("Feedback and words are still here").
