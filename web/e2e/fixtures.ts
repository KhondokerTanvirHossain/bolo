import { test as base, expect } from "@playwright/test";

/**
 * Every e2e test answers Google Fonts locally (an empty stylesheet, no font files): page loads never wait on the
 * internet, and what the tests check (behaviour and layout) doesn't depend on the web fonts. Icons keep a 1em box
 * without their font (bolo.css), so layouts measure the same.
 */
export const test = base.extend<{ noWebFonts: void }>({
  noWebFonts: [async ({ page }, use) => {
    await page.route(/^https:\/\/fonts\.(googleapis|gstatic)\.com\//, (r) =>
      r.request().resourceType() === "stylesheet" ? r.fulfill({ status: 200, contentType: "text/css", body: "" }) : r.abort());
    await use();
  }, { auto: true }],
});

export { expect };
export type { Page, WebSocketRoute } from "@playwright/test";
