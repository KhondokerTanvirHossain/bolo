import { expect, test, type Page } from "./fixtures";

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
  await expect(page.getByText(/one-way code of your Google account/)).toBeVisible();
});

test("on a large phone (430 px), the shell uses the phone layout", async ({ page }) => {
  await page.setViewportSize({ width: 430, height: 932 });
  await stub(page, true);
  await page.goto("/");
  await page.getByRole("button", { name: "Me" }).click();
  await expect(page.getByText(/rahim@example.com/)).toBeVisible();
  const look = await page.evaluate(() => {
    const side = document.querySelector(".dk-side") as HTMLElement;
    const foot = document.querySelector(".dk-side__foot") as HTMLElement | null;
    const pageEl = document.querySelector(".dk-page") as HTMLElement;
    return {
      footShown: foot ? getComputedStyle(foot).display !== "none" : false,
      sideScrolls: side.scrollWidth > side.clientWidth,
      padding: getComputedStyle(pageEl).paddingLeft,
      width: document.documentElement.scrollWidth,
    };
  });
  expect(look).toEqual({ footShown: false, sideScrolls: false, padding: "16px", width: 430 });
});

test("the public privacy page scrolls to its last section", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 700 });
  await page.goto("/privacy");
  const last = page.getByRole("heading", { level: 2 }).last();
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  await page.mouse.move(195, 350);
  for (let i = 0; i < 10; i++) await page.mouse.wheel(0, 600);
  await expect(last).toBeInViewport();
  // P1d task 6: what stays after "delete everything" is disclosed.
  await expect(page.getByText(/one-way code made from your Google account/)).toBeVisible();
  // The founder's contact address for privacy questions (2026-11-10).
  await expect(page.getByRole("link", { name: "k.tanvir.hossain@gmail.com" })).toHaveAttribute("href", "mailto:k.tanvir.hossain@gmail.com");
});

test("typing a space into Delete everything's box never starts a talk", async ({ page }) => {
  await stub(page, true);
  let talks = 0;
  await page.route("**/api/talk", (r) => { talks++; return r.fulfill({ status: 201, json: { id: "x", minutes: 5 } }); });
  await page.goto("/");
  await page.getByRole("button", { name: "Me", exact: true }).click();
  await page.getByRole("button", { name: /Privacy/ }).click();
  await page.getByRole("button", { name: "Delete everything" }).click();
  await page.getByRole("textbox").pressSequentially("DE LETE");
  await page.waitForTimeout(300);
  expect(talks).toBe(0);
});

test("Delete everything keeps focus inside, and Esc keeps my data", async ({ page }) => {
  const s = await stub(page, true);
  await page.goto("/");
  await page.getByRole("button", { name: "Me", exact: true }).click();
  await page.getByRole("button", { name: /Privacy/ }).click();
  await page.getByRole("button", { name: "Delete everything" }).click();
  const dialog = page.getByRole("dialog");
  for (let i = 0; i < 6; i++) {
    await page.keyboard.press("Tab");
    expect(await dialog.evaluate((d) => d.contains(document.activeElement))).toBe(true);
  }
  await page.keyboard.press("Escape");
  await expect(dialog).toHaveCount(0);
  expect(s.deleted).toBe(false);
});

/** P3a: Settings → Talk view, Chat (default) or Classic (TLK-8, TLK-16 changed 2026-11-12). */
test("Talk view switches between Chat and Classic", async ({ page }) => {
  await stub(page, true);
  const puts: string[] = [];
  await page.route("**/api/me/settings", (r) => { puts.push(r.request().postData() ?? ""); return r.fulfill({ status: 204 }); });
  await page.goto("/");
  await page.getByRole("button", { name: "Me", exact: true }).click();
  await expect(page.getByRole("button", { name: "Chat", exact: true })).toHaveAttribute("aria-pressed", "true");
  await page.getByRole("button", { name: "Classic", exact: true }).click();
  await expect(page.getByRole("button", { name: "Classic", exact: true })).toHaveAttribute("aria-pressed", "true");
  expect(JSON.parse(puts.at(-1) ?? "{}")).toMatchObject({ talkView: "classic", buddySpeed: "normal", captions: true });
});
