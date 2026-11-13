import { expect, test, type Page } from "./fixtures";

/** P3b (ACC-9, 2026-11-13): Me → What Bolo remembers. The API is stubbed; it answers what the Java API answers. */
const ACCOUNT = { learnerId: "a1", kind: "account", consent: { keepRecordings: true, policyVersion: "2026-11-v1" },
  profile: { goal: "work_meetings", fearedMoment: "speaking_up", talkMinutes: 5 }, settings: { buddySpeed: "normal", captions: true, talkView: "chat" },
  account: { email: "r@x.com", name: "Rafi" } };

const NOTE = {
  enabled: true,
  followUps: [
    { id: "t1", kind: "plan", text: "Showing the payment bug demo to the client on Thursday", said: "2026-11-11" },
    { id: "t2", kind: "worry", text: "Nervous about speaking first in the Monday meeting", said: "2026-11-10" },
    { id: "t3", kind: "prep", text: "Practising a two-line update for standups", said: "2026-11-12" },
  ],
  about: [{ id: "a1", text: "Works as a software tester at a payments company in Dhaka" }],
  interests: [{ id: "i1", text: "Cricket" }],
};

type Calls = { method: string; url: string; body: string | null }[];

async function stub(page: Page, note: object = NOTE): Promise<Calls> {
  const calls: Calls = [];
  let current: Record<string, unknown> = JSON.parse(JSON.stringify(note));
  await page.addInitScript(() => window.localStorage.setItem("bolo.onboarding.v1", JSON.stringify({ step: "talk" })));
  await page.route("**/api/me", (r) => r.fulfill({ json: ACCOUNT }));
  await page.route("**/api/auth/config", (r) => r.fulfill({ json: { enabled: false } }));
  await page.route("**/api/events", (r) => r.fulfill({ status: 202 }));
  await page.route("**/api/home", (r) => r.fulfill({ json: { primary: "talk", card: null,
    freeTalks: { plan: "free", window: "month", left: 2, total: 2, resetsOn: "2026-12-01" } } }));
  await page.route("**/api/referral", (r) => r.fulfill({ json: { offer: false, code: "ab12cd34", friends: [], counted: 0, rewards: 0, maxRewards: 3, premiumUntil: null, newReward: false } }));
  await page.route("**/api/memory**", (r) => {
    const req = r.request();
    calls.push({ method: req.method(), url: req.url(), body: req.postData() });
    if (req.method() === "GET") return r.fulfill({ json: current });
    if (req.method() === "PUT") {
      const on = JSON.parse(req.postData() ?? "{}").enabled;
      current = on ? { ...current, enabled: true } : { enabled: false, followUps: [], about: [], interests: [] };
    }
    if (req.method() === "POST") current = { ...current, followUps: [], about: [], interests: [] };
    return r.fulfill({ status: 204 });
  });
  return calls;
}

async function openMemory(page: Page) {
  await page.goto("/");
  await page.getByRole("button", { name: "Me", exact: true }).click();
  await page.getByRole("button", { name: /What Bolo remembers/ }).click();
  await expect(page.getByRole("heading", { name: "What Bolo remembers" })).toBeVisible();
}

test("Me shows the row with its count, and the screen lists the three groups", async ({ page }) => {
  await stub(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Me", exact: true }).click();
  await expect(page.getByRole("button", { name: /What Bolo remembers.*3 things to follow up/ })).toBeVisible();
  await page.getByRole("button", { name: /What Bolo remembers/ }).click();
  await expect(page.getByText("Bolo keeps short notes from your talks so it can follow up, like a friend would.", { exact: false })).toBeVisible();
  await expect(page.getByText("To follow up")).toBeVisible();
  await expect(page.getByText("On your mind")).toBeVisible();
  await expect(page.getByText("You said this on 11 Nov")).toBeVisible();
  await expect(page.getByText("About you")).toBeVisible();
  await expect(page.getByText("Your interests")).toBeVisible();
  await expect(page.getByText("Cricket")).toBeVisible();
  await expect(page.getByRole("switch", { name: "Let Bolo remember our talks" })).toHaveAttribute("aria-checked", "true");
});

test("removing a line hides it and calls DELETE /api/memory/lines/:id", async ({ page }) => {
  const calls = await stub(page);
  await openMemory(page);
  await page.getByRole("button", { name: "Remove: Cricket" }).click();
  await expect(page.getByText("Cricket")).toHaveCount(0);
  await expect(page.getByText("Your interests")).toHaveCount(0);
  expect(calls.some((c) => c.method === "DELETE" && c.url.endsWith("/api/memory/lines/i1"))).toBe(true);
});

test("turning remembering off shows the off copy and calls PUT with enabled:false", async ({ page }) => {
  const calls = await stub(page);
  await openMemory(page);
  await page.getByRole("switch", { name: "Let Bolo remember our talks" }).click();
  await expect(page.getByText("Bolo isn't keeping notes. Each talk starts fresh.")).toBeVisible();
  await expect(page.getByText("To follow up")).toHaveCount(0);
  expect(calls.some((c) => c.method === "PUT" && c.body === JSON.stringify({ enabled: false }))).toBe(true);
});

test("forget everything asks first, then empties", async ({ page }) => {
  const calls = await stub(page);
  await openMemory(page);
  await page.getByRole("button", { name: "Forget everything Bolo knows" }).click();
  await expect(page.getByText("Bolo will forget everything it knows about you. Talks still work as before.")).toBeVisible();
  await page.getByRole("button", { name: "Cancel" }).click();
  expect(calls.some((c) => c.method === "POST")).toBe(false);
  await page.getByRole("button", { name: "Forget everything Bolo knows" }).click();
  await page.getByRole("button", { name: "Forget everything", exact: true }).click();
  await expect(page.getByText("Nothing yet")).toBeVisible();
  expect(calls.some((c) => c.method === "POST" && c.url.endsWith("/api/memory/forget"))).toBe(true);
});

test("the empty note shows Nothing yet", async ({ page }) => {
  await stub(page, { enabled: true, followUps: [], about: [], interests: [] });
  await openMemory(page);
  await expect(page.getByText("Nothing yet")).toBeVisible();
  await expect(page.getByText("After your next talk, the things Bolo will ask you about show up here.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Forget everything Bolo knows" })).toHaveCount(0);
});

test("a failed remove brings the line back and says so", async ({ page }) => {
  await stub(page);
  await page.route("**/api/memory/lines/**", (r) => r.fulfill({ status: 500, json: { code: "unknown" } }));
  await openMemory(page);
  await page.getByRole("button", { name: "Remove: Cricket" }).click();
  await expect(page.locator(".bo-memory [role=alert]")).toHaveText("That didn't save. Try again.");
  await expect(page.getByText("Cricket")).toBeVisible();
});

test("fits at 390 px with no horizontal scroll", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await stub(page);
  await openMemory(page);
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
});
