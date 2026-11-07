import { expect, test, type Page, type WebSocketRoute } from "@playwright/test";

// Same Firefox fake-stream workaround as onboarding.spec.ts: the test strips processing constraints, the app keeps them.
test.beforeEach(async ({ page, browserName }) => {
  await page.addInitScript(() => {
    window.localStorage.setItem("bolo.onboarding.v1", JSON.stringify({ step: "talk" }));
  });
  if (browserName !== "firefox") return;
  await page.addInitScript(() => {
    const md = navigator.mediaDevices;
    const original = md.getUserMedia.bind(md);
    md.getUserMedia = () => original({ audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: false } });
  });
});

/** A signed-in learner with consent and a profile, and POST /api/talk answering with `talkStatus`. */
async function stubApi(page: Page, talkStatus = 201) {
  const learner = {
    learnerId: "00000000-0000-0000-0000-000000000001", kind: "guest",
    consent: { keepRecordings: true, policyVersion: "2026-11-v1" },
    profile: { goal: "job_interviews", fearedMoment: "interviews", talkMinutes: 5 },
  };
  await page.route("**/api/me", (r) => r.fulfill({ json: learner }));
  await page.route("**/api/talk", (r) => talkStatus === 201
    ? r.fulfill({ status: 201, json: { id: "11111111-1111-1111-1111-111111111111", minutes: 5 } })
    : r.fulfill({ status: talkStatus, json: { code: "busy" } }));
}

type Fake = { ws: () => WebSocketRoute; texts: string[]; binaryBytes: () => number; opened: () => boolean };

/** Plays the server's side of /ws/talk: records what the browser sends. */
async function fakeTalkServer(page: Page): Promise<Fake> {
  const texts: string[] = [];
  let bytes = 0;
  let route: WebSocketRoute | null = null;
  await page.routeWebSocket("**/ws/talk/**", (ws) => {
    route = ws;
    ws.onMessage((m) => {
      if (typeof m === "string") texts.push(m); else bytes += m.length;
    });
  });
  return { ws: () => route!, texts, binaryBytes: () => bytes, opened: () => route !== null };
}

/** 0.3 s of a quiet 24 kHz hum. */
function pcm24k(): Buffer {
  const b = Buffer.alloc(24000 * 0.3 * 2);
  for (let i = 0; i < b.length / 2; i++) b.writeInt16LE(Math.round(3000 * Math.sin(i / 10)), i * 2);
  return b;
}

const send = (ws: WebSocketRoute, m: object) => ws.send(JSON.stringify(m));

/** True when an AudioContext's clock advances here (headless CI runners have no audio output). Bounded by deadlines. */
async function audioRenders(page: Page): Promise<boolean> {
  return page.evaluate(() => {
    const deadline = <T,>(p: Promise<T>, ms: number, fallback: T) =>
      Promise.race([p, new Promise<T>((r) => setTimeout(() => r(fallback), ms))]);
    const ctx = new AudioContext();
    return deadline(ctx.resume().then(() => true, () => true), 1000, false).then(async () => {
      const start = ctx.currentTime;
      await new Promise((r) => setTimeout(r, 600));
      const advanced = ctx.currentTime > start;
      void deadline(ctx.close(), 500, undefined);
      return advanced;
    });
  });
}

test("talk with Bolo: hear the opening, take a turn, end and see how fast Bolo answered", async ({ page }) => {
  await stubApi(page);
  const fake = await fakeTalkServer(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Start talking" }).click();
  await expect.poll(fake.opened).toBe(true);
  const ws = fake.ws();

  send(ws, { type: "state", value: "buddy_speaking" });
  send(ws, { type: "caption", turn: 0, text: "Hi, I'm Bolo. What did you do today?" });
  ws.send(pcm24k());
  send(ws, { type: "buddy_done", turn: 0 });
  await expect(page.getByText("Hi, I'm Bolo. What did you do today?")).toBeVisible();
  await expect(page.getByText("Free talk · 5 min")).toBeVisible();

  // The browser says when Bolo's line has finished playing, and streams the mic as 640-byte frames — where Web Audio
  // renders at all. Without audio output the clock never moves, so record that instead of failing.
  if (await audioRenders(page)) {
    await expect.poll(() => fake.texts.some((t) => t.includes('"played"') && t.includes('"turn":0')), { timeout: 5000 }).toBe(true);
    await expect.poll(() => fake.binaryBytes(), { timeout: 5000 }).toBeGreaterThan(0);
    expect(fake.binaryBytes() % 640).toBe(0);
  } else {
    test.info().annotations.push({ type: "skipped-check", description: "played/frames: Web Audio does not render here" });
  }

  send(ws, { type: "state", value: "your_turn" });
  const done = page.getByRole("button", { name: "Done, end my turn" });
  await expect(done).toBeEnabled();
  await page.keyboard.press("Space");
  await expect.poll(() => fake.texts.includes('{"type":"done"}')).toBe(true);

  send(ws, { type: "ended", reason: "learner_ended", turns: 3, p50Ms: 900, p90Ms: 1400 });
  await expect(page.getByText("Nice talk. You took 3 turns.")).toBeVisible();
  await expect(page.getByText("Bolo answered in about 0.9 s (slowest 1.4 s).")).toBeVisible();
  await expect(page.getByRole("button", { name: "Talk again" })).toBeVisible();
});

test("when Bolo is busy in another talk, say so and open no socket", async ({ page }) => {
  await stubApi(page, 422);
  const fake = await fakeTalkServer(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Start talking" }).click();
  await expect(page.getByText("Bolo is in another talk right now. Try again in a few minutes.")).toBeVisible();
  expect(fake.opened()).toBe(false);
});

test("a dropped socket shows the lost-voice message with Try again", async ({ page }) => {
  await stubApi(page);
  const fake = await fakeTalkServer(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Start talking" }).click();
  await expect.poll(fake.opened).toBe(true);
  send(fake.ws(), { type: "state", value: "buddy_speaking" });
  await fake.ws().close({ code: 1011 });
  await expect(page.getByText("Bolo lost its voice for a moment. Your talk so far is saved.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Try again" })).toBeVisible();
});

test("Esc asks before ending; Esc again keeps talking; End ends", async ({ page }) => {
  await stubApi(page);
  const fake = await fakeTalkServer(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Start talking" }).click();
  await expect.poll(fake.opened).toBe(true);
  send(fake.ws(), { type: "state", value: "your_turn" });
  await expect(page.getByRole("button", { name: "Done, end my turn" })).toBeEnabled();

  await page.keyboard.press("Escape");
  await expect(page.getByRole("heading", { name: "End this talk?" })).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("heading", { name: "End this talk?" })).toBeHidden();

  await page.keyboard.press("Escape");
  await page.getByRole("dialog").getByRole("button", { name: "End", exact: true }).click();
  await expect.poll(() => fake.texts.includes('{"type":"end"}')).toBe(true);
});
