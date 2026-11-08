import { expect, test, type Page } from "@playwright/test";

const consent = { keepRecordings: true, policyVersion: "2026-11-v1" };
const profile = { goal: "job_interviews", fearedMoment: "phone_calls", talkMinutes: 5 };

async function stub(page: Page, account: boolean) {
  await page.addInitScript(() => {
    if (!sessionStorage.getItem("seeded")) {
      localStorage.setItem("bolo.onboarding.v1", JSON.stringify({ step: "talk", keepRecordings: true }));
      sessionStorage.setItem("seeded", "1");
    }
  });
  const state = { keep: true, deleted: false, consents: [] as string[] };
  await page.route("**/api/me", (r) => (state.deleted ? r.fulfill({ status: 401, json: { code: "signed-out" } }) : r.fulfill({ json: {
    learnerId: "a1", kind: account ? "account" : "guest", consent: { ...consent, keepRecordings: state.keep }, profile,
    settings: { buddySpeed: "normal", captions: true }, account: account ? { email: "rahim@example.com", name: "Rahim" } : null } })));
  await page.route("**/api/consent", async (r) => {
    const body = r.request().postData() ?? "";
    state.consents.push(body);
    state.keep = JSON.parse(body).keepRecordings;
    await r.fulfill({ status: 204 });
  });
  await page.route("**/api/me/delete", async (r) => { state.deleted = true; await r.fulfill({ status: 204 }); });
  await page.route("**/api/auth/config", (r) => r.fulfill({ json: { enabled: true, apiKey: "k", authDomain: "d", projectId: "p", appId: "a" } }));
  await page.route("**/api/home", (r) => r.fulfill({ json: { primary: "talk", card: null } }));
  await page.route("**/api/events", (r) => r.fulfill({ status: 202 }));
  return state;
}

test("Me shows the account, goals, speaking and privacy", async ({ page }) => {
  await stub(page, true);
  await page.goto("/");
  await page.getByRole("button", { name: "Me" }).click();
  await expect(page.getByText(/rahim@example.com/)).toBeVisible();
  await expect(page.getByText("Job interviews")).toBeVisible();
  await expect(page.getByText("Phone calls")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Speaking" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Sign out" })).toBeVisible();
});

test("turning off Keep my recordings saves a consent record and stays off after a reload", async ({ page }) => {
  const s = await stub(page, true);
  await page.goto("/");
  await page.getByRole("button", { name: "Me" }).click();
  await page.getByRole("button", { name: /Privacy/ }).click();
  const toggle = page.getByRole("switch", { name: "Keep my recordings" });
  await expect(toggle).toHaveAttribute("aria-checked", "true");
  await toggle.click();
  await expect.poll(() => s.consents.length).toBe(1);
  expect(s.consents[0]).toContain("\"keepRecordings\":false");
  await expect(page.getByText(/from your next talk/)).toBeVisible();
  await page.reload();
  await page.getByRole("button", { name: "Me" }).click();
  await page.getByRole("button", { name: /Privacy/ }).click();
  await expect(page.getByRole("switch", { name: "Keep my recordings" })).toHaveAttribute("aria-checked", "false");
});

test("Delete everything needs DELETE, then returns to the welcome screen", async ({ page }) => {
  await stub(page, true);
  await page.goto("/");
  await page.getByRole("button", { name: "Me" }).click();
  await page.getByRole("button", { name: /Privacy/ }).click();
  await page.getByRole("button", { name: "Delete everything" }).click();
  const confirm = page.getByRole("dialog").getByRole("button", { name: "Delete everything" });
  await expect(confirm).toBeDisabled();
  await page.getByLabel("Type DELETE to confirm").fill("DELETE");
  await confirm.click();
  await expect(page.getByRole("button", { name: "Meet Bolo" })).toBeVisible();
});

test("a guest's Me offers Save your progress", async ({ page }) => {
  await stub(page, false);
  await page.goto("/");
  await page.getByRole("button", { name: "Me" }).click();
  await expect(page.getByText(/Guest · your progress is in this browser only/)).toBeVisible();
  await expect(page.getByRole("button", { name: "Continue with Google" })).toBeVisible();
});

test("on a phone, Me and Privacy fit the screen", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await stub(page, true);
  await page.goto("/");
  await page.getByRole("button", { name: "Me" }).click();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.getByRole("button", { name: /Privacy/ }).click();
  await expect(page.getByRole("switch", { name: "Keep my recordings" })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
});
