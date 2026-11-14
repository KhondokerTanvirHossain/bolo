/** The reminder time choice (ReminderSlots, DS v43): four times of the learner's day, or an exact time. */
export type SlotId = "morning" | "lunch" | "evening" | "night";

export type Slot = { id: SlotId; name: string; desk: string; time: string; icon: string };

export const SLOTS: Slot[] = [
  { id: "morning", name: "Morning commute", desk: "Morning", time: "08:00", icon: "wb_twilight" },
  { id: "lunch", name: "Lunch", desk: "Lunch", time: "13:30", icon: "lunch_dining" },
  { id: "evening", name: "Evening commute", desk: "Evening", time: "18:30", icon: "directions_bus" },
  { id: "night", name: "Night", desk: "Night", time: "22:00", icon: "bedtime" },
];

const minutes = (hhmm: string) => Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(3, 5));

/** The slot nearest this moment (on a 24-hour circle), preselected on the reminder step. */
export function nearestSlot(now: Date): SlotId {
  const m = now.getHours() * 60 + now.getMinutes();
  const gap = (s: Slot) => {
    const d = Math.abs(minutes(s.time) - m);
    return Math.min(d, 24 * 60 - d);
  };
  return SLOTS.reduce((best, s) => (gap(s) < gap(best) ? s : best)).id;
}

export function slotOf(time: string): SlotId | null {
  return SLOTS.find((s) => s.time === time)?.id ?? null;
}

/** "18:30" → "6:30 pm". */
export function formatTime(hhmm: string): string {
  const h = Number(hhmm.slice(0, 2));
  const m = hhmm.slice(3, 5);
  return `${h % 12 === 0 ? 12 : h % 12}:${m} ${h < 12 ? "am" : "pm"}`;
}

const pad = (n: number) => String(n).padStart(2, "0");

/** A daily calendar event at the chosen time, from tomorrow, in the device's own time ("Add to my calendar instead"). */
export function icsFor(time: string, now: Date = new Date()): string {
  const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
  const day = `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}`;
  const stamp = `${now.getUTCFullYear()}${pad(now.getUTCMonth() + 1)}${pad(now.getUTCDate())}T${pad(now.getUTCHours())}${pad(now.getUTCMinutes())}00Z`;
  return [
    "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Bolo//Daily talk//EN", "BEGIN:VEVENT",
    `UID:bolo-daily-${day}@bolo.eliooo.org`, `DTSTAMP:${stamp}`,
    `DTSTART:${day}T${time.replace(":", "")}00`, "DURATION:PT10M", "RRULE:FREQ=DAILY",
    "SUMMARY:Talk with Bolo", "URL:https://bolo.eliooo.org/", "END:VEVENT", "END:VCALENDAR", "",
  ].join("\r\n");
}
