/**
 * Web Push in this browser (daily reminders). iPhone: only Bolo opened from the Home Screen (iOS 16.4+) can get
 * notifications; every iPhone browser is Safari underneath, Chrome included.
 */
import { addDevice, removeDevice } from "./api";

export type PushSupport = "ok" | "iphone-tab" | "old-iphone" | "unsupported";

export type PushEnv = { ua: string; maxTouchPoints: number; standalone: boolean; hasPush: boolean };

export function pushSupport(env: PushEnv): PushSupport {
  const iphone = /iPhone|iPad|iPod/.test(env.ua) || (/Macintosh/.test(env.ua) && env.maxTouchPoints > 1 && !/Chrome\//.test(env.ua));
  if (iphone) {
    const v = /OS (\d+)_(\d+)/.exec(env.ua) ?? /Version\/(\d+)\.(\d+)/.exec(env.ua);
    if (v && (Number(v[1]) < 16 || (Number(v[1]) === 16 && Number(v[2]) < 4))) return "old-iphone";
    if (!env.standalone) return "iphone-tab";
    return env.hasPush ? "ok" : "unsupported";
  }
  return env.hasPush ? "ok" : "unsupported";
}

export function browserEnv(): PushEnv {
  const nav = navigator as Navigator & { standalone?: boolean };
  return {
    ua: nav.userAgent,
    maxTouchPoints: nav.maxTouchPoints ?? 0,
    standalone: nav.standalone === true || window.matchMedia?.("(display-mode: standalone)").matches === true,
    hasPush: "serviceWorker" in navigator && "PushManager" in window && "Notification" in window,
  };
}

export const currentSupport = () => pushSupport(browserEnv());

function key(base64url: string): Uint8Array {
  const pad = "=".repeat((4 - (base64url.length % 4)) % 4);
  const raw = atob((base64url + pad).replace(/-/g, "+").replace(/_/g, "/"));
  return Uint8Array.from(raw, (c) => c.charCodeAt(0));
}

async function registration(): Promise<ServiceWorkerRegistration> {
  await navigator.serviceWorker.register("/sw.js");
  return navigator.serviceWorker.ready;
}

/** Asks (only after a tap), subscribes this browser and tells Bolo. */
export async function enablePush(publicKey: string): Promise<"on" | "denied" | "failed"> {
  try {
    const permission = await Notification.requestPermission();
    if (permission !== "granted") return "denied";
    const reg = await registration();
    const sub = (await reg.pushManager.getSubscription())
      ?? (await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key(publicKey) as BufferSource }));
    const json = sub.toJSON() as { endpoint?: string; keys?: { p256dh?: string; auth?: string } };
    const saved = await addDevice(json.endpoint ?? sub.endpoint, json.keys?.p256dh ?? "", json.keys?.auth ?? "");
    return saved ? "on" : "failed";
  } catch {
    return "failed";
  }
}

/** This browser stops getting reminders (sign-out, or Remind me off). */
export async function disablePushHere(): Promise<void> {
  try {
    if (!("serviceWorker" in navigator)) return;
    const reg = await navigator.serviceWorker.getRegistration("/sw.js");
    const sub = await reg?.pushManager.getSubscription();
    if (!sub) return;
    await removeDevice(sub.endpoint);
    await sub.unsubscribe();
  } catch {
    // nothing to undo
  }
}

/** Whether this browser gets reminders: subscribed here, not yet, or notifications blocked. */
export async function pushState(): Promise<"here" | "not-here" | "blocked"> {
  try {
    if (!("Notification" in window)) return "not-here";
    if (Notification.permission === "denied") return "blocked";
    const reg = await navigator.serviceWorker?.getRegistration("/sw.js");
    const sub = await reg?.pushManager.getSubscription();
    return sub && Notification.permission === "granted" ? "here" : "not-here";
  } catch {
    return "not-here";
  }
}
