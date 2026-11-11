import { expect, test, type Page } from "./fixtures";

const PLANS = { plans: [{ plan: "basic", monthlyTalks: 8, priceBdt: 300, priceUsd: 3 }, { plan: "premium", monthlyTalks: 20, priceBdt: 500, priceUsd: 5 },
  { plan: "ultimate", monthlyTalks: 45, priceBdt: 1000, priceUsd: 10 }] };
const ACCOUNT = { learnerId: "a1", kind: "account", consent: { keepRecordings: true, policyVersion: "2026-11-v1" },
  profile: { goal: "job_interviews", fearedMoment: "interviews", talkMinutes: 5 }, account: { email: "r@x.com", name: "Rafi" } };

/** P2c: the pricing page. `who` is the visitor: nobody (no session), a guest, or an account. */
async function stub(page: Page, who: "nobody" | "guest" | "account") {
  const joins: string[] = [];
  let mine: object | null = null;
  await page.route("**/api/plans", (r) => r.fulfill({ json: PLANS }));
  await page.route("**/api/auth/config", (r) => r.fulfill({ json: { enabled: false } }));
  await page.route("**/api/me", (r) => who === "nobody" ? r.fulfill({ status: 401 })
    : r.fulfill({ json: who === "account" ? ACCOUNT : { ...ACCOUNT, kind: "guest", account: null } }));
  await page.route("**/api/home", (r) => r.fulfill({ json: { primary: "talk", card: null,
    freeTalks: { plan: "free", window: "first_week", left: 3, total: 7, resetsOn: "2026-12-01" } } }));
  await page.route("**/api/waitlist/me", (r) => (mine ? r.fulfill({ json: mine }) : r.fulfill({ status: 204 })));
  await page.route("**/api/waitlist", (r) => {
    joins.push(r.request().postData() ?? "");
    if (who !== "account") return r.fulfill({ status: 422, json: { code: "sign-in" } });
    mine = JSON.parse(r.request().postData() ?? "{}");
    return r.fulfill({ status: 204 });
  });
  return joins;
}

test.describe("in Bangladesh", () => {
  test.use({ timezoneId: "Asia/Dhaka" });

  test("a signed-out visitor sees four plans in taka, Basic chosen", async ({ page }) => {
    await stub(page, "nobody");
    await page.goto("/pricing");
    await expect(page.getByRole("heading", { level: 1, name: "Choose a plan" })).toBeVisible();
    for (const n of ["Free", "Basic", "Premium", "Ultimate"]) await expect(page.getByRole("radio", { name: new RegExp(`^${n}`) })).toBeVisible();
    await expect(page.getByRole("radio", { name: /^Basic/ })).toHaveAttribute("aria-checked", "true");
    await expect(page.getByText("৳300 a month", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Join the list for Basic · ৳300 a month" })).toBeVisible();
    await expect(page.getByText("bKash payments open in a few days. Join the list and we'll email you first.")).toBeVisible();
  });

  test("on a phone it fits, and the arrow keys move between plans", async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await stub(page, "nobody");
    await page.goto("/pricing");
    await page.getByRole("radio", { name: /^Basic/ }).focus();
    await page.keyboard.press("ArrowDown");
    await expect(page.getByRole("radio", { name: /^Premium/ })).toHaveAttribute("aria-checked", "true");
    await expect(page.getByRole("radio", { name: /^Premium/ })).toBeFocused();
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  });
});

test.describe("outside Bangladesh", () => {
  test.use({ timezoneId: "Europe/London" });

  test("prices are in dollars; the switch flips to taka and survives a reload", async ({ page }) => {
    await stub(page, "nobody");
    await page.goto("/pricing");
    await expect(page.getByText("$3 a month", { exact: true })).toBeVisible();
    await expect(page.getByText("Card payments are coming soon. Join the waitlist and we'll email you when they open.")).toBeVisible();
    await page.getByRole("button", { name: "In Bangladesh? See prices in ৳" }).click();
    await expect(page.getByText("৳300 a month", { exact: true })).toBeVisible();
    await page.reload();
    await expect(page.getByText("৳300 a month", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Not in Bangladesh? See prices in $" })).toBeVisible();
  });

  test("an account joins the waitlist for Premium", async ({ page }) => {
    const joins = await stub(page, "account");
    await page.goto("/pricing");
    await expect(page.getByRole("radio", { name: /^Free/ })).toContainText("Your plan");
    await page.getByRole("radio", { name: /^Premium/ }).click();
    await page.getByRole("button", { name: "Join the waitlist for Premium" }).click();
    await expect(page.getByText("You're on the list for Premium. We'll email you at r@x.com.")).toBeVisible();
    expect(JSON.parse(joins[0])).toMatchObject({ plan: "premium", region: "intl", timeZone: "Europe/London" });
  });

  test("a guest is told joining signs in with Google; if sign-in is off, it says so", async ({ page }) => {
    const joins = await stub(page, "guest");
    await page.goto("/pricing");
    await expect(page.getByText("Sign in with Google to join the list.")).toBeVisible();
    await page.getByRole("button", { name: "Join the waitlist for Basic" }).click();
    await expect(page.getByText("Sign-in didn't work. Try again.")).toBeVisible();
    expect(joins).toHaveLength(0);
  });
});

test("out of free talks, See plans opens the pricing page", async ({ page }) => {
  await page.addInitScript(() => window.localStorage.setItem("bolo.onboarding.v1", JSON.stringify({ step: "talk" })));
  await stub(page, "account");
  await page.route("**/api/events", (r) => r.fulfill({ status: 202 }));
  await page.route("**/api/talk", (r) => r.fulfill({ status: 422, json: { code: "free-limit" } }));
  await page.goto("/");
  await page.getByRole("button", { name: "Start talking" }).click();
  await page.getByRole("dialog").getByRole("link", { name: "See plans" }).click();
  await expect(page).toHaveURL(/\/pricing$/);
  await expect(page.getByRole("heading", { level: 1, name: "Choose a plan" })).toBeVisible();
});

test("the sidebar's plan card and Me's plan row link to the plans", async ({ page }) => {
  await page.addInitScript(() => window.localStorage.setItem("bolo.onboarding.v1", JSON.stringify({ step: "talk" })));
  await stub(page, "account");
  await page.route("**/api/events", (r) => r.fulfill({ status: 202 }));
  await page.goto("/");
  await expect(page.locator("[data-side-plan]").getByRole("link", { name: "See plans" })).toHaveAttribute("href", "/pricing");
  await page.getByRole("button", { name: "Me", exact: true }).click();
  await expect(page.locator("main").getByRole("link", { name: "See plans" })).toHaveAttribute("href", "/pricing");
});

/** Review (P2c): an existing learner signs in from /pricing on a new browser, then goes back to Bolo: no onboarding again. */
test("signing in from the pricing page, then Back to Bolo, opens the app, not onboarding", async ({ page }) => {
  await stub(page, "account");
  await page.route("**/api/events", (r) => r.fulfill({ status: 202 }));
  await page.route("**/api/me/day1", (r) => r.fulfill({ status: 404 }));
  await page.goto("/pricing");
  await page.getByRole("link", { name: "Back to Bolo" }).click();
  await expect(page.getByRole("button", { name: "Start talking" })).toBeVisible();
});
