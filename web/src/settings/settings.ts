import { api, type LearnerView } from "@/api/browser";

export type Settings = { buddySpeed: "normal" | "slower"; captions: boolean };
export const DEFAULT_SETTINGS: Settings = { buddySpeed: "normal", captions: true };
export const CAPTIONS_KEY = "bolo.captions";

export async function fetchSettings(): Promise<Settings> {
  const me = await api.get<LearnerView & { settings?: Settings }>("/api/me");
  return me.kind === "ok" && me.value.settings ? me.value.settings : DEFAULT_SETTINGS;
}

/** SET-1/SET-2: saved for the next talk; captions are also cached for the talk screen on this device. */
export async function saveSettings(s: Settings): Promise<boolean> {
  try {
    window.localStorage.setItem(CAPTIONS_KEY, s.captions ? "on" : "off");
  } catch {
    // per-device cache only
  }
  return (await api.put<void>("/api/me/settings", s)).kind === "ok";
}

/** US-2.7: "keep Bolo slower" keeps the captions choice as it is. */
export async function keepSlower(): Promise<boolean> {
  return saveSettings({ ...(await fetchSettings()), buddySpeed: "slower" });
}
