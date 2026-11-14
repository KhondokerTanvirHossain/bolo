import { describe, expect, it } from "vitest";
import { SLOTS, formatTime, icsFor, nearestSlot } from "./slots";

describe("reminder slots", () => {
  it("are the four times of the learner's day", () => {
    expect(SLOTS.map((s) => [s.name, s.time])).toEqual([
      ["Morning commute", "08:00"], ["Lunch", "13:30"], ["Evening commute", "18:30"], ["Night", "22:00"],
    ]);
  });

  it("preselect the slot nearest now", () => {
    expect(nearestSlot(new Date(2026, 10, 14, 17, 10))).toBe("evening");
    expect(nearestSlot(new Date(2026, 10, 14, 9, 0))).toBe("morning");
    expect(nearestSlot(new Date(2026, 10, 14, 23, 50))).toBe("night");
    expect(nearestSlot(new Date(2026, 10, 14, 12, 0))).toBe("lunch");
  });

  it("format times the way people say them", () => {
    expect(formatTime("08:00")).toBe("8:00 am");
    expect(formatTime("18:30")).toBe("6:30 pm");
    expect(formatTime("12:05")).toBe("12:05 pm");
    expect(formatTime("00:15")).toBe("12:15 am");
  });

  it("make a daily calendar event at the chosen time", () => {
    const ics = icsFor("18:30", new Date(2026, 10, 14, 10, 0));
    expect(ics).toContain("BEGIN:VCALENDAR");
    expect(ics).toContain("RRULE:FREQ=DAILY");
    expect(ics).toContain("DTSTART:20261115T183000");
    expect(ics).toContain("SUMMARY:Talk with Bolo");
    expect(ics).toContain("URL:https://bolo.eliooo.org/");
  });
});
