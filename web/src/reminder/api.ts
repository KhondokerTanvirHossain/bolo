/** Daily reminders (PRG-5): /api/reminder*. */
import { api } from "../api/browser";

export type Reminder = {
  asked: boolean;
  on: boolean;
  time: string;
  timeZone: string;
  keepPrivate: boolean;
  preview: string;
  devices: number;
  publicKey: string;
};

export const zone = () => {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone;
  } catch {
    return "Asia/Dhaka";
  }
};

export async function fetchReminder(): Promise<Reminder | null> {
  const r = await api.get<Reminder>("/api/reminder");
  return r.kind === "ok" ? r.value : null;
}

export async function saveReminder(patch: Partial<Pick<Reminder, "on" | "time" | "keepPrivate">>): Promise<Reminder | null> {
  const r = await api.put<Reminder>("/api/reminder", { ...patch, timeZone: zone() });
  return r.kind === "ok" ? r.value : null;
}

export async function notNow(): Promise<void> {
  await api.post("/api/reminder/not-now", { timeZone: zone() });
}

export async function dismissCard(): Promise<void> {
  await api.post("/api/reminder/card/dismiss", {});
}

export async function addDevice(endpoint: string, p256dh: string, auth: string): Promise<boolean> {
  const r = await api.post("/api/reminder/subscriptions", { endpoint, keys: { p256dh, auth } });
  return r.kind === "ok";
}

export async function removeDevice(endpoint: string): Promise<void> {
  await api.del("/api/reminder/subscriptions", { endpoint });
}

/** Bolo opened (from a reminder when {@code reminder} is set): resets the back-off, keeps the zone current. */
export async function opened(reminder?: string): Promise<void> {
  await api.post("/api/reminder/opened", { timeZone: zone(), reminder: reminder ?? null });
}

export async function sendTestReminder(): Promise<number | null> {
  const r = await api.post<{ devices: number }>("/api/admin/reminder-test", {});
  return r.kind === "ok" ? r.value.devices : null;
}
