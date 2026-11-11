import { expect, test } from "./fixtures";

/** The privacy page Google's consent screen links to, in plain words (ACC-3/4, NFR-16). */
test("the privacy page says what's kept, why, for how long, who processes it and how to delete it", async ({ page }) => {
  await page.goto("/privacy");
  await expect(page).toHaveTitle("Bolo");
  await expect(page.getByRole("heading", { level: 1, name: "Privacy at Bolo" })).toBeVisible();
  for (const h of ["What Bolo keeps", "Why", "For how long", "Who processes it", "Deleting your data"]) {
    await expect(page.getByRole("heading", { level: 2, name: h })).toBeVisible();
  }
  // Review (P2b): what a help report carries is listed too.
  await expect(page.getByText("If you report a problem: your note, the screen you were on, your last talk's id and your browser's name.")).toBeVisible();
});

test("the setup questions don't claim to listen", async ({ page }) => {
  await page.addInitScript(() => window.localStorage.setItem("bolo.onboarding.v1", JSON.stringify({ step: "setup" })));
  await page.route("**/api/me", (r) => r.fulfill({ json: { learnerId: "1", kind: "guest",
    consent: { keepRecordings: true, policyVersion: "2026-11-v1" }, profile: null } }));
  await page.route("**/api/auth/config", (r) => r.fulfill({ json: { enabled: false } }));
  await page.route("**/api/events", (r) => r.fulfill({ status: 202 }));
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "What do you need English for most?" })).toBeVisible();
  await expect(page.getByText(/Say it, or click one/)).toHaveCount(0);
  await expect(page.getByText(/Click one\. Keys 1–4 work too\./)).toBeVisible();
});
