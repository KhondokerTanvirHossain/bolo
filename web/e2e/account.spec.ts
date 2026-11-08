import { expect, test, type Page, type WebSocketRoute } from "@playwright/test";

const TALK = "44444444-4444-4444-4444-444444444444";
const FIRST = { status: "ready", strength: { text: "You kept going for a whole minute.", clip: null }, fixes: [],
  skills: { pronunciation: "GOOD", fluency: "GOOD", sentenceBuilding: "GOOD", vocabulary: "GOOD" }, firstSession: true };
const consent = { keepRecordings: true, policyVersion: "2026-11-v1" };
const profile = { goal: "job_interviews", fearedMoment: "interviews", talkMinutes: 5 };
const guestMe = { learnerId: "g1", kind: "guest", consent, profile, settings: { buddySpeed: "normal", captions: true }, account: null };
const accountMe = { ...guestMe, learnerId: "a1", kind: "account", account: { email: "rahim@example.com", name: "Rahim" } };

async function stub(page: Page, { signIn = true, onboarded = true, talkRefusal = false } = {}) {
  if (onboarded) {
    await page.addInitScript(() => window.localStorage.setItem("bolo.onboarding.v1", JSON.stringify({ step: "talk" })));
  }
  await page.addInitScript(() => { (window as unknown as { __boloFakeIdToken: string }).__boloFakeIdToken = "fake:u1:rahim@example.com"; });
  let me: object | null = onboarded ? guestMe : null;
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
  await page.route("**/api/home", (r) => r.fulfill({ json: { primary: "talk", card: null } }));
  await page.route("**/api/events", (r) => r.fulfill({ status: 202 }));
  await page.route(`**/api/talk/${TALK}/debrief/viewed`, (r) => r.fulfill({ status: 204 }));
  await page.route(`**/api/talk/${TALK}/debrief`, (r) => r.fulfill({ json: FIRST }));
  await page.route("**/api/talk", (r) => (talkRefusal
    ? r.fulfill({ status: 422, json: { code: "sign-in" } }) : r.fulfill({ status: 201, json: { id: TALK, minutes: 5 } })));
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

test("the 4th guest talk asks to sign in, and Not now leaves Home as it was", async ({ page }) => {
  await stub(page, { talkRefusal: true });
  await page.goto("/");
  await page.getByRole("button", { name: "Start talking" }).click();
  await expect(page.getByRole("heading", { name: "Save your progress to keep talking" })).toBeVisible();
  await page.getByRole("button", { name: "Not now" }).click();
  await expect(page.getByRole("heading", { name: "Save your progress to keep talking" })).toHaveCount(0);
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
