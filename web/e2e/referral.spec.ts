import { expect, test, type Page } from "./fixtures";

const ACCOUNT = { learnerId: "a1", kind: "account", consent: { keepRecordings: true, policyVersion: "2026-11-v1" },
  profile: { goal: "job_interviews", fearedMoment: "interviews", talkMinutes: 5 }, settings: { buddySpeed: "normal", captions: true },
  account: { email: "r@x.com", name: "Rafi" } };
const STATUS = { offer: true, code: "ab12cd34", friends: ["Nadia"], counted: 1, rewards: 0, maxRewards: 3, premiumUntil: null, newReward: false };

/** P2d: an account on Home; `referral` is what GET /api/referral answers. */
async function stub(page: Page, referral: object = STATUS) {
  const seen: string[] = [];
  await page.addInitScript(() => {
    window.localStorage.setItem("bolo.onboarding.v1", JSON.stringify({ step: "talk" }));
    // No share sheet here (as on most computers): Share copies; the copy is recorded.
    Object.defineProperty(navigator, "share", { value: undefined, configurable: true });
    Object.defineProperty(navigator, "clipboard", { value: { writeText: async (t: string) => { (window as unknown as { copied: string }).copied = t; } }, configurable: true });
  });
  await page.route("**/api/me", (r) => r.fulfill({ json: ACCOUNT }));
  await page.route("**/api/auth/config", (r) => r.fulfill({ json: { enabled: false } }));
  await page.route("**/api/events", (r) => r.fulfill({ status: 202 }));
  await page.route("**/api/home", (r) => r.fulfill({ json: { primary: "talk", card: null,
    freeTalks: { plan: "free", window: "month", left: 0, total: 2, resetsOn: "2026-12-01" } } }));
  await page.route("**/api/referral", (r) => r.fulfill({ json: referral }));
  await page.route("**/api/referral/seen", (r) => { seen.push("seen"); return r.fulfill({ status: 204 }); });
  return seen;
}

test("Me → Invite friends: the offer, 3 slots, the link, Copy and Share", async ({ page }) => {
  await stub(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Me", exact: true }).click();
  await page.getByRole("button", { name: /Invite friends/ }).click();
  await expect(page.getByRole("heading", { name: "Invite 3 friends, get a month of Premium" })).toBeVisible();
  await expect(page.getByText("Nadia")).toBeVisible();
  await expect(page.getByText("Waiting for a friend")).toHaveCount(2);
  const link = page.getByLabel("Your invite link");
  await expect(link).toHaveValue(/\/\?ref=ab12cd34$/);
  await page.getByRole("button", { name: "Copy" }).click();
  await expect(page.getByText("Link copied")).toBeVisible();
  expect(await page.evaluate(() => (window as unknown as { copied: string }).copied)).toMatch(/\/\?ref=ab12cd34$/);
  await page.getByRole("button", { name: "Share invite link" }).click();
  await expect(page.getByText("Link copied")).toBeVisible();
});

test("on a phone the invite screen fits", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await stub(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Me", exact: true }).click();
  await page.getByRole("button", { name: /Invite friends/ }).click();
  await expect(page.getByRole("heading", { name: "Invite 3 friends, get a month of Premium" })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
});

test("out of free talks, the sheet offers the invite while it runs", async ({ page }) => {
  await stub(page);
  await page.route("**/api/talk", (r) => r.fulfill({ status: 422, json: { code: "free-limit" } }));
  await page.goto("/");
  await page.getByRole("button", { name: "Start talking" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Invite 3 friends, get a free month" }).click();
  await expect(page.getByRole("heading", { name: "Invite 3 friends, get a month of Premium" })).toBeVisible();
});

test("after the offer ends the sheet doesn't mention it", async ({ page }) => {
  await stub(page, { ...STATUS, offer: false });
  await page.route("**/api/talk", (r) => r.fulfill({ status: 422, json: { code: "free-limit" } }));
  await page.goto("/");
  await page.getByRole("button", { name: "Start talking" }).click();
  await expect(page.getByRole("dialog").getByRole("link", { name: "See plans" })).toBeVisible();
  await expect(page.getByText("Invite 3 friends, get a free month")).toHaveCount(0);
});

test("a month earned is announced on Home once", async ({ page }) => {
  const seen = await stub(page, { ...STATUS, friends: ["A", "B", "C"], counted: 3, rewards: 1, premiumUntil: "2026-12-12T06:00:00Z", newReward: true });
  await page.goto("/");
  await expect(page.getByText("A month of Premium is yours. Until 12 December.")).toBeVisible();
  await expect.poll(() => seen.length).toBe(1);
});
