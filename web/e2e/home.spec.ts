import { expect, test, type Page, type WebSocketRoute } from "./fixtures";

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

const TALK = "33333333-3333-3333-3333-333333333333";
const READY = { status: "ready", strength: { text: "You kept going for a whole minute.", clip: null }, fixes: [],
  skills: { pronunciation: "GOOD", fluency: "GOOD", sentenceBuilding: "GOOD", vocabulary: "GOOD" }, firstSession: false };

const WEEK = { run: 6, restUsed: true, welcomeBack: false, days: [
  { date: "2026-11-07", state: "done" }, { date: "2026-11-08", state: "done" }, { date: "2026-11-09", state: "rest" },
  { date: "2026-11-10", state: "done" }, { date: "2026-11-11", state: "done" }, { date: "2026-11-12", state: "today" },
  { date: "2026-11-13", state: "future" }] };

async function stub(page: Page, card: object | null, extra: object = {}) {
  const settings = { buddySpeed: "normal", captions: true };
  await page.route("**/api/me", (r) => r.fulfill({ json: { learnerId: "1", kind: "guest",
    consent: { keepRecordings: true, policyVersion: "2026-11-v1" },
    profile: { goal: "job_interviews", fearedMoment: "interviews", talkMinutes: 5 }, settings } }));
  await page.route("**/api/home", (r) => r.fulfill({ json: { primary: "talk", card, ...extra } }));
  await page.route("**/api/events", (r) => r.fulfill({ status: 202 }));
  const puts: string[] = [];
  await page.route("**/api/me/settings", (r) => { puts.push(r.request().postData() ?? ""); return r.fulfill({ status: 204 }); });
  const viewed: string[] = [];
  await page.route(`**/api/talk/${TALK}/debrief/viewed`, (r) => { viewed.push(r.request().url()); return r.fulfill({ status: 204 }); });
  await page.route(`**/api/talk/${TALK}/debrief`, (r) => r.fulfill({ json: READY }));
  await page.route("**/api/talk", (r) => r.fulfill({ status: 201, json: { id: TALK, minutes: 5 } }));
  let ws: WebSocketRoute | null = null;
  await page.routeWebSocket("**/ws/talk/**", (w) => { ws = w; });
  return { puts, viewed, ws: () => ws };
}

test("Home offers to finish an unseen feedback, and opening it marks it seen", async ({ page }) => {
  const s = await stub(page, { kind: "feedback", talkId: TALK });
  await page.goto("/");
  await expect(page.getByRole("button", { name: "Start talking" })).toBeVisible();
  await page.getByRole("button", { name: "Finish your feedback" }).click();
  await expect(page.getByText("You kept going for a whole minute.")).toBeVisible();
  await expect.poll(() => s.viewed.length).toBe(1);
});

test("Home offers the last feedback's sound, and opens practice on it", async ({ page }) => {
  await stub(page, { kind: "practice", pattern: "V_BH", title: "The v sound" });
  await page.route("**/api/practice", (r) => r.fulfill({ json: { patterns: [
    { key: "V_BH", title: "The v sound", example: "v as in van", tip: "Top teeth touch your lower lip for v.", sentences: ["Very few vans visit."] }] } }));
  await page.goto("/");
  await page.getByRole("button", { name: "Practise the v sound" }).click();
  await expect(page.getByText("Very few vans visit.")).toBeVisible();
});

test("Me saves a slower Bolo for the next talk", async ({ page }) => {
  const s = await stub(page, null);
  await page.goto("/");
  await page.getByRole("button", { name: "Me" }).click();
  await expect(page.getByRole("heading", { level: 1, name: "Me" })).toBeVisible();
  await page.getByRole("button", { name: "Slower" }).click();
  await expect.poll(() => s.puts.at(-1) ?? "").toContain("\"buddySpeed\":\"slower\"");
  await page.getByRole("button", { name: "Talk" }).click();
  await expect(page.getByRole("button", { name: "Start talking" })).toBeVisible();
});

test("after 'Slower please' Bolo asks to keep it slower, and Yes saves it", async ({ page }) => {
  const s = await stub(page, null);
  await page.goto("/");
  await page.getByRole("button", { name: "Start talking" }).click();
  await expect.poll(() => s.ws() !== null).toBe(true);
  s.ws()!.send(JSON.stringify({ type: "state", value: "your_turn" }));
  s.ws()!.send(JSON.stringify({ type: "slower" }));
  await page.getByRole("button", { name: "Yes, keep it" }).click();
  await expect.poll(() => s.puts.at(-1) ?? "").toContain("\"buddySpeed\":\"slower\"");
  await expect(page.getByText("Keep Bolo slower next time?")).toHaveCount(0);
});

test("on a phone, Home with its card and the Me page fit the screen", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await stub(page, { kind: "feedback", talkId: TALK });
  await page.goto("/");
  await expect(page.locator("[data-home-card]")).toBeVisible();
  const card = (await page.locator("[data-home-card]").boundingBox())!;
  expect(card.x).toBeGreaterThanOrEqual(15);
  expect(card.x + card.width).toBeLessThanOrEqual(375);
  await page.getByRole("button", { name: "Me" }).click();
  await expect(page.getByRole("heading", { level: 1, name: "Me" })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
});

test("the dashboard Home: Bolo's line from the last talk, the Talk disc, and this week's run", async ({ page }) => {
  await stub(page, null, { lastTopic: "The payment bug demo", week: WEEK });
  await page.goto("/");
  await expect(page.getByRole("heading", { level: 1, name: "Ready when you are." })).toBeVisible();
  await expect(page.getByText("Last time: The payment bug demo. Tell me what happened next.")).toBeVisible();
  await expect(page.getByText("6-day run")).toBeVisible();
  await expect(page.getByText("1 rest day used")).toBeVisible();
  await expect(page.getByRole("button", { name: "Talk", exact: true })).toHaveAttribute("aria-current", "page");
  // This week's "Progress" link opens the Progress tab.
  await page.locator(".dk-cols").getByRole("button", { name: "Progress", exact: true }).click();
  await expect(page.getByRole("complementary").getByRole("button", { name: "Progress", exact: true })).toHaveAttribute("aria-current", "page");
});

test("Space on Home starts a talk", async ({ page }) => {
  const s = await stub(page, null);
  await page.goto("/");
  await expect(page.getByRole("heading", { level: 1, name: "Ready when you are." })).toBeVisible();
  await page.locator("h1").click();
  await page.keyboard.press("Space");
  await expect.poll(() => s.ws() !== null).toBe(true);
});

test("Close feedback lands on the dashboard Home", async ({ page }) => {
  await stub(page, { kind: "feedback", talkId: TALK });
  await page.goto("/");
  await page.getByRole("button", { name: "Finish your feedback" }).click();
  await expect(page.getByText("You kept going for a whole minute.")).toBeVisible();
  await page.getByRole("button", { name: "Close feedback" }).click();
  await expect(page.getByRole("heading", { level: 1, name: "Ready when you are." })).toBeVisible();
});
