import { expect, test, type Page, type WebSocketRoute } from "./fixtures";

/** Daily reminders (PRG-5/6/7, design system v43). The API is stubbed; the browser's permission box and push are faked. */
const TALK = "55555555-5555-5555-5555-555555555555";
const FIRST = { status: "ready", strength: { text: "You kept going for a whole minute.", clip: null }, fixes: [],
  skills: { pronunciation: "GOOD", fluency: "GOOD", sentenceBuilding: "GOOD", vocabulary: "GOOD" }, firstSession: true };
const me = { learnerId: "a1", kind: "account", consent: { keepRecordings: true, policyVersion: "2026-11-v1" },
  profile: { goal: "job_interviews", fearedMoment: "interviews", talkMinutes: 5 }, settings: { buddySpeed: "normal", captions: true },
  account: { email: "rahim@example.com", name: "Rahim" } };
const IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1";

const PUBLIC_KEY = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";

type Reminder = { asked: boolean; on: boolean; time: string; timeZone: string; keepPrivate: boolean; preview: string; devices: number; publicKey: string };

/** permission: what the fake browser box answers. */
/** existing: a subscription this browser already has ("same" key or an "other", older key); it also means permission was granted. */
async function stub(page: Page, { permission = "granted", card = null as object | null, asked = false, on = false,
  existing = null as null | "same" | "other" } = {}) {
  await page.addInitScript(() => window.localStorage.setItem("bolo.onboarding.v1", JSON.stringify({ step: "talk" })));
  await page.addInitScript(({ answer, existing, publicKey }) => {
    const w = window as unknown as Record<string, unknown>;
    const order: string[] = [];
    w.__order = order;
    w.__unsubscribed = 0;
    const realFetch = window.fetch.bind(window);
    window.fetch = (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input instanceof Request ? input.url : input);
      if (url.includes("/api/reminder") && init?.method === "PUT") order.push("save");
      return realFetch(input, init);
    };
    const b64 = (k: string) => Uint8Array.from(atob(k.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (k.length % 4)) % 4)), (c) => c.charCodeAt(0));
    // The browser's own permission box and push service, faked: no real notification service in tests.
    class FakeNotification {
      static permission = existing ? "granted" : "default";
      static requestPermission() { order.push("permission"); FakeNotification.permission = answer; return Promise.resolve(answer); }
    }
    w.Notification = FakeNotification;
    const make = (key: Uint8Array) => ({ endpoint: "https://push.example/abc", options: { applicationServerKey: key.buffer },
      toJSON: () => ({ endpoint: "https://push.example/abc", keys: { p256dh: "BKey", auth: "Auth" } }),
      unsubscribe: async () => { (w.__unsubscribed as number)++; current = null; return true; } });
    let current: ReturnType<typeof make> | null = existing ? make(existing === "same" ? b64(publicKey) : new Uint8Array([4, 1, 2, 3])) : null;
    const pushManager = { getSubscription: async () => current,
      subscribe: async (o: { applicationServerKey: Uint8Array }) => { current = make(new Uint8Array(o.applicationServerKey)); return current; } };
    const reg = { pushManager };
    w.PushManager = function PushManager() {};
    Object.defineProperty(navigator, "serviceWorker", { configurable: true, value: {
      register: async () => reg, ready: Promise.resolve(reg), getRegistration: async () => reg } });
  }, { answer: permission, existing, publicKey: PUBLIC_KEY });
  let reminder: Reminder = { asked, on, time: "18:30", timeZone: "Asia/Dhaka", keepPrivate: false,
    preview: "Bolo wants to know how the demo went.", devices: 0, publicKey: PUBLIC_KEY };
  const calls = { puts: [] as Record<string, unknown>[], subs: 0, notNow: 0, opened: [] as Record<string, unknown>[], tests: 0 };
  await page.route("**/api/me", (r) => r.fulfill({ json: me }));
  await page.route("**/api/auth/config", (r) => r.fulfill({ json: { enabled: false } }));
  await page.route("**/api/me/day1", (r) => r.fulfill({ status: 404 }));
  await page.route("**/api/home", (r) => r.fulfill({ json: { primary: "talk", card, freeTalks: null } }));
  await page.route("**/api/events", (r) => r.fulfill({ status: 202 }));
  await page.route(`**/api/talk/${TALK}/debrief/viewed`, (r) => r.fulfill({ status: 204 }));
  await page.route(`**/api/talk/${TALK}/debrief`, (r) => r.fulfill({ json: FIRST }));
  await page.route("**/api/talk", (r) => r.fulfill({ status: 201, json: { id: TALK, minutes: 5 } }));
  await page.route("**/api/reminder", async (r) => {
    if (r.request().method() === "PUT") {
      const body = JSON.parse(r.request().postData() ?? "{}");
      calls.puts.push(body);
      reminder = { ...reminder, ...Object.fromEntries(Object.entries(body).filter(([k, v]) => v !== undefined && k !== "timeZone")), asked: true };
      if (body.keepPrivate !== undefined) reminder.preview = body.keepPrivate ? "Time for today's talk." : "Bolo wants to know how the demo went.";
    }
    return r.fulfill({ json: reminder });
  });
  await page.route("**/api/reminder/not-now", (r) => { calls.notNow++; reminder = { ...reminder, asked: true, on: false }; return r.fulfill({ status: 204 }); });
  await page.route("**/api/reminder/subscriptions", (r) => { calls.subs++; reminder = { ...reminder, devices: 1 }; return r.fulfill({ status: 204 }); });
  await page.route("**/api/reminder/opened", (r) => { calls.opened.push(JSON.parse(r.request().postData() ?? "{}")); return r.fulfill({ status: 204 }); });
  await page.route("**/api/reminder/card/dismiss", (r) => r.fulfill({ status: 204 }));
  await page.route("**/api/admin/me", (r) => r.fulfill({ json: { admin: true } }));
  await page.route("**/api/admin/reminder-test", (r) => { calls.tests++; return r.fulfill({ json: { devices: 2 } }); });
  let ws: WebSocketRoute | null = null;
  await page.routeWebSocket("**/ws/talk/**", (w) => { ws = w; });
  return { calls, ws: () => ws, reminder: () => reminder };
}

async function finishFirstTalk(page: Page, s: Awaited<ReturnType<typeof stub>>) {
  await page.goto("/");
  await page.getByRole("button", { name: "Start talking" }).click();
  await expect.poll(() => s.ws() !== null).toBe(true);
  s.ws()!.send(JSON.stringify({ type: "ended", reason: "learner_ended", turns: 3, p50Ms: 900, p90Ms: 1400 }));
  await expect(page.getByText("You kept going for a whole minute.")).toBeVisible({ timeout: 10_000 });
  await page.keyboard.press("ArrowDown");
  await page.getByRole("button", { name: "Later" }).click();
}

test("the first talk ends with the reminder step, then 'see you tomorrow'", async ({ page }) => {
  const s = await stub(page);
  await finishFirstTalk(page, s);
  await expect(page.getByRole("heading", { name: "When should Bolo check in?" })).toBeVisible();
  await expect(page.getByText("Bolo wants to know how the demo went.")).toBeVisible();
  await page.getByRole("radio", { name: /Evening/ }).click();
  await page.getByRole("button", { name: "Remind me at 6:30 pm" }).click();
  await expect(page.getByText("See you tomorrow. Bolo will be here.")).toBeVisible();
  expect(s.calls.puts[0]).toMatchObject({ on: true, time: "18:30" });
  expect(s.calls.subs).toBe(1);
});

test("Not now goes Home and the step never comes back", async ({ page }) => {
  const s = await stub(page);
  await finishFirstTalk(page, s);
  await page.getByRole("button", { name: "Not now" }).click();
  await expect(page.getByText("See you tomorrow. Bolo will be here.")).toBeVisible();
  expect(s.calls.notNow).toBe(1);
  await page.reload();
  await expect(page.getByRole("button", { name: "Start talking" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "When should Bolo check in?" })).toBeHidden();
});

test("blocked notifications keep the time and Home says when to come back", async ({ page }) => {
  const s = await stub(page, { permission: "denied" });
  await finishFirstTalk(page, s);
  await page.getByRole("radio", { name: /Morning/ }).click();
  await page.getByRole("button", { name: "Remind me at 8:00 am" }).click();
  await expect(page.getByText("Come back around 8:00 am")).toBeVisible();
  expect(s.calls.puts[0]).toMatchObject({ on: true, time: "08:00" });
  expect(s.calls.subs).toBe(0);
});

test.describe("on an iPhone in a browser tab", () => {
  test.use({ userAgent: IPHONE });
  test("the step explains Add to Home Screen and keeps the time", async ({ page }) => {
    const s = await stub(page);
    await finishFirstTalk(page, s);
    await page.getByRole("radio", { name: /Evening/ }).click();
    await page.getByRole("button", { name: "Remind me at 6:30 pm" }).click();
    await expect(page.getByRole("heading", { name: "One more step on iPhone" })).toBeVisible();
    await expect(page.getByText("2. Tap Add to Home Screen")).toBeVisible();
    await expect(page.getByText("Your time is saved: 6:30 pm.")).toBeVisible();
    expect(s.calls.subs).toBe(0);
    await page.getByRole("button", { name: "Got it" }).click();
    await expect(page.getByText("See you tomorrow. Bolo will be here.")).toBeVisible();
  });
});

test("Me → Daily reminder changes the time, Keep it private, and off", async ({ page }) => {
  const s = await stub(page, { asked: true });
  await page.goto("/");
  await page.getByRole("button", { name: "Me" }).click();
  await page.getByRole("button", { name: /Daily reminder/ }).click();
  await expect(page.getByRole("heading", { name: "Daily reminder" })).toBeVisible();
  await page.getByRole("switch", { name: "Remind me" }).click();
  await expect.poll(() => s.calls.subs).toBe(1);
  await page.getByRole("radio", { name: /Night/ }).click();
  await expect.poll(() => s.calls.puts.at(-1)).toMatchObject({ time: "22:00" });
  await page.getByRole("switch", { name: "Keep it private" }).click();
  await expect(page.getByText("Time for today's talk.")).toBeVisible();
  await page.getByRole("switch", { name: "Remind me" }).click();
  await expect.poll(() => s.calls.puts.at(-1)).toMatchObject({ on: false });
  await page.getByRole("button", { name: "Me" }).first().click();
  await expect(page.getByRole("button", { name: /Daily reminder\s*Off/ })).toBeVisible();
});

test("a learner who never answered sees Get a daily reminder on Home", async ({ page }) => {
  const s = await stub(page, { card: { kind: "reminder" } });
  await page.goto("/");
  await page.getByRole("button", { name: /Get a daily reminder/ }).click();
  await expect(page.getByRole("heading", { name: "When should Bolo check in?" })).toBeVisible();
  await page.getByRole("button", { name: "Not now" }).click();
  expect(s.calls.notNow).toBe(1);
  await expect(page.getByRole("button", { name: "Start talking" })).toBeVisible();
});

test("opening Bolo from a reminder tells the server which one", async ({ page }) => {
  const s = await stub(page, { asked: true });
  await page.goto("/?from=reminder&r=11111111-1111-1111-1111-111111111111");
  await expect.poll(() => s.calls.opened.length).toBeGreaterThan(0);
  expect(s.calls.opened[0]).toMatchObject({ reminder: "11111111-1111-1111-1111-111111111111" });
  await expect(page).toHaveURL(/\/$/);
});

/** Review (reminders, I7): reminders are the account's; a new browser's first talk doesn't ask again or change them. */
test("an account that already answered elsewhere isn't asked again after its first talk here", async ({ page }) => {
  const s = await stub(page, { asked: true, on: true });
  await finishFirstTalk(page, s);
  await expect(page.getByText("See you tomorrow. Bolo will be here.")).toBeVisible();
  await expect(page.getByRole("heading", { name: "When should Bolo check in?" })).toBeHidden();
  expect(s.calls.notNow).toBe(0);
  expect(s.calls.puts).toHaveLength(0);
});

/** Review (reminders, I8): Safari only shows its box straight from the tap, so it is asked before anything is saved. */
test("the permission box opens before the time is saved", async ({ page }) => {
  const s = await stub(page);
  await finishFirstTalk(page, s);
  await page.getByRole("button", { name: /Remind me at/ }).click();
  await expect(page.getByText("See you tomorrow. Bolo will be here.")).toBeVisible();
  expect(await page.evaluate(() => (window as unknown as { __order: string[] }).__order)).toEqual(["permission", "save"]);
});

/** Review (reminders, I6, I5): opening Bolo re-sends this browser's subscription, and remakes one made with an old key. */
test("opening Bolo re-sends this browser's subscription", async ({ page }) => {
  const s = await stub(page, { asked: true, on: true, existing: "same" });
  await page.goto("/");
  await expect.poll(() => s.calls.subs).toBe(1);
  expect(await page.evaluate(() => (window as unknown as { __unsubscribed: number }).__unsubscribed)).toBe(0);
});

test("a subscription made with an old key is remade and sent again", async ({ page }) => {
  const s = await stub(page, { asked: true, on: true, existing: "other" });
  await page.goto("/");
  await expect.poll(() => s.calls.subs).toBe(1);
  expect(await page.evaluate(() => (window as unknown as { __unsubscribed: number }).__unsubscribed)).toBe(1);
});

