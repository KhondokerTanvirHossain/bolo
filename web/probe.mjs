import { chromium } from "@playwright/test";
const b = await chromium.launch({ args: ["--use-fake-ui-for-media-stream", "--use-fake-device-for-media-stream"] });
const ctx = await b.newContext({ permissions: ["microphone"] });
const p = await ctx.newPage();
await p.goto("http://localhost:3100/");
console.log(await p.evaluate(async () => {
  try {
    const s = await navigator.mediaDevices.getUserMedia({ audio: { deviceId: undefined, echoCancellation: true } });
    const d = await navigator.mediaDevices.enumerateDevices();
    return { ok: true, tracks: s.getAudioTracks().map(t => t.getSettings().deviceId), devices: d.map(x => [x.kind, x.deviceId.slice(0,8), x.label]) };
  } catch (e) { return { ok: false, name: e.name, msg: e.message }; }
}));
await b.close();
