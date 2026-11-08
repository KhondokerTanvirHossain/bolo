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
- One live talk at a time (`BOLO_TALK_MAX_LIVE`, default 1) because F0 allows one live recognition. A second learner is
  told "Bolo is in another talk right now". Raise it after moving the Speech resource to S0.
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
