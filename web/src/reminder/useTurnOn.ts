"use client";
import { useEffect, useState } from "react";
import { fetchReminder, type Reminder } from "./api";
import { browserEnv, pushState, pushSupport } from "./push";

const SHOWN = "bolo.reminder.turnon.v1";

/** ScreenReminderTurnOn: once, when Bolo runs from the Home Screen with a reminder on and this phone not signed up. */
export function useTurnOn(active: boolean): [Reminder | null, () => void] {
  const [r, setR] = useState<Reminder | null>(null);
  const [checked, setChecked] = useState(false);
  useEffect(() => {
    if (!active || checked) return;
    setChecked(true);
    const env = browserEnv();
    if (!env.standalone || pushSupport(env) !== "ok") return;
    try {
      if (window.localStorage.getItem(SHOWN)) return;
    } catch {
      return;
    }
    void (async () => {
      const reminder = await fetchReminder();
      if (!reminder?.on || (await pushState()) !== "not-here") return;
      setR(reminder);
    })();
  }, [active, checked]);
  const close = () => {
    try {
      window.localStorage.setItem(SHOWN, "1");
    } catch {
      // shown once per page load at worst
    }
    setR(null);
  };
  return [r, close];
}
