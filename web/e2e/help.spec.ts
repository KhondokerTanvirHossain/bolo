import { expect, test, type Page } from "./fixtures";

async function stub(page: Page, status = 204) {
  await page.addInitScript(() => window.localStorage.setItem("bolo.onboarding.v1", JSON.stringify({ step: "talk" })));
  const reports: string[] = [];
  await page.route("**/api/me", (r) => r.fulfill({ json: { learnerId: "a1", kind: "account", consent: { keepRecordings: true, policyVersion: "2026-11-v1" },
    profile: { goal: "job_interviews", fearedMoment: "interviews", talkMinutes: 5 }, settings: { buddySpeed: "normal", captions: true },
    account: { email: "r@x.com", name: "Rafi" } } }));
  await page.route("**/api/auth/config", (r) => r.fulfill({ json: { enabled: false } }));
  await page.route("**/api/events", (r) => r.fulfill({ status: 202 }));
  await page.route("**/api/home", (r) => r.fulfill({ json: { primary: "talk", card: { kind: "feedback", talkId: "talk-9" } } }));
  await page.route("**/api/help", (r) => { reports.push(r.request().postData() ?? ""); return status === 429
    ? r.fulfill({ status: 429, json: { code: "slow-down" } }) : r.fulfill({ status }); });
  return reports;
}

test("a stuck learner reports a problem from the sidebar, with the screen and last talk", async ({ page }) => {
  const reports = await stub(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Report a problem" }).click();
  await page.getByLabel("What went wrong?").fill("The mic button does nothing");
  await page.getByRole("button", { name: "Send" }).click();
  await expect(page.getByText("Thanks. We read every one.")).toBeVisible();
  const sent = JSON.parse(reports[0]);
  expect(sent.message).toBe("The mic button does nothing");
  expect(sent.context).toMatchObject({ view: "home", talkId: "talk-9" });
});

test("too many reports says so; Esc closes the dialog", async ({ page }) => {
  await stub(page, 429);
  await page.goto("/");
  await page.getByRole("button", { name: "Report a problem" }).click();
  await page.getByLabel("What went wrong?").fill("again");
  await page.getByRole("button", { name: "Send" }).click();
  await expect(page.getByText("That's a lot of reports. Please try again in a while.")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("heading", { name: "Report a problem" })).toHaveCount(0);
});

test("on a phone, Help is in Me and the dialog fits", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await stub(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Me", exact: true }).click();
  await page.getByRole("button", { name: /Help/ }).click();
  await expect(page.getByRole("heading", { name: "Report a problem" })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
});

for (const [path, title] of [["/terms", "Terms of Use"], ["/refund", "Refund policy"]] as const) {
  test(`${title} is public, scrolls to its end and gives the contact`, async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 700 });
    await page.goto(path);
    await expect(page.getByRole("heading", { level: 1, name: title })).toBeVisible();
    await page.mouse.move(195, 350);
    for (let i = 0; i < 10; i++) await page.mouse.wheel(0, 600);
    await expect(page.getByRole("link", { name: "k.tanvir.hossain@gmail.com" })).toBeInViewport();
  });
}
