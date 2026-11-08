import { expect, test, type Page, type WebSocketRoute } from "@playwright/test";

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

const TALK = "22222222-2222-2222-2222-222222222222";

const READY = {
  status: "ready",
  strength: { text: "You explained your job for 40 seconds without stopping.", clip: `/api/media/talk/${TALK}/clip-0.wav` },
  fixes: [
    { id: "f1", kind: "sound", title: "The v sound", example: "v as in van", said: "It is very important.", saidHighlight: "very",
      better: "It is very important.", betterHighlight: "very", tip: "Top teeth touch your lower lip for v.", count: 3,
      clip: `/api/media/talk/${TALK}/clip-1.wav`, model: `/api/media/talk/${TALK}/model-1.wav` },
    { id: "f2", kind: "upgrade", title: "Say it better", said: "Yesterday I fix bug", better: "Yesterday I fixed a bug.",
      changed: ["fixed", "a"], clip: `/api/media/talk/${TALK}/clip-2.wav`, model: `/api/media/talk/${TALK}/model-2.wav` },
    { id: "f3", kind: "habit", title: "Filler watch", word: "um", count: 7, tip: "Try a short pause instead of \"um\"." },
  ],
  skills: { pronunciation: "GOOD", fluency: "STRONG", sentenceBuilding: "BUILDING", vocabulary: "GOOD" },
  firstSession: true,
  latency: { p50Ms: 900, p90Ms: 1400 },
};

/** A learner with consent; a talk that the fake server ends after one learner turn; the debrief answers in order. */
async function stub(page: Page, debriefs: object[]) {
  const learner = { learnerId: "1", kind: "guest", consent: { keepRecordings: true, policyVersion: "2026-11-v1" },
    profile: { goal: "job_interviews", fearedMoment: "interviews", talkMinutes: 5 } };
  await page.route("**/api/me", (r) => r.fulfill({ json: learner }));
  await page.route("**/api/talk", (r) => r.fulfill({ status: 201, json: { id: TALK, minutes: 5 } }));
  const queue = [...debriefs];
  await page.route("**/api/talk/*/debrief", (r) => r.fulfill({ json: queue.length > 1 ? queue.shift() : queue[0] }));
  const disputes: string[] = [];
  await page.route("**/api/talk/*/debrief/fixes/*/dispute", (r) => { disputes.push(r.request().url()); return r.fulfill({ status: 204 }); });
  await page.route("**/api/media/**", (r) => r.fulfill({ status: 200, contentType: "audio/wav", body: wav() }));
  let ws: WebSocketRoute | null = null;
  await page.routeWebSocket("**/ws/talk/**", (w) => { ws = w; });
  return { ws: () => ws, disputes };
}

function wav(): Buffer {
  const pcm = Buffer.alloc(16000);
  const h = Buffer.alloc(44);
  h.write("RIFF", 0); h.writeUInt32LE(36 + pcm.length, 4); h.write("WAVE", 8); h.write("fmt ", 12);
  h.writeUInt32LE(16, 16); h.writeUInt16LE(1, 20); h.writeUInt16LE(1, 22); h.writeUInt32LE(16000, 24);
  h.writeUInt32LE(32000, 28); h.writeUInt16LE(2, 32); h.writeUInt16LE(16, 34); h.write("data", 36); h.writeUInt32LE(pcm.length, 40);
  return Buffer.concat([h, pcm]);
}

async function talkAndEnd(page: Page, s: Awaited<ReturnType<typeof stub>>) {
  await page.goto("/");
  await page.getByRole("button", { name: "Start talking" }).click();
  await expect.poll(() => s.ws() !== null).toBe(true);
  s.ws()!.send(JSON.stringify({ type: "ended", reason: "learner_ended", turns: 3, p50Ms: 900, p90Ms: 1400 }));
}

test("after a talk the Coach listens back, then shows the strength, the fixes and the skills", async ({ page }) => {
  const s = await stub(page, [{ status: "scoring" }, { status: "scoring" }, READY]);
  await talkAndEnd(page, s);
  await expect(page.getByText("Your coach is listening back")).toBeVisible();
  await expect(page.getByText("You explained your job for 40 seconds without stopping.")).toBeVisible({ timeout: 10_000 });

  await page.keyboard.press("ArrowDown");
  await expect(page.getByRole("heading", { name: /The v sound/ })).toBeVisible();
  await expect(page.getByText("Top teeth touch your lower lip for v.")).toBeVisible();
  await expect(page.getByText("Sound fix · 1 of 3")).toBeVisible();

  await page.getByRole("button", { name: "That's not what I said" }).click();
  await expect(page.getByText("Thanks, we've removed it")).toBeVisible();
  expect(s.disputes[0]).toContain("/debrief/fixes/f1/dispute");
  await expect(page.getByText("Say it better · 1 of 2")).toBeVisible();

  await page.keyboard.press("ArrowDown");
  await expect(page.locator(".bo-big-count")).toHaveText('"um" × 7');
  await page.keyboard.press("ArrowDown");
  await expect(page.getByText("This is your starting point")).toBeVisible();
  await expect(page.getByText("Sentence building")).toBeVisible();
  await expect(page.getByText("Bolo answered in about 0.9 s (slowest 1.4 s).")).toBeVisible();
});

test("too little speech says so kindly", async ({ page }) => {
  const s = await stub(page, [{ status: "too_short" }]);
  await talkAndEnd(page, s);
  await expect(page.getByText("Not enough talking to give you useful feedback yet")).toBeVisible();
  await expect(page.getByRole("button", { name: "Talk a bit more" })).toBeVisible();
  // Founder test 2026-11-08: cards that overrode the centring slid off to the bottom right.
  const box = (await page.locator("[data-screen=scoring] .dk-card").boundingBox())!;
  const vp = page.viewportSize()!;
  expect(box.x).toBeGreaterThanOrEqual(0);
  expect(box.y).toBeGreaterThanOrEqual(0);
  expect(box.x + box.width).toBeLessThanOrEqual(vp.width);
  expect(box.y + box.height).toBeLessThanOrEqual(vp.height);
  expect(Math.abs(box.x + box.width / 2 - vp.width / 2)).toBeLessThan(4);
});

test("a debrief that couldn't be prepared says the talk is saved", async ({ page }) => {
  const s = await stub(page, [{ status: "failed" }]);
  await talkAndEnd(page, s);
  await expect(page.getByText("We couldn't prepare your feedback. Your talk is saved.")).toBeVisible();
});

const TWO_UPGRADES = { ...READY, fixes: [
  { id: "f1", kind: "upgrade", title: "Say it better", said: "Yesterday I fix bug", better: "Yesterday I fixed a bug.", changed: ["fixed", "a"],
    clip: `/api/media/talk/${TALK}/clip-1.wav`, model: `/api/media/talk/${TALK}/model-1.wav` },
  { id: "f2", kind: "upgrade", title: "Say it better", said: "My mother teach me", better: "My mother taught me.", changed: ["taught"],
    clip: `/api/media/talk/${TALK}/clip-2.wav`, model: `/api/media/talk/${TALK}/model-2.wav` },
] };

/** Review #1: two cards of the same kind — the second card plays its own clips. */
test("each card plays its own clip", async ({ page }) => {
  const s = await stub(page, [TWO_UPGRADES]);
  await talkAndEnd(page, s);
  await expect(page.getByText("You explained your job for 40 seconds without stopping.")).toBeVisible({ timeout: 10_000 });
  await page.keyboard.press("ArrowDown");
  await expect(page.locator("audio").first()).toHaveAttribute("src", /clip-1\.wav$/);
  await page.keyboard.press("ArrowDown");
  await expect(page.getByText("My mother teach me")).toBeVisible();
  await expect(page.locator("audio").first()).toHaveAttribute("src", /clip-2\.wav$/);
});

/** Review #3: a double tap disputes one card, once. */
test("a double tap on 'That's not what I said' removes one card", async ({ page }) => {
  const s = await stub(page, [TWO_UPGRADES]);
  await talkAndEnd(page, s);
  await expect(page.getByText("You explained your job for 40 seconds without stopping.")).toBeVisible({ timeout: 10_000 });
  await page.keyboard.press("ArrowDown");
  await page.getByRole("button", { name: "That's not what I said" }).dblclick();
  await expect(page.getByText("Thanks, we've removed it")).toBeVisible();
  await expect(page.getByText("My mother teach me")).toBeVisible();
  expect(s.disputes).toHaveLength(1);
  expect(s.disputes[0]).toContain("/fixes/f1/dispute");
});

/** US-4.3: Try it on a fix card scores the model sentence; three tries, then "This one takes practice." */
test("Try it on a fix card", async ({ page }) => {
  const s = await stub(page, [READY]);
  const posts: string[] = [];
  await page.route("**/api/attempts", async (r) => {
    posts.push(r.request().postData() ?? "");
    await new Promise((res) => setTimeout(res, 300));
    await r.fulfill({ json: { level: "NAILED", heard: true, focus: [] } });
  });
  await talkAndEnd(page, s);
  await expect(page.getByText("You explained your job for 40 seconds without stopping.")).toBeVisible({ timeout: 10_000 });
  await page.keyboard.press("ArrowDown");
  const tryIt = page.getByRole("button", { name: "Try it" });
  await tryIt.click();
  await page.waitForTimeout(600);
  await page.getByRole("button", { name: "Stop recording" }).dblclick();
  await expect(page.getByText("Nailed it")).toBeVisible();
  expect(posts).toHaveLength(1);
  expect(posts[0]).toContain("It is very important.");
});

/** Pilot learners are mostly on phones: the debrief must be readable and tappable at 390 px, with no keyboard. */
test("on a phone the debrief cards fit and can be tapped through", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const s = await stub(page, [READY]);
  await talkAndEnd(page, s);
  const strength = page.getByText("You explained your job for 40 seconds without stopping.");
  await expect(strength).toBeVisible({ timeout: 10_000 });
  const box = (await strength.boundingBox())!;
  expect(box.x).toBeGreaterThanOrEqual(15);
  expect(box.x + box.width).toBeLessThanOrEqual(375);
  await page.getByRole("button", { name: /The v sound/ }).click();
  await expect(page.getByText("Top teeth touch your lower lip for v.")).toBeVisible();
  for (const label of ["Top teeth touch your lower lip for v.", "Try"]) {
    const b = (await page.getByText(label, { exact: true }).first().boundingBox())!;
    expect(b.x + b.width, label).toBeLessThanOrEqual(375);
  }
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
});
