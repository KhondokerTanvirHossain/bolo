import { expect, test, type Page, type WebSocketRoute } from "./fixtures";

/** DBF-8 (design system v40 ScreenRating): "How was today?" after the feedback of some talks. The API is stubbed. */
const TALK = "33333333-3333-3333-3333-333333333333";
const READY = {
  status: "ready",
  strength: { text: "You explained your job for 40 seconds without stopping." },
  fixes: [{ id: "f1", kind: "habit", title: "Filler watch", word: "um", count: 7, tip: "Try a short pause instead of \"um\"." }],
  skills: { pronunciation: "GOOD", fluency: "STRONG", sentenceBuilding: "BUILDING", vocabulary: "GOOD" },
  firstSession: false,
};

test.beforeEach(async ({ page, browserName }) => {
  await page.addInitScript(() => window.localStorage.setItem("bolo.onboarding.v1", JSON.stringify({ step: "talk" })));
  if (browserName !== "firefox") return;
  await page.addInitScript(() => {
    const md = navigator.mediaDevices;
    const original = md.getUserMedia.bind(md);
    md.getUserMedia = () => original({ audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: false } });
  });
});

async function stub(page: Page, ask: boolean) {
  const learner = { learnerId: "1", kind: "guest", consent: { keepRecordings: true, policyVersion: "2026-11-v1" },
    profile: { goal: "job_interviews", fearedMoment: "interviews", talkMinutes: 5 } };
  await page.route("**/api/me", (r) => r.fulfill({ json: learner }));
  await page.route("**/api/talk", (r) => r.fulfill({ status: 201, json: { id: TALK, minutes: 5 } }));
  await page.route("**/api/talk/*/debrief", (r) => r.fulfill({ json: READY }));
  await page.route("**/api/talk/*/debrief/viewed", (r) => r.fulfill({ status: 204 }));
  await page.route("**/api/events", (r) => r.fulfill({ status: 202 }));
  const puts: Record<string, unknown>[] = [];
  let asks = 0;
  await page.route("**/api/talk/*/rating", (r) => {
    if (r.request().method() === "PUT") { puts.push(JSON.parse(r.request().postData() ?? "{}")); return r.fulfill({ status: 204 }); }
    asks++;
    return r.fulfill({ json: { ask } });
  });
  let ws: WebSocketRoute | null = null;
  await page.routeWebSocket("**/ws/talk/**", (w) => { ws = w; });
  return { puts, asks: () => asks, ws: () => ws };
}

async function toLastCard(page: Page, s: Awaited<ReturnType<typeof stub>>) {
  await page.goto("/");
  await page.getByRole("button", { name: "Start talking" }).click();
  await expect.poll(() => s.ws() !== null).toBe(true);
  s.ws()!.send(JSON.stringify({ type: "ended", reason: "learner_ended", turns: 3, p50Ms: 900, p90Ms: 1400 }));
  await expect(page.getByText("You explained your job for 40 seconds without stopping.")).toBeVisible({ timeout: 10_000 });
  await page.keyboard.press("ArrowDown");
  await page.keyboard.press("ArrowDown");
  await expect(page.getByText("Your skills today").first()).toBeVisible();
}

test("asks on the last card; a face saves at once and Send saves the note", async ({ page }) => {
  const s = await stub(page, true);
  await toLastCard(page, s);
  const dialog = page.getByRole("dialog", { name: "How was today?" });
  await expect(dialog).toBeVisible();
  for (const face of ["Bad", "Not great", "Okay", "Good", "Great"]) await expect(dialog.getByRole("radio", { name: face, exact: true })).toBeVisible();
  await dialog.getByRole("radio", { name: "Good" }).click();
  await expect(dialog.getByRole("radio", { name: "Good" })).toHaveAttribute("aria-checked", "true");
  await expect.poll(() => s.puts.length).toBe(1);
  expect(s.puts[0]).toEqual({ score: 4 });
  await dialog.getByLabel("Want to tell us why? (optional)").fill("Bolo asked good questions");
  await dialog.getByRole("button", { name: "Send" }).click();
  await expect(dialog).toBeHidden();
  expect(s.puts[1]).toEqual({ score: 4, note: "Bolo asked good questions" });
});

test("Not now before a face saves a skip", async ({ page }) => {
  const s = await stub(page, true);
  await toLastCard(page, s);
  const dialog = page.getByRole("dialog", { name: "How was today?" });
  await dialog.getByRole("button", { name: "Not now" }).click();
  await expect(dialog).toBeHidden();
  await expect.poll(() => s.puts.length).toBe(1);
  expect(s.puts[0]).toEqual({ score: null });
  await expect(page.getByText("Your skills today").first()).toBeVisible();
});

test("Esc closes it like Not now and keeps the feedback open", async ({ page }) => {
  const s = await stub(page, true);
  await toLastCard(page, s);
  const dialog = page.getByRole("dialog", { name: "How was today?" });
  await expect(dialog).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(dialog).toBeHidden();
  await expect.poll(() => s.puts.length).toBe(1);
  expect(s.puts[0]).toEqual({ score: null });
  await expect(page.getByText("Your skills today").first()).toBeVisible();
});

test("no sheet when the server says not to ask", async ({ page }) => {
  const s = await stub(page, false);
  await toLastCard(page, s);
  await expect.poll(() => s.asks()).toBe(1);
  await expect(page.getByRole("dialog", { name: "How was today?" })).toHaveCount(0);
});

test.describe("phone", () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test("it is a bottom sheet that fits", async ({ page }) => {
    const s = await stub(page, true);
    await toLastCard(page, s);
    const box = await page.getByRole("dialog", { name: "How was today?" }).boundingBox();
    expect(box!.y + box!.height).toBeGreaterThan(830);
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  });
});

test.describe("desktop", () => {
  test.use({ viewport: { width: 1440, height: 900 } });

  test("it is a centred dialog", async ({ page }) => {
    const s = await stub(page, true);
    await toLastCard(page, s);
    const box = await page.getByRole("dialog", { name: "How was today?" }).boundingBox();
    expect(Math.abs(box!.x + box!.width / 2 - 720)).toBeLessThan(40);
    expect(box!.y + box!.height).toBeLessThan(860);
  });
});
