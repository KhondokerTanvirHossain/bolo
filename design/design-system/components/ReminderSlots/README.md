# ReminderSlots

The reminder-time choice (PRG-5, US-6.5): four slots named after the learner's day, in a 2×2 grid, plus **Pick an exact time**.

**Needs:** the four slots with their default times, and which one is selected.

**Use**
- Slots are named by routine, not by clock: Morning commute (8:00 am), Lunch (1:30 pm), Evening commute (6:30 pm), Night (10:00 pm). Commutes come first because the bus is where Bolo is used (persona Rafi).
- It is a radio group: exactly one is selected. Selected is `sun-soft` with a `sun-ink` border and filled icon, like a pressed AnswerChip.
- **Pick an exact time** opens Android's time picker; the chosen time then replaces the selected slot's time.
- The same component appears in Me → Settings → Reminder, with a toggle above it to turn reminders off.

**Behaviour:** selection springs the border in (`spring-toggle`) with a light haptic tick.
