# P0-1 Foundations and First Screens Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A deployed Bolo at `https://bolo.13-205-14-249.sslip.io` where a visitor clicks "Meet Bolo", gives consent (stored on the server as a guest), allows the mic, picks a microphone with a live level meter and passes the 5-second mic check.

**Architecture:** `api/` is a Spring Boot 4 hexagonal app (the `learner` module and `common`); `web/` is Next.js 15. Both run as a compose project on the shared elioo EC2 box behind its existing Caddy, with their own Postgres container. Images are built locally for amd64 and loaded over SSH.

**Tech Stack:** Java 21, Spring Boot 4.1.2 (MVC, virtual threads, JDBC, Flyway), ArchUnit 1.5.1, Testcontainers, Gradle 8.14; Next.js 15, React 19, TS strict, pnpm, Vitest, Playwright; Postgres 17; Docker compose; Caddy (existing).

**Spec:** [Bolo-Architecture-MVP.md](../../../Bolo-Architecture-MVP.md), [Bolo-PRD-MVP.md](../../../Bolo-PRD-MVP.md) (US-1.1, US-1.1a, US-1.2, US-1.3, US-1.5), desktop prototype `design/prototypes/desktop/index.html` (locked v1 copy and layout).

## Global Constraints

- Package root `app.bolo`; layout `domain / application/port/in (*UseCase) / application/port/out (*Port) / application/service (*Service) / adapter/in/web / adapter/out/persistence / config`.
- Domain types are `record`s; manual entity mapping; no MapStruct, no Lombok in domain.
- Logs never contain learner audio or transcript text.
- Copy is taken verbatim from the desktop prototype; no banned words (wrong, bad, error, mistake, fail, incorrect, poor, phoneme, schwa, fricative, labiodental) in learner-facing copy; no red in learner feedback.
- Colours, type and spacing come from `design/design-system/tokens.css`; font Anek Bangla, self-hosted.
- Box limits: `bolo-api` ≤ 512 MB, `bolo-web` ≤ 256 MB, `bolo-db` ≤ 256 MB. Never build images on the box. Never touch another tenant's containers or images.
- Caddyfile edits follow elioo `docs/RUNBOOK.md` exactly: backup, in-place `cat >`, diff inside the container, `caddy validate`, `caddy reload`, and compare the other tenants' status codes before and after.
- Consent row (with `policy_version`) must exist before any audio endpoint accepts a request (NFR-16). Policy version for P0: `2026-11-v1`.

## Review Focus

1. A visitor reloads mid-onboarding → resumes at the same step (US-1.7); the step is kept in `localStorage` and the guest cookie survives.
2. Mic permission blocked in the browser → "Bolo needs your mic to hear you" with browser-specific unblock guidance, then auto-continue when it's granted (US-1.3, `navigator.permissions` change event).
3. No microphone device at all → the mic picker shows one clear next step rather than an empty list.
4. A cross-site POST to `/api/consent` → refused (Origin check and custom header), so a third-party page can't create consent.
5. Consent submitted twice → two append-only rows; the latest one wins; no unique-constraint 500.

---

### Task 1: API skeleton with architecture rules

**Files:**
- Create: `api/settings.gradle`, `api/build.gradle`, `api/gradle/wrapper/*` (copy the Gradle 8.14 wrapper from elioo-health), `api/src/main/java/app/bolo/BoloApplication.java`, `api/src/main/java/app/bolo/common/time/BoloClock.java`, `api/src/main/resources/application.yml`, `api/src/main/resources/db/migration/V1__learner.sql`
- Create: `docker-compose.yml` (local Postgres 17 on 5434)
- Test: `api/src/test/java/app/bolo/architecture/LayerRulesTest.java`, `api/src/test/java/app/bolo/HealthAcceptanceTest.java`

**Interfaces:**
- Produces: `BoloClock` (`Instant now()`, `ZoneId ZONE = Asia/Dhaka`, `LocalDate today()`), a Spring bean wrapping `java.time.Clock`.
- Produces: `spring.threads.virtual.enabled=true`; actuator exposes `health` only.

- [ ] Step 1: ArchUnit tests: the 3 medscribe layer rules plus the cross-module rule from architecture §3.3 (modules may depend on another module only via `..application.port.in..`, and anything may use `app.bolo.common..`).
- [ ] Step 2: Acceptance test with Testcontainers `postgres:17-alpine`: `GET /actuator/health` → 200 and `{"status":"UP"}`; Flyway has applied V1.
- [ ] Step 3: Implement the build, the application and V1 (tables `learner(id uuid pk, kind text check in ('guest','account'), created_at)`, `consent_record(id bigserial, learner_id fk, keep_recordings boolean not null, over_13 boolean not null check (over_13), policy_version text not null, recorded_at timestamptz)`, `web_session(token_hash bytea pk, learner_id fk, created_at, last_seen_at)`).
- [ ] Step 4: `./gradlew build` → green.
- [ ] Step 5: Commit `feat(api): skeleton, architecture rules, V1 schema`.

### Task 2: Guest session and consent (`learner` module)

**Files:**
- Create under `api/src/main/java/app/bolo/learner/`: `domain/{Learner,LearnerId,Consent}.java`, `application/port/in/{StartGuestUseCase,RecordConsentUseCase,CurrentLearnerUseCase}.java`, `application/port/out/{LearnerPort,ConsentLedgerPort,SessionPort}.java`, `application/service/{GuestService,ConsentService}.java`, `adapter/in/web/{SessionController,ConsentController,SessionCookieFilter}.java`, `adapter/out/persistence/Jdbc*Adapter.java`
- Create: `api/src/main/java/app/bolo/common/web/{WriteGuardFilter,ApiError}.java`
- Test: `learner/application/service/ConsentServiceTest.java` (Mockito), `learner/LearnerAcceptanceTest.java` (Testcontainers + MockMvc)

**Interfaces:**
- `POST /api/session` → creates a guest if no valid cookie, sets `bolo_session` (HttpOnly, Secure, SameSite=Lax, Path=/, 90 days); returns `{learnerId, kind, consent: null | {keepRecordings, policyVersion}}`. Idempotent with a valid cookie.
- `GET /api/me` → the same body; 401 `{"code":"signed-out"}` without a cookie.
- `POST /api/consent` body `{keepRecordings: boolean, over13: boolean, policyVersion: string}` → 204; 422 `{"code":"under-13"}` when `over13` is false; 422 `{"code":"policy-version"}` when the version isn't the current one.
- The session token is 32 random bytes in base64url; the DB stores the SHA-256 only.
- `WriteGuardFilter` for every non-GET under `/api/**`: requires `Content-Type: application/json` (multipart is allowed later), header `X-Bolo: 1` and `Origin` equal to `bolo.public-origin` (config); otherwise 403 `{"code":"forbidden"}`.

- [ ] Step 1: Unit tests: consent with `over13=false` is refused; an unknown policy version is refused; a valid one appends a row stamped with `BoloClock.now()`.
- [ ] Step 2: Acceptance tests: POST `/api/session` twice with the returned cookie → same `learnerId`; consent twice → 2 rows, and `GET /api/me` returns the latest; POST without `X-Bolo` → 403; POST with `Origin: https://evil.example` → 403.
- [ ] Step 3: Implement.
- [ ] Step 4: `./gradlew build` → green, including ArchUnit.
- [ ] Step 5: Commit `feat(learner): guest session and append-only consent`.

### Task 3: Web onboarding, first four screens

**Files:**
- Create: `web/` (Next.js 15 App Router, `output: "standalone"`, strict TS, pnpm), `web/src/styles/tokens.css` (copied from the design system), `web/src/styles/base.css`, `web/public/fonts/AnekBangla-*.woff2`
- Create: `web/src/app/page.tsx` (onboarding host), `web/src/onboarding/{steps.ts,useOnboarding.ts}`, `web/src/onboarding/screens/{MeetBolo,Consent,MicPermission,MicPicker,MicCheck}.tsx` + `.css`
- Create: `web/src/audio/{micDevices.ts,levelMeter.ts,micCheck.ts}`, `web/src/api/browser.ts` (typed results `ok | refused | signed-out | offline`, always sends `X-Bolo: 1`)
- Test: `web/src/audio/micCheck.test.ts`, `web/src/onboarding/useOnboarding.test.ts`, `web/e2e/onboarding.spec.ts` (Playwright with Chrome `--use-fake-ui-for-media-stream --use-fake-device-for-media-stream`)

**Interfaces:**
- `classifyMicCheck(samples: {rmsDb: number}[], speechWindow: [number, number]): "great" | "noisy" | "quiet"`: noise floor = median RMS outside the speech window, voice = 90th percentile inside it; `quiet` if voice < −38 dBFS, `noisy` if voice − floor < 15 dB, else `great`. Thresholds live in one exported constant.
- Onboarding steps, in order: `meet → consent → mic → picker → check`. The step and answers persist in `localStorage` key `bolo.onboarding.v1`, read in try/catch.
- Copy, layout and keyboard keys (Space, Enter, Esc) come from the prototype's `welcome`, `consent`, `mic` and `start` screens.

- [ ] Step 1: Vitest: `classifyMicCheck` gives `great` for (−60 floor, −25 voice), `noisy` for (−35, −25) and `quiet` for (−60, −45); the onboarding reducer resumes at the stored step; the consent buttons stay disabled until 13+ is ticked.
- [ ] Step 2: Playwright: with the API running, click Meet Bolo → tick 13+ → "Yes, keep them" → Allow microphone → the picker lists the fake device and the meter moves → "Check" reaches a result screen; a reload on the picker resumes on the picker.
- [ ] Step 3: Implement: an `AnalyserNode` level meter; device list from `enumerateDevices()` with the choice remembered; the headphones question ("Using headphones?" Yes/No) stored in onboarding answers for TLK-15.
- [ ] Step 4: `pnpm lint && pnpm typecheck && pnpm test && pnpm build && pnpm e2e` → green.
- [ ] Step 5: Commit `feat(web): Meet Bolo, consent, mic permission, picker and check`.

### Task 4: Containers, deploy to the shared box, CI build

**Files:**
- Create: `api/Dockerfile` (multi-stage, `eclipse-temurin:21-jre`, `-XX:MaxRAMPercentage=70`), `web/Dockerfile` (`node:22-alpine`, standalone), `deploy/compose.prod.yml`, `deploy/Caddyfile.bolo`, `deploy/bolo.env.example`, `scripts/deploy.sh`, `scripts/smoke.sh`, `.github/workflows/ci.yml`, `docs/RUNBOOK.md`

**Interfaces:**
- Compose project `bolo` in `/home/ec2-user/bolo/` with services `bolo-api` (8080), `bolo-web` (3000) and `bolo-db` (postgres:17-alpine, volume `bolo_pgdata`); networks `bolo_internal` and the external `n8n_edge` (api and web only); memory caps from Global Constraints; `restart: unless-stopped`.
- `scripts/deploy.sh <sha>`: `docker buildx build --platform linux/amd64` for both images tagged `bolo-api:<sha>` and `bolo-web:<sha>` → `docker save | gzip | ssh … 'gunzip | docker load'` → `IMAGE_TAG=<sha> docker compose up -d` → wait for health → remove only `bolo-*` images older than the previous tag.
- Caddy site block `bolo.13-205-14-249.sslip.io`: `encode zstd gzip`, HSTS, `-Server`; `/api/*` and `/ws/*` → `bolo-api:8080`; everything else → `bolo-web:3000`.
- `scripts/smoke.sh`: `https://…/` → 200, `/api/actuator/health` → 200 UP (actuator is mapped under `/api`), and `medioo.eliooo.org` and `drugdb.eliooo.org` status codes unchanged.
- CI `ci.yml`: on push and PR, run `./gradlew build` (Testcontainers) and web lint, typecheck, test and build on a pinned `ubuntu-24.04` runner. No deploy job yet; deploy stays local until secrets are approved.

- [ ] Step 1: `docker compose -f deploy/compose.prod.yml config` validates; `scripts/smoke.sh` passes against a local stack started with `IMAGE_TAG=dev`.
- [ ] Step 2: First deploy: create `~/bolo/bolo.env` (mode 600) on the box, add the Caddy block following the runbook procedure, run `scripts/deploy.sh $(git rev-parse --short HEAD)`.
- [ ] Step 3: `scripts/smoke.sh` against production → all rows pass; the other tenants' codes are unchanged; `free -m` "available" ≥ 1 GB.
- [ ] Step 4: Commit `chore(deploy): compose on the shared box, Caddy block, smoke, CI`.
