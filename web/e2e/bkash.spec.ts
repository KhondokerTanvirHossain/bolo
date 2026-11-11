import { expect, test, type Page } from "./fixtures";

const PLANS = { plans: [{ plan: "basic", monthlyTalks: 8, priceBdt: 300, priceUsd: 3 }, { plan: "premium", monthlyTalks: 20, priceBdt: 500, priceUsd: 5 },
  { plan: "ultimate", monthlyTalks: 45, priceBdt: 1000, priceUsd: 10 }] };
const ACCOUNT = { learnerId: "a1", kind: "account", consent: { keepRecordings: true, policyVersion: "2026-11-v1" },
  profile: { goal: "job_interviews", fearedMoment: "interviews", talkMinutes: 5 }, settings: { buddySpeed: "normal", captions: true },
  account: { email: "r@x.com", name: "Rafi" } };

test.use({ timezoneId: "Asia/Dhaka" });

/** P2e: an account in Bangladesh; `number` null means bKash payments are off; `mine` is GET /api/payments/me. */
async function stub(page: Page, { number = "01712345678" as string | null, mine = null as object | null } = {}) {
  const pays: string[] = [];
  let current = mine;
  await page.addInitScript(() => {
    window.localStorage.setItem("bolo.onboarding.v1", JSON.stringify({ step: "talk" }));
    Object.defineProperty(navigator, "clipboard", { value: { writeText: async (t: string) => { (window as unknown as { copied: string }).copied = t; } }, configurable: true });
  });
  await page.route("**/api/plans", (r) => r.fulfill({ json: PLANS }));
  await page.route("**/api/me", (r) => r.fulfill({ json: ACCOUNT }));
  await page.route("**/api/auth/config", (r) => r.fulfill({ json: { enabled: false } }));
  await page.route("**/api/events", (r) => r.fulfill({ status: 202 }));
  await page.route("**/api/referral", (r) => r.fulfill({ json: { offer: false, code: "ab12cd34", friends: [], counted: 0, rewards: 0, maxRewards: 3, premiumUntil: null, newReward: false } }));
  await page.route("**/api/home", (r) => r.fulfill({ json: { primary: "talk", card: null,
    freeTalks: { plan: "free", window: "month", left: 1, total: 2, resetsOn: "2026-12-01" } } }));
  await page.route("**/api/waitlist/me", (r) => r.fulfill({ status: 204 }));
  await page.route("**/api/payments/bkash", (r) => (number ? r.fulfill({ json: { number } }) : r.fulfill({ status: 204 })));
  await page.route("**/api/payments/me", (r) => (current ? r.fulfill({ json: current }) : r.fulfill({ status: 204 })));
  await page.route("**/api/payments", (r) => {
    pays.push(r.request().postData() ?? "");
    const body = JSON.parse(r.request().postData() ?? "{}");
    current = { status: "pending", plan: body.plan, trxId: body.trxId, reason: null };
    return r.fulfill({ status: 204 });
  });
  return pays;
}

test("pay with bKash: the number and amount, Copy, then the TrxID is sent for checking", async ({ page }) => {
  const pays = await stub(page);
  await page.goto("/pricing");
  await page.getByRole("button", { name: "Pay with bKash · ৳300" }).click();
  const sheet = page.getByRole("dialog", { name: "Pay with bKash" });
  await expect(sheet.getByText("01712345678")).toBeVisible();
  await expect(sheet.getByText("৳300", { exact: true })).toBeVisible();
  await expect(sheet.getByText("Basic for 30 days from approval. It doesn't renew by itself.", { exact: false })).toBeVisible();
  await sheet.getByRole("button", { name: "Copy number" }).click();
  expect(await page.evaluate(() => (window as unknown as { copied: string }).copied)).toBe("01712345678");
  await sheet.getByLabel("TrxID").fill("9ab3cd7ef1");
  await sheet.getByLabel("Your bKash number").fill("01812345678");
  await sheet.getByRole("button", { name: "Send for checking" }).click();
  await expect(page.getByText("Thanks. We're checking your payment, usually within a few hours. Your plan starts when it's confirmed.")).toBeVisible();
  expect(JSON.parse(pays[0])).toEqual({ plan: "basic", trxId: "9AB3CD7EF1", payerNumber: "01812345678" });
  await page.keyboard.press("Escape");
  await expect(page.getByText("Payment being checked (TrxID 9AB3CD7EF1).")).toBeVisible();
});

test("a pending payment shows on Me's plan row", async ({ page }) => {
  await stub(page, { mine: { status: "pending", plan: "basic", trxId: "9AB3CD7EF1", reason: null } });
  await page.goto("/");
  await page.getByRole("button", { name: "Me", exact: true }).click();
  await expect(page.getByText("Payment being checked (TrxID 9AB3CD7EF1).")).toBeVisible();
});

test("a rejected payment says why", async ({ page }) => {
  await stub(page, { mine: { status: "rejected", plan: "basic", trxId: "9AB3CD7EF1", reason: "TrxID not found" } });
  await page.goto("/pricing");
  await expect(page.getByText("We couldn't confirm your payment (TrxID not found).", { exact: false })).toBeVisible();
});

test("with bKash off, Bangladesh keeps the list", async ({ page }) => {
  await stub(page, { number: null });
  await page.goto("/pricing");
  await expect(page.getByRole("button", { name: "Join the list for Basic · ৳300 a month" })).toBeVisible();
  await expect(page.getByRole("button", { name: /Pay with bKash/ })).toHaveCount(0);
});

test("on a phone the sheet fits, refuses a bad TrxID, and Esc closes it", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const pays = await stub(page);
  await page.goto("/pricing");
  await page.getByRole("button", { name: "Pay with bKash · ৳300" }).click();
  const sheet = page.getByRole("dialog", { name: "Pay with bKash" });
  await expect(sheet).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await expect(sheet.getByLabel("Your bKash number")).toHaveAttribute("inputmode", "numeric");
  await sheet.getByLabel("TrxID").fill("AB-12");
  await sheet.getByLabel("Your bKash number").fill("01812345678");
  await sheet.getByRole("button", { name: "Send for checking" }).click();
  await expect(sheet.getByText("Check the TrxID", { exact: false })).toBeVisible();
  expect(pays).toHaveLength(0);
  await page.keyboard.press("Escape");
  await expect(sheet).toHaveCount(0);
});
