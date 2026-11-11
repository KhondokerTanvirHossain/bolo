import { expect, test, type Page } from "./fixtures";

const USERS = [{ id: "u1", email: "friend@x.com", name: "Friend", kind: "account", unlimited: false, plan: "free", planEndsAt: null,
  talksThisMonth: 3, lastTalkAt: "2026-11-10T10:00:00Z" }];
const SETTINGS = { dailyTalkCap: 300, plans: [{ plan: "basic", monthlyTalks: 8, priceBdt: 300, priceUsd: 3 },
  { plan: "premium", monthlyTalks: 20, priceBdt: 500, priceUsd: 5 }, { plan: "ultimate", monthlyTalks: 45, priceBdt: 1000, priceUsd: 10 }] };

async function stub(page: Page, admin: boolean) {
  const sent: { grants: string[]; settings: string[] } = { grants: [], settings: [] };
  await page.route("**/api/admin/settings", async (r) => {
    if (!admin) return r.fulfill({ status: 404 });
    if (r.request().method() === "PUT") { sent.settings.push(r.request().postData() ?? ""); return r.fulfill({ status: 204 }); }
    return r.fulfill({ json: SETTINGS });
  });
  await page.route("**/api/admin/users?**", (r) => r.fulfill({ json: { users: USERS } }));
  await page.route("**/api/admin/users/u1/grant", (r) => { sent.grants.push(r.request().postData() ?? ""); return r.fulfill({ status: 204 }); });
  await page.route("**/api/admin/usage?**", (r) => r.fulfill({ json: { now: { live: 1, today: 12, cap: 300 },
    days: [{ date: "2026-11-11", talks: 12, minutes: 50, newAccounts: 3, costUsd: 1.25 }] } }));
  let helpOpen = true;
  await page.route("**/api/admin/help", (r) => r.fulfill({ json: { requests: helpOpen ? [{ id: 7, email: "stuck@x.com", message: "The mic button does nothing",
    context: { view: "home", talkId: "talk-9" }, at: "2026-11-11T08:00:00Z" }] : [] } }));
  let paid = false;
  await page.route("**/api/admin/payments", (r) => r.fulfill({ json: { payments: [{ id: 5, at: "2026-11-12T08:00:00Z", email: "payer@x.com", plan: "premium",
    amountBdt: 500, trxId: "9AB3CD7EF1", payerNumber: "01812345678", status: paid ? "approved" : "pending", reason: null }] } }));
  await page.route("**/api/admin/payments/5/approve", (r) => { paid = true; (sent as { approvals?: number }).approvals = 1; return r.fulfill({ status: 204 }); });
  await page.route("**/api/admin/waitlist", (r) => r.fulfill({ json: { counts: [{ plan: "basic", bd: 3, intl: 1 }, { plan: "premium", bd: 0, intl: 2 }],
    entries: [{ email: "far@x.com", plan: "premium", region: "intl", timeZone: "Europe/London", at: "2026-11-11T09:30:00Z" }] } }));
  await page.route("**/api/admin/help/7/done", (r) => { helpOpen = false; return r.fulfill({ status: 204 }); });
  return sent;
}

test("someone who isn't the admin sees Not found", async ({ page }) => {
  await stub(page, false);
  await page.goto("/admin");
  await expect(page.getByRole("heading", { name: "Not found" })).toBeVisible();
});

test("the admin finds a friend and gives them Premium", async ({ page }) => {
  const s = await stub(page, true);
  await page.goto("/admin");
  await expect(page.getByRole("heading", { name: "Bolo admin" })).toBeVisible();
  await expect(page.getByText("friend@x.com")).toBeVisible();
  await page.getByRole("button", { name: "Give premium" }).click();
  await expect(page.getByText("Gave friend@x.com premium for 30 days.")).toBeVisible();
  expect(JSON.parse(s.grants[0])).toMatchObject({ plan: "premium", days: 30 });
});

test("usage shows today against the cap; settings saves the cap", async ({ page }) => {
  const s = await stub(page, true);
  await page.goto("/admin");
  await page.getByRole("tab", { name: "Usage" }).click();
  await expect(page.getByText("Live talks now: 1 · Today: 12 of 300 (daily cap)")).toBeVisible();
  await page.getByRole("tab", { name: "Settings" }).click();
  await page.getByLabel("Daily talk cap").fill("150");
  await page.getByRole("button", { name: "Save" }).click();
  await expect(page.getByText("Saved.")).toBeVisible();
  expect(JSON.parse(s.settings[0]).dailyTalkCap).toBe(150);
});

test("on a phone, the admin users list scrolls inside its box, not the page", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await stub(page, true);
  await page.goto("/admin");
  await expect(page.getByText("friend@x.com")).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
});

test("the admin's Help tab lists a report, and Done clears it", async ({ page }) => {
  await stub(page, true);
  await page.goto("/admin");
  await page.getByRole("tab", { name: "Help" }).click();
  await expect(page.getByText("The mic button does nothing")).toBeVisible();
  await expect(page.getByText("view: home · talkId: talk-9")).toBeVisible();
  await page.getByRole("button", { name: "Done" }).click();
  await expect(page.getByText("No open reports.")).toBeVisible();
});

/** Review (P2a): an emptied cap field must not save as 0 (that rests everyone); 0 itself needs a yes. */
test("an empty cap isn't saved, and a cap of 0 asks first", async ({ page }) => {
  const s = await stub(page, true);
  await page.goto("/admin");
  await page.getByRole("tab", { name: "Settings" }).click();
  await page.getByLabel("Daily talk cap").fill("");
  await page.getByRole("button", { name: "Save" }).click();
  await expect(page.getByText("Enter a daily cap (0 or more).")).toBeVisible();
  expect(s.settings).toHaveLength(0);

  await page.getByLabel("Daily talk cap").fill("0");
  page.once("dialog", (d) => { expect(d.message()).toContain("No one can start a talk"); void d.dismiss(); });
  await page.getByRole("button", { name: "Save" }).click();
  await expect(page.getByText("Not saved.")).toBeVisible();
  expect(s.settings).toHaveLength(0);

  page.once("dialog", (d) => void d.accept());
  await page.getByRole("button", { name: "Save" }).click();
  await expect(page.getByText("Saved.")).toBeVisible();
  expect(JSON.parse(s.settings[0]).dailyTalkCap).toBe(0);
});

/** P2c: who is waiting for which plan. */
test("the Waitlist tab shows counts by plan and region, and who is waiting", async ({ page }) => {
  await stub(page, true);
  await page.goto("/admin");
  await page.getByRole("tab", { name: "Waitlist" }).click();
  await expect(page.getByText("Basic: 4 (৳ 3, $ 1)")).toBeVisible();
  await expect(page.getByText("Premium: 2 (৳ 0, $ 2)")).toBeVisible();
  await expect(page.getByRole("cell", { name: "far@x.com" })).toBeVisible();
  await expect(page.getByRole("cell", { name: "Europe/London" })).toBeVisible();
});

/** P2e: the founder checks each TrxID in the bKash app, then approves. */
test("the Payments tab lists a payment to check, and Approve moves it to decided", async ({ page }) => {
  const s = await stub(page, true);
  await page.goto("/admin");
  await page.getByRole("tab", { name: "Payments" }).click();
  await expect(page.getByRole("cell", { name: "9AB3CD7EF1" })).toBeVisible();
  await expect(page.getByRole("cell", { name: "৳500" })).toBeVisible();
  await page.getByRole("button", { name: "Approve" }).click();
  await expect(page.getByRole("cell", { name: "approved" })).toBeVisible();
  expect((s as { approvals?: number }).approvals).toBe(1);
});

test("Settings saves the bKash number", async ({ page }) => {
  const s = await stub(page, true);
  await page.goto("/admin");
  await page.getByRole("tab", { name: "Settings" }).click();
  await page.getByLabel("bKash number for payments").fill("01712345678");
  await page.getByRole("button", { name: "Save" }).click();
  await expect(page.getByText("Saved.")).toBeVisible();
  expect(JSON.parse(s.settings[0]).bkashNumber).toBe("01712345678");
});
