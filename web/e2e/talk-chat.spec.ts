import { expect, test, type Page, type WebSocketRoute } from "./fixtures";

/**
 * P3a (TLK-8, TLK-16 changed 2026-11-12): the talk as a live chat in the sky above the scene, the time left in numbers
 * and a mic level meter. Classic (Settings → Talk view) keeps the earlier screen. The relay is faked; it sends what the
 * Java relay sends.
 */
test.beforeEach(async ({ page, browserName }) => {
  await page.addInitScript(() => window.localStorage.setItem("bolo.onboarding.v1", JSON.stringify({ step: "talk" })));
  if (browserName !== "firefox") return;
  await page.addInitScript(() => {
    const md = navigator.mediaDevices;
    const original = md.getUserMedia.bind(md);
    md.getUserMedia = () => original({ audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: false } });
  });
});

const send = (ws: WebSocketRoute, m: object) => ws.send(JSON.stringify(m));

async function startTalk(page: Page, talkView: "chat" | "classic" = "chat"): Promise<WebSocketRoute> {
  await page.route("**/api/me", (r) => r.fulfill({ json: {
    learnerId: "00000000-0000-0000-0000-000000000001", kind: "guest",
    consent: { keepRecordings: true, policyVersion: "2026-11-v1" },
    profile: { goal: "job_interviews", fearedMoment: "interviews", talkMinutes: 5 },
    settings: { buddySpeed: "normal", captions: true, talkView } } }));
  await page.route("**/api/talk", (r) => r.fulfill({ status: 201, json: { id: "11111111-1111-1111-1111-111111111111", minutes: 5 } }));
  let route: WebSocketRoute | null = null;
  await page.routeWebSocket("**/ws/talk/**", (ws) => { route = ws; ws.onMessage(() => {}); });
  await page.goto("/");
  await page.getByRole("button", { name: "Start talking" }).click();
  await expect.poll(() => route !== null).toBe(true);
  return route!;
}

async function openingThenYourTurn(ws: WebSocketRoute, leftMs = 222000) {
  send(ws, { type: "state", value: "buddy_speaking", leftMs });
  send(ws, { type: "caption", turn: 0, text: "So, what did you work on today?" });
  send(ws, { type: "buddy_done", turn: 0 });
  send(ws, { type: "state", value: "your_turn", leftMs });
}

const log = (page: Page) => page.getByRole("log", { name: "Conversation" });

test("the talk shows as a chat: Bolo's line, my live words, then my settled turn", async ({ page }) => {
  const ws = await startTalk(page);
  await openingThenYourTurn(ws);
  await expect(log(page).getByText("So, what did you work on today?")).toBeVisible();
  await expect(page.getByRole("timer")).toHaveText(/3:4\d left/);
  send(ws, { type: "you", turn: 1, text: "I fixed" });
  send(ws, { type: "you", turn: 1, text: "I fixed a bug" });
  await expect(page.locator(".bo-chat__turn--live")).toHaveText(/I fixed a bug/);
  send(ws, { type: "turn_end", turn: 1, text: "I fixed a bug." });
  await expect(log(page).getByText("I fixed a bug.")).toBeVisible();
  await expect(page.locator(".bo-chat__turn--live")).toHaveCount(0);
});

test("time left reads 1 minute left, then Wrapping up", async ({ page }) => {
  const ws = await startTalk(page);
  await openingThenYourTurn(ws, 30000);
  await expect(page.getByRole("timer")).toHaveText("1 minute left");
  send(ws, { type: "state", value: "buddy_speaking", leftMs: -1000 });
  await expect(page.getByRole("timer")).toHaveText("Wrapping up");
});

test("pause stops the clock", async ({ page }) => {
  const ws = await startTalk(page);
  await openingThenYourTurn(ws);
  send(ws, { type: "paused", reason: "learner", leftMs: 100000 });
  await expect(page.getByRole("timer")).toHaveText("1:40 left");
  await page.waitForTimeout(2200);
  await expect(page.getByRole("timer")).toHaveText("1:40 left");
});

test("a dropped turn disappears", async ({ page }) => {
  const ws = await startTalk(page);
  await openingThenYourTurn(ws);
  send(ws, { type: "you", turn: 1, text: "ami bhalo" });
  await expect(page.locator(".bo-chat__turn--live")).toBeVisible();
  send(ws, { type: "you_drop", turn: 1 });
  await expect(page.locator(".bo-chat__turn--live")).toHaveCount(0);
});

test("repeat replays Bolo's bubble, no duplicate", async ({ page }) => {
  const ws = await startTalk(page);
  await openingThenYourTurn(ws);
  send(ws, { type: "state", value: "buddy_speaking", leftMs: 200000 });
  send(ws, { type: "caption", turn: 1, text: "So, what did you work on today?", repeatOf: 0 });
  await expect(log(page).getByText("So, what did you work on today?")).toHaveCount(1);
});

test("the live bubble is hidden from screen readers", async ({ page }) => {
  const ws = await startTalk(page);
  await openingThenYourTurn(ws);
  send(ws, { type: "you", turn: 1, text: "hello there" });
  await expect(page.locator(".bo-chat__turn--live")).toHaveAttribute("aria-hidden", "true");
  await expect(log(page)).toHaveAttribute("aria-live", "polite");
});

test("a long live turn stays in view", async ({ page }) => {
  const ws = await startTalk(page);
  await openingThenYourTurn(ws);
  let text = "";
  for (let i = 0; i < 40; i++) {
    text += "and then we checked the numbers again ";
    send(ws, { type: "you", turn: 1, text: text.slice(0, 600) });
  }
  await expect(page.locator(".bo-chat__turn--live")).toBeInViewport();
});

test("scrolled up, the chat stops following and Latest brings it back", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const ws = await startTalk(page);
  for (let t = 0; t < 8; t++) {
    if (t % 2 === 0) send(ws, { type: "caption", turn: t, text: `Bolo's question number ${t}, a fairly long one to fill the space?` });
    else send(ws, { type: "turn_end", turn: t, text: `My answer number ${t}, also long enough to wrap onto two lines.` });
  }
  send(ws, { type: "state", value: "your_turn", leftMs: 200000 });
  const scroller = page.locator(".bo-chat");
  await scroller.evaluate((el) => { el.scrollTop = 0; el.dispatchEvent(new Event("scroll")); });
  send(ws, { type: "caption", turn: 8, text: "The newest line from Bolo." });
  await expect(page.getByRole("button", { name: "Latest" })).toBeVisible();
  await page.getByRole("button", { name: "Latest" }).click();
  await expect(log(page).getByText("The newest line from Bolo.")).toBeInViewport();
});

test("the meter moves on my turn and rests while Bolo speaks", async ({ page }) => {
  const ws = await startTalk(page);
  await openingThenYourTurn(ws);
  await expect(page.locator(".bo-level")).not.toHaveClass(/bo-level--rest/);
  send(ws, { type: "state", value: "buddy_speaking", leftMs: 200000 });
  await expect(page.locator(".bo-level")).toHaveClass(/bo-level--rest/);
});

test("Classic view keeps the caption and the arc", async ({ page }) => {
  const ws = await startTalk(page, "classic");
  await openingThenYourTurn(ws);
  await expect(page.getByText("So, what did you work on today?")).toBeVisible();
  await expect(page.getByRole("log")).toHaveCount(0);
  await expect(page.locator(".bo-mic .arc")).toBeVisible();
  await expect(page.getByText("Free talk · 5 min")).toBeVisible();
});

test("Captions off hides the chat", async ({ page }) => {
  const ws = await startTalk(page);
  await openingThenYourTurn(ws);
  await expect(log(page)).toBeVisible();
  await page.getByRole("button", { name: "Captions" }).click();
  await expect(page.getByRole("log")).toHaveCount(0);
});

test("fits at 390 px", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const ws = await startTalk(page);
  await openingThenYourTurn(ws);
  send(ws, { type: "you", turn: 1, text: "I worked on the quarterly report for the whole sales team" });
  await expect(page.locator(".bo-chat__turn--live")).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
});

test("on a phone the keyboard row is hidden (no keys there)", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const ws = await startTalk(page);
  await openingThenYourTurn(ws);
  await expect(page.locator(".dk-keys")).toBeHidden();
});
