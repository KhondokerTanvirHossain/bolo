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

/** Review #1: End while the mic is still opening releases the mic and opens no talk socket. */
test("End while Bolo is getting ready releases the mic", async ({ page }) => {
  await page.addInitScript(() => {
    const md = navigator.mediaDevices;
    const original = md.getUserMedia.bind(md);
    const w = window as unknown as { __streams: MediaStream[] };
    w.__streams = [];
    md.getUserMedia = (c?: MediaStreamConstraints) => new Promise((resolve, reject) => {
      setTimeout(() => original(c).then((s) => { w.__streams.push(s); resolve(s); }, reject), 1500);
    });
  });
  await stubApi(page);
  const fake = await fakeTalkServer(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Start talking" }).click();
  await expect(page.getByText("Bolo is getting ready")).toBeVisible();
  await page.keyboard.press("Escape");
  await page.getByRole("dialog").getByRole("button", { name: "End", exact: true }).click();
  await expect(page.getByRole("button", { name: "Start talking" })).toBeVisible();
  await page.waitForTimeout(2500);
  const live = await page.evaluate(() =>
    (window as unknown as { __streams: MediaStream[] }).__streams.flatMap((s) => s.getTracks()).filter((t) => t.readyState === "live").length);
  expect(live).toBe(0);
  expect(fake.opened()).toBe(false);
});

/** Review #2: if Bolo never answers End (a dead connection), the screen doesn't freeze. */
test("End with no answer from Bolo returns to the start within a few seconds", async ({ page }) => {
  await stubApi(page);
  const fake = await fakeTalkServer(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Start talking" }).click();
  await expect.poll(fake.opened).toBe(true);
  send(fake.ws(), { type: "state", value: "your_turn" });
  await expect(page.getByRole("button", { name: "Done, end my turn" })).toBeEnabled();
  await page.keyboard.press("Escape");
  await page.getByRole("dialog").getByRole("button", { name: "End", exact: true }).click();
  await expect(page.getByRole("button", { name: "Start talking" })).toBeVisible({ timeout: 6000 });
});

async function liveTalk(page: Page) {
  await stubApi(page);
  const fake = await fakeTalkServer(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Start talking" }).click();
  await expect.poll(fake.opened).toBe(true);
  return fake;
}

test("15 s of silence shows two starters to read aloud", async ({ page }) => {
  const fake = await liveTalk(page);
  send(fake.ws(), { type: "caption", turn: 0, text: "What did you do today?" });
  send(fake.ws(), { type: "state", value: "your_turn" });
  send(fake.ws(), { type: "hint", starters: ["Today I…", "This morning I…"] });
  await expect(page.getByText("Try starting with")).toBeVisible();
  await expect(page.getByText("This morning I…")).toBeVisible();
});

test("P pauses; Continue resumes", async ({ page }) => {
  const fake = await liveTalk(page);
  send(fake.ws(), { type: "state", value: "your_turn" });
  await expect(page.getByRole("button", { name: "Done, end my turn" })).toBeEnabled();
  await page.keyboard.press("p");
  await expect.poll(() => fake.texts.includes('{"type":"pause"}')).toBe(true);
  send(fake.ws(), { type: "paused", reason: "learner" });
  await expect(page.getByText("Paused. Tap to continue.")).toBeVisible();
  await page.getByRole("button", { name: "Continue" }).click();
  await expect.poll(() => fake.texts.includes('{"type":"resume"}')).toBe(true);
});

test("a crisis shows care and local help", async ({ page }) => {
  const fake = await liveTalk(page);
  send(fake.ws(), { type: "crisis", title: "You're not alone",
    lines: ["I'm really glad you told me.", "Please talk to someone who can help right now."],
    help: [{ name: "Kaan Pete Roi (emotional support)", number: "09612-119911", hours: "3 PM – 3 AM, every day" },
      { name: "National emergency", number: "999", hours: "Any time" }] });
  send(fake.ws(), { type: "ended", reason: "crisis", turns: 1, p50Ms: null, p90Ms: null });
  await expect(page.getByRole("heading", { name: "You're not alone" })).toBeVisible();
  await expect(page.getByRole("link", { name: /09612-119911/ })).toHaveAttribute("href", "tel:09612119911");
  await expect(page.getByRole("link", { name: /999/ })).toHaveAttribute("href", "tel:999");
  await expect(page.getByText("Your coach is listening back")).toBeHidden();
});

test("R asks Bolo to repeat; CC hides the caption; a long wait says Bolo is thinking", async ({ page }) => {
  const fake = await liveTalk(page);
  send(fake.ws(), { type: "caption", turn: 0, text: "What did you do today?" });
  send(fake.ws(), { type: "state", value: "your_turn" });
  await expect(page.getByText("What did you do today?")).toBeVisible();
  await page.keyboard.press("r");
  await expect.poll(() => fake.texts.includes('{"type":"repeat"}')).toBe(true);
  await page.getByRole("button", { name: "Captions" }).click();
  await expect(page.getByText("What did you do today?")).toBeHidden();
  await page.getByRole("button", { name: "Captions" }).click();
  send(fake.ws(), { type: "state", value: "thinking" });
  await expect(page.getByText("Bolo is thinking…")).toBeVisible({ timeout: 6000 });
});

/** Review #3: a crisis stops Bolo's normal reply, and the crisis script isn't cut off when the server closes. */
test("a crisis silences the reply and lets the script finish", async ({ page }) => {
  await page.addInitScript(() => {
    const w = window as unknown as { __stops: number; __closes: number };
    w.__stops = 0;
    w.__closes = 0;
    const stop = AudioBufferSourceNode.prototype.stop;
    AudioBufferSourceNode.prototype.stop = function (...a: [number?]) { w.__stops++; return stop.apply(this, a); };
    const close = AudioContext.prototype.close;
    AudioContext.prototype.close = function () { w.__closes++; return close.apply(this); };
  });
  const fake = await liveTalk(page);
  const ws = fake.ws();
  ws.send(pcm24k());
  send(ws, { type: "crisis", title: "You're not alone", lines: ["I'm really glad you told me."],
    help: [{ name: "National emergency", number: "999", hours: "Any time" }] });
  ws.send(pcm24k());
  send(ws, { type: "ended", reason: "crisis", turns: 1, p50Ms: null, p90Ms: null });
  await ws.close();
  await expect(page.getByRole("heading", { name: "You're not alone" })).toBeVisible();
  const counts = await page.evaluate(() => {
    const w = window as unknown as { __stops: number; __closes: number };
    return { stops: w.__stops, closes: w.__closes };
  });
  expect(counts.stops).toBeGreaterThan(0);
  expect(counts.closes).toBe(0);
});

/** Review: browser shortcuts (Cmd/Ctrl+R reload, Ctrl+P print) are not Bolo's. */
test("Ctrl+R is the browser's, not a repeat", async ({ page }) => {
  const fake = await liveTalk(page);
  send(fake.ws(), { type: "caption", turn: 0, text: "What did you do today?" });
  send(fake.ws(), { type: "state", value: "your_turn" });
  await expect(page.getByRole("button", { name: "Done, end my turn" })).toBeEnabled();
  await page.evaluate(() => window.dispatchEvent(new KeyboardEvent("keydown", { key: "r", ctrlKey: true })));
  await page.waitForTimeout(300);
  expect(fake.texts.includes('{"type":"repeat"}')).toBe(false);
});
