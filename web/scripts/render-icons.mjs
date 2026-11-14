// One-off: renders the notification badge (white sun-bubble on transparent, 96 px) from brand/logo/bolo-notification.svg.
// Run from web/: node scripts/render-icons.mjs. The app icons are resized from brand/logo/bolo-playstore-512.png with sips.
import { firefox } from "@playwright/test";
import { readFileSync, writeFileSync } from "node:fs";

const svg = readFileSync(new URL("../../brand/logo/bolo-notification.svg", import.meta.url), "utf8");
const d = /d="([^"]+)"/.exec(svg)[1];
const browser = await firefox.launch();
const page = await browser.newPage();
const png = await page.evaluate((path) => {
  const c = document.createElement("canvas");
  c.width = 96;
  c.height = 96;
  const g = c.getContext("2d");
  g.scale(96 / 24, 96 / 24);
  g.fillStyle = "#FFFFFF";
  g.fill(new Path2D(path));
  return c.toDataURL("image/png").split(",")[1];
}, d);
writeFileSync("public/icons/badge-96.png", Buffer.from(png, "base64"));
await browser.close();
