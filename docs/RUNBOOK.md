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
  verified 2026-11-08: Kaan Pete Roi 09612-119911 (3 PM–3 AM), national emergency 999. **Founder approval is needed
  before any pilot learner uses Bolo**; re-verify the numbers every quarter.
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
