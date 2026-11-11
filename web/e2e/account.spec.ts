import { expect, test, type Page, type WebSocketRoute } from "./fixtures";

const TALK = "44444444-4444-4444-4444-444444444444";
const FIRST = { status: "ready", strength: { text: "You kept going for a whole minute.", clip: null }, fixes: [],
  skills: { pronunciation: "GOOD", fluency: "GOOD", sentenceBuilding: "GOOD", vocabulary: "GOOD" }, firstSession: true };
const consent = { keepRecordings: true, policyVersion: "2026-11-v1" };
const profile = { goal: "job_interviews", fearedMoment: "interviews", talkMinutes: 5 };
const guestMe = { learnerId: "g1", kind: "guest", consent, profile, settings: { buddySpeed: "normal", captions: true }, account: null };
const accountMe = { ...guestMe, learnerId: "a1", kind: "account", account: { email: "rahim@example.com", name: "Rahim" } };

type FreeTalks = { window: "first_week" | "month"; left: number; total: number; resetsOn: string | null };

async function stub(page: Page, { signIn = true, onboarded = true, talkRefusal = "" as string, account = false,
  freeTalks = null as FreeTalks | null } = {}) {
  if (onboarded) {
    await page.addInitScript(() => window.localStorage.setItem("bolo.onboarding.v1", JSON.stringify({ step: "talk" })));
  }
  await page.addInitScript(() => { (window as unknown as { __boloFakeIdToken: string }).__boloFakeIdToken = "fake:u1:rahim@example.com"; });
  let me: object | null = onboarded ? (account ? accountMe : guestMe) : null;
  const posted: { google: string[]; day1Gets: number } = { google: [], day1Gets: 0 };
  await page.route("**/api/me", (r) => (me ? r.fulfill({ json: me }) : r.fulfill({ status: 401, json: { code: "signed-out" } })));
  await page.route("**/api/auth/config", (r) => r.fulfill({ json: signIn
    ? { enabled: true, apiKey: "k", authDomain: "bolo-test.firebaseapp.com", projectId: "bolo-test", appId: "a" } : { enabled: false } }));
  await page.route("**/api/auth/google", async (r) => {
    posted.google.push(r.request().postData() ?? "");
    me = accountMe;
    await r.fulfill({ json: accountMe });
  });
  await page.route("**/api/me/day1", (r) => { posted.day1Gets++; return r.fulfill({ status: 404 }); });
  await page.route("**/api/home", (r) => r.fulfill({ json: { primary: "talk", card: null, freeTalks } }));
  await page.route("**/api/events", (r) => r.fulfill({ status: 202 }));
  await page.route(`**/api/talk/${TALK}/debrief/viewed`, (r) => r.fulfill({ status: 204 }));
  await page.route(`**/api/talk/${TALK}/debrief`, (r) => r.fulfill({ json: FIRST }));
  await page.route("**/api/talk", (r) => (talkRefusal
    ? r.fulfill({ status: 422, json: { code: talkRefusal } }) : r.fulfill({ status: 201, json: { id: TALK, minutes: 5 } })));
  let ws: WebSocketRoute | null = null;
  await page.routeWebSocket("**/ws/talk/**", (w) => { ws = w; });
  return { posted, ws: () => ws };
}

async function finishFirstTalk(page: Page, s: Awaited<ReturnType<typeof stub>>) {
  await page.goto("/");
  await page.getByRole("button", { name: "Start talking" }).click();
  await expect.poll(() => s.ws() !== null).toBe(true);
  s.ws()!.send(JSON.stringify({ type: "ended", reason: "learner_ended", turns: 3, p50Ms: 900, p90Ms: 1400 }));
  await expect(page.getByText("You kept going for a whole minute.")).toBeVisible({ timeout: 10_000 });
  await page.keyboard.press("ArrowDown");
  await expect(page.getByRole("button", { name: "Practise (3 min)" })).toBeVisible();
}

test("the first loop ends with save your progress; staying a guest leads to 'see you tomorrow'", async ({ page }) => {
  const s = await stub(page);
  await finishFirstTalk(page, s);
  await page.getByRole("button", { name: "Later" }).click();
  await expect(page.getByRole("heading", { name: "Save your progress" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Continue with Google" })).toBeVisible();
  await page.getByRole("button", { name: "Not now" }).click();
  await expect(page.getByRole("heading", { name: "Stay a guest for now?" })).toBeVisible();
  await page.getByRole("button", { name: "Stay a guest" }).click();
  await expect(page.getByText("See you tomorrow. Bolo will be here.")).toBeVisible();
});

test("Continue with Google signs in and keeps everything", async ({ page }) => {
  const s = await stub(page);
  await finishFirstTalk(page, s);
  await page.getByRole("button", { name: "Later" }).click();
  await page.getByRole("button", { name: "Continue with Google" }).click();
  await expect.poll(() => s.posted.google.length).toBe(1);
  expect(s.posted.google[0]).toContain("fake:u1:rahim@example.com");
  await expect(page.getByText("See you tomorrow. Bolo will be here.")).toBeVisible();
  await page.getByRole("button", { name: "Me" }).click();
  await expect(page.getByText(/rahim@example.com/)).toBeVisible();
});

test("a guest signs in before the first talk, and Not now leaves Home as it was", async ({ page }) => {
  await stub(page, { talkRefusal: "sign-in" });
  await page.goto("/");
  await page.getByRole("button", { name: "Start talking" }).click();
  await expect(page.getByRole("heading", { name: "Sign in to start talking" })).toBeVisible();
  await expect(page.getByText(/7 talks in your first week, then 2 every month/)).toBeVisible();
  await page.getByRole("button", { name: "Not now" }).click();
  await expect(page.getByRole("heading", { name: "Sign in to start talking" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Start talking" })).toBeVisible();
});

test("'I already have an account' restores and skips onboarding", async ({ page }) => {
  const s = await stub(page, { onboarded: false });
  await page.goto("/");
  await page.getByRole("button", { name: "I already have an account" }).click();
  await expect(page.getByRole("button", { name: "Start talking" })).toBeVisible();
  await expect.poll(() => s.posted.day1Gets).toBeGreaterThanOrEqual(1);
});

test("with sign-in off there is no Google button and no save step", async ({ page }) => {
  const s = await stub(page, { signIn: false });
  await finishFirstTalk(page, s);
  await page.getByRole("button", { name: "Later" }).click();
  await expect(page.getByRole("button", { name: "Start talking" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Save your progress" })).toHaveCount(0);
});

test("on a phone the save card fits", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const s = await stub(page);
  await finishFirstTalk(page, s);
  await page.getByRole("button", { name: "Later" }).click();
  const card = (await page.getByRole("dialog").boundingBox())!;
  expect(card.x).toBeGreaterThanOrEqual(15);
  expect(card.x + card.width).toBeLessThanOrEqual(375);
});

/** Review I2: a returning account holder who lands on the closed-beta card can still sign in. */
test("the closed-beta card offers 'I already have an account'", async ({ page }) => {
  await stub(page, { onboarded: false });
  await page.route("**/api/session", (r) => r.fulfill({ status: 422, json: { code: "invite" } }));
  await page.goto("/");
  await page.getByRole("button", { name: "Meet Bolo" }).click();
  await page.getByRole("button", { name: "Skip intro" }).click();
  await page.getByText("I'm 13 or older").click();
  await page.getByRole("button", { name: "Yes, keep them" }).click();
  await expect(page.getByRole("heading", { name: "Bolo is in a closed beta" })).toBeVisible();
  await page.getByRole("button", { name: "I already have an account" }).click();
  await expect(page.getByRole("button", { name: "Start talking" })).toBeVisible();
});

test("Home counts the free talks left", async ({ page }) => {
  await stub(page, { account: true, freeTalks: { window: "first_week", left: 4, total: 7, resetsOn: "2026-11-16" } });
  await page.goto("/");
  await expect(page.getByRole("complementary").getByText("4 of 7 free talks left in your first week")).toBeVisible();
});

test("out of free talks: the sheet says when they come back, and Wait closes it", async ({ page }) => {
  await stub(page, { account: true, talkRefusal: "free-limit", freeTalks: { window: "month", left: 0, total: 2, resetsOn: "2026-12-01" } });
  await page.addInitScript(() => window.localStorage.setItem("bolo.freeWeekOverSeen", "1"));
  await page.goto("/");
  await expect(page.getByRole("complementary").getByText("Free talks come back on 1 December")).toBeVisible();
  await page.getByRole("button", { name: "Start talking" }).click();
  await expect(page.getByRole("heading", { name: "You've had this month's 2 free talks" })).toBeVisible();
  await expect(page.getByText("They come back on 1 December.")).toBeVisible();
  await page.getByRole("button", { name: "Wait for 1 December" }).click();
  await expect(page.getByRole("heading", { name: "You've had this month's 2 free talks" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Start talking" })).toBeVisible();
});

test("Me shows the free plan and no closed-beta copy", async ({ page }) => {
  await stub(page, { account: true, freeTalks: { window: "month", left: 1, total: 2, resetsOn: "2026-12-01" } });
  await page.goto("/");
  await page.getByRole("button", { name: "Me" }).click();
  await expect(page.getByText("rahim@example.com · Free plan")).toBeVisible();
  await expect(page.locator("main").getByText("1 of 2 free talks left this month")).toBeVisible();
  await expect(page.getByText(/closed beta/i)).toHaveCount(0);
});

test("the day the free week ends, Home says so once", async ({ page }) => {
  await stub(page, { account: true, freeTalks: { window: "month", left: 2, total: 2, resetsOn: "2026-12-01" } });
  await page.goto("/");
  await expect(page.getByRole("complementary").getByText("Your free week is over. From now on you get 2 free talks a month.")).toBeVisible();
  await page.reload();
  await expect(page.getByRole("complementary").getByText("2 of 2 free talks left this month")).toBeVisible();
});

test("an unlimited account's Me says nothing about a free plan", async ({ page }) => {
  await stub(page, { account: true, freeTalks: null });
  await page.goto("/");
  await page.getByRole("button", { name: "Me" }).click();
  await expect(page.getByText(/rahim@example.com/)).toBeVisible();
  await expect(page.getByText(/Free plan/)).toHaveCount(0);
});

test("on a phone, Home shows the free-talk line under Talk, inside the screen", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await stub(page, { account: true, freeTalks: { window: "first_week", left: 4, total: 7, resetsOn: "2026-11-16" } });
  await page.goto("/");
  const line = page.locator("[data-free-talks]");
  await expect(line).toHaveText("4 of 7 free talks left in your first week");
  const b = (await line.boundingBox())!;
  expect(b.x).toBeGreaterThanOrEqual(0);
  expect(b.x + b.width).toBeLessThanOrEqual(390);
  await expect(page.getByRole("button", { name: "Start talking" })).toBeInViewport();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
});


test("on a 360 px phone, Bolo's line never sits under the Talk disc", async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 780 });
  await stub(page, { account: true, freeTalks: { window: "month", left: 2, total: 2, resetsOn: "2026-12-01" } });
  await page.route("**/api/home", (r) => r.fulfill({ json: { primary: "talk", card: null, lastTopic: "Planning my sister's wedding reception",
    freeTalks: { window: "month", left: 2, total: 2, resetsOn: "2026-12-01" }, week: null } }));
  // A Day 1 clip saved on this device (IndexedDB "bolo"/"clips"/"day1"), as after onboarding.
  await page.addInitScript(() => {
    const req = indexedDB.open("bolo", 1);
    req.onupgradeneeded = () => req.result.createObjectStore("clips");
    req.onsuccess = () => { const tx = req.result.transaction("clips", "readwrite"); tx.objectStore("clips").put(new Blob([new Uint8Array(8)], { type: "audio/webm" }), "day1"); };
  });
  await page.goto("/");
  await expect(page.getByRole("button", { name: "Play my Day 1 clip" })).toBeVisible();
  const line = (await page.locator(".dk-hero .bo-caption").boundingBox())!;
  const disc = (await page.getByRole("button", { name: "Start talking" }).boundingBox())!;
  expect(line.y + line.height).toBeLessThanOrEqual(disc.y);
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(360);
});

test("the free-limit sheet keeps focus, the page behind can't be reached, and Esc closes it", async ({ page }) => {
  await stub(page, { account: true, talkRefusal: "free-limit", freeTalks: { window: "month", left: 0, total: 2, resetsOn: "2026-12-01" } });
  await page.goto("/");
  await page.getByRole("button", { name: "Start talking" }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  for (let i = 0; i < 4; i++) {
    await page.keyboard.press("Tab");
    expect(await dialog.evaluate((d) => d.contains(document.activeElement))).toBe(true);
  }
  await page.keyboard.press("Escape");
  await expect(dialog).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Start talking" })).toBeVisible();
});

test("Esc on the sign-in sheet is Not now", async ({ page }) => {
  await stub(page, { talkRefusal: "sign-in" });
  await page.goto("/");
  await page.getByRole("button", { name: "Start talking" }).click();
  await expect(page.getByRole("heading", { name: "Sign in to start talking" })).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("heading", { name: "Sign in to start talking" })).toHaveCount(0);
});

test("when the daily cap is reached, Bolo is resting", async ({ page }) => {
  await stub(page, { account: true, talkRefusal: "daily-cap", freeTalks: { window: "first_week", left: 5, total: 7, resetsOn: "2026-11-16" } });
  await page.goto("/");
  await page.getByRole("button", { name: "Start talking" }).click();
  await expect(page.getByRole("heading", { name: "Bolo is resting" })).toBeVisible();
  await page.getByRole("button", { name: "OK" }).click();
  await expect(page.getByRole("heading", { name: "Bolo is resting" })).toHaveCount(0);
});

test("a Premium learner's sidebar names the plan", async ({ page }) => {
  await stub(page, { account: true, freeTalks: { window: "month", left: 12, total: 20, resetsOn: "2026-12-01", plan: "premium" } as never });
  await page.goto("/");
  await expect(page.getByRole("complementary").getByText("Premium plan")).toBeVisible();
  await expect(page.getByRole("complementary").getByText("12 of 20 Premium talks left this month")).toBeVisible();
});
