import { expect, test, type Page } from "./fixtures";

/** SCN-1, SCN-2 (2026-11-15, DS v44): Free talk or a Standup rehearsal under the Talk button; its tip and history tag. */
const T = "66666666-6666-6666-6666-666666666666";
const ROW = () => ({ talkId: T, startedAt: new Date().toISOString(), minutes: 5, topic: "My morning update", status: "ready",
  fixes: ["Give a date, not \"soon\""], scenario: "standup" });

async function stub(page: Page, settings: Record<string, unknown> = {}) {
  const saved: Record<string, unknown>[] = [];
  let current = { learnerId: "a1", kind: "account", consent: { keepRecordings: true, policyVersion: "2026-11-v1" },
    profile: { goal: "work_meetings", fearedMoment: "speaking_up", talkMinutes: 5 }, account: { email: "r@x.com", name: "Rafi" },
    settings: { buddySpeed: "normal", captions: true, scenario: "free", difficulty: "realistic", ...settings } };
  await page.addInitScript(() => window.localStorage.setItem("bolo.onboarding.v1", JSON.stringify({ step: "talk" })));
  await page.route("**/api/me", (r) => r.fulfill({ json: current }));
  await page.route("**/api/me/settings", (r) => {
    const body = JSON.parse(r.request().postData() ?? "{}");
    saved.push(body);
    current = { ...current, settings: body };
    return r.fulfill({ status: 204 });
  });
  await page.route("**/api/auth/config", (r) => r.fulfill({ json: { enabled: false } }));
  await page.route("**/api/events", (r) => r.fulfill({ status: 202 }));
  await page.route("**/api/home", (r) => r.fulfill({ json: { primary: "talk", card: null,
    freeTalks: { plan: "free", window: "month", left: 2, total: 2, resetsOn: "2026-12-01" } } }));
  await page.route("**/api/progress", (r) => r.fulfill({ json: { effort: { talks: 1, minutes: 5 }, trends: { sessions: 1, after: 3, skills: {} },
    patterns: { active: [], fixed: [] }, latest: [ROW()] } }));
  await page.route("**/api/history", (r) => r.fulfill({ json: { sessions: [ROW()] } }));
  await page.route(`**/api/talk/${T}/debrief/viewed`, (r) => r.fulfill({ status: 204 }));
  await page.route(`**/api/talk/${T}/debrief`, (r) => r.fulfill({ json: {
    status: "ready", topic: "My morning update", scenario: "standup",
    strength: { text: "You gave your update clearly.", clip: null },
    fixes: [{ id: "f1", kind: "standup", rule: "give_a_date", title: "Give a date, not \"soon\"", said: "The report will be ready soon",
      better: "The report will be ready by Thursday", changed: ["by", "Thursday"], why: "A manager plans around your date.", clip: null, model: null }],
    skills: { pronunciation: "GOOD", fluency: "GOOD", sentenceBuilding: "GOOD", vocabulary: "GOOD" }, firstSession: false } }));
  await page.route(`**/api/talk/${T}/chat`, (r) => r.fulfill({ json: { topic: "My morning update", turns: [], marks: [] } }));
  return { saved };
}

for (const [name, viewport] of [["desktop", { width: 1280, height: 800 }], ["phone", { width: 390, height: 844 }]] as const) {
  test.describe(name, () => {
    test.use({ viewport });

    test("Change opens the picker; Friendly applies, closes and stays after a reload", async ({ page }) => {
      const { saved } = await stub(page);
      await page.goto("/");
      await expect(page.getByText(/Free talk · 5 min/)).toBeVisible();
      await page.getByRole("button", { name: "Change" }).click();
      const dialog = page.getByRole("dialog", { name: "What do you want to practise?" });
      await expect(dialog).toBeVisible();
      await expect(dialog.getByText("Realistic interrupts once or twice, like a real meeting.")).toBeVisible();
      await dialog.getByRole("radio", { name: /Standup rehearsal/ }).click();
      await expect(dialog.getByRole("button", { name: "Realistic" })).toHaveAttribute("aria-pressed", "true");
      await dialog.getByRole("button", { name: "Friendly" }).click();
      await expect(dialog).toBeHidden();
      await expect(page.getByText(/Standup rehearsal · Friendly · 5 min/)).toBeVisible();
      await expect.poll(() => saved.at(-1)).toMatchObject({ scenario: "standup", difficulty: "friendly", buddySpeed: "normal" });
      await page.reload();
      await expect(page.getByText(/Standup rehearsal · Friendly · 5 min/)).toBeVisible();
    });

    test("Free talk applies at once; Esc closes without changing", async ({ page }) => {
      const { saved } = await stub(page, { scenario: "standup", difficulty: "realistic" });
      await page.goto("/");
      await page.getByRole("button", { name: "Change" }).click();
      await page.keyboard.press("Escape");
      await expect(page.getByRole("dialog")).toBeHidden();
      await expect(page.getByText(/Standup rehearsal · Realistic · 5 min/)).toBeVisible();
      expect(saved).toHaveLength(0);
      await page.getByRole("button", { name: "Change" }).click();
      await page.getByRole("radio", { name: /Free talk/ }).click();
      await expect(page.getByText(/Free talk · 5 min/)).toBeVisible();
      await expect.poll(() => saved.at(-1)).toMatchObject({ scenario: "free" });
    });

    test("with I stammer on, the picker says Bolo won't cut in", async ({ page }) => {
      await stub(page, { stammer: true });
      await page.goto("/");
      await page.getByRole("button", { name: "Change" }).click();
      await expect(page.getByText("With I stammer on, Bolo won't cut in.")).toBeVisible();
    });
  });
}

test("a standup's history row and its Standup tip card", async ({ page }) => {
  await stub(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Me" }).click();
  await page.getByRole("button", { name: "Progress" }).click();
  await page.getByRole("button", { name: "See all" }).click();
  await expect(page.getByText("Standup rehearsal · 5 min")).toBeVisible();
  await page.getByRole("button", { name: /My morning update/ }).click();
  await page.getByText("Give a date, not \"soon\"").first().click();
  await expect(page.getByText(/Standup tip/).first()).toBeVisible();
  await expect(page.getByText("A manager plans around your date.")).toBeVisible();
  await expect(page.getByText("\"The report will be ready soon\"")).toBeVisible();
});
