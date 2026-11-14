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

/**
 * Starts the browser's own box straight from the tap: Safari shows it only from a user gesture, so call this before
 * any await in the click handler (review, reminders I8).
 */
export function askPermission(): Promise<NotificationPermission> | null {
  try {
    return "Notification" in window ? Notification.requestPermission() : null;
  } catch {
    return null;
  }
}

/** True when the subscription was made with this key (else it answers 403 forever and must be remade). */
function sameKey(sub: PushSubscription, publicKey: string): boolean {
  const k = sub.options?.applicationServerKey;
  if (!k) return true;
  const a = new Uint8Array(k);
  const b = key(publicKey);
  return a.length === b.length && a.every((v, i) => v === b[i]);
}

async function subscribeAndSend(reg: ServiceWorkerRegistration, publicKey: string): Promise<boolean> {
  let sub = await reg.pushManager.getSubscription();
  if (sub && !sameKey(sub, publicKey)) {
    await sub.unsubscribe();
    sub = null;
  }
  sub ??= await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key(publicKey) as BufferSource });
  const json = sub.toJSON() as { endpoint?: string; keys?: { p256dh?: string; auth?: string } };
  return addDevice(json.endpoint ?? sub.endpoint, json.keys?.p256dh ?? "", json.keys?.auth ?? "");
}

/** Asks (only after a tap; pass the box already opened by {@link askPermission}), subscribes this browser, tells Bolo. */
export async function enablePush(publicKey: string | null, asked?: Promise<NotificationPermission> | null): Promise<"on" | "denied" | "failed"> {
  try {
    const permission = await (asked ?? Notification.requestPermission());
    if (permission !== "granted") return "denied";
    if (!publicKey) return "failed";
    return (await subscribeAndSend(await registration(), publicKey)) ? "on" : "failed";
  } catch {
    return "failed";
  }
}

/**
 * On every app load with reminders on (review, reminders I6): sends this browser's subscription again, so one the
 * server dropped, one that moved to another learner, or one the browser rotated is right again. Remakes one made
 * with an old key. Never asks for permission.
 */
export async function resyncPush(publicKey: string): Promise<void> {
  try {
    if (!("Notification" in window) || Notification.permission !== "granted" || !("serviceWorker" in navigator)) return;
    const reg = await navigator.serviceWorker.getRegistration("/sw.js");
    if (!reg || !(await reg.pushManager.getSubscription())) return;
    await subscribeAndSend(reg, publicKey);
  } catch {
    // tried again next time Bolo opens
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
