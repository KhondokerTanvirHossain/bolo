"use client";
import { useEffect } from "react";
import { fetchReminder, opened } from "./api";
import { resyncPush } from "./push";

/**
 * Bolo opened: tell the server once per page load (it clears the back-off, PRG-6, and keeps the time zone current).
 * Opened from a reminder ("/?from=reminder&r=<id>"), it says which one, then the address goes back to "/".
 */
export function useReminderOpen() {
  useEffect(() => {
    const url = new URL(window.location.href);
    const fromReminder = url.searchParams.get("from") === "reminder";
    const id = fromReminder ? url.searchParams.get("r") ?? undefined : undefined;
    void opened(id);
    // Reminders on: this browser's subscription is sent again (guests and signed-out pages get no reminder back).
    void fetchReminder().then((r) => { if (r?.on && r.publicKey) void resyncPush(r.publicKey); });
    if (fromReminder) window.history.replaceState(null, "", "/");
  }, []);
}
