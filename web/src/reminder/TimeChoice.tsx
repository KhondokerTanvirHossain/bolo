"use client";
import { Icon } from "@/scene/Scene";
import { SLOTS } from "./slots";

/** ReminderSlots (DS v43): four times of the learner's day in a radio group, and Pick an exact time. */
export function TimeChoice({ time, desk, onChange }: { time: string; desk?: boolean; onChange: (time: string) => void }) {
  return (
    <>
      <div className="bo-slots" role="radiogroup" aria-label="Reminder time">
        {SLOTS.map((s) => {
          const on = s.time === time;
          return (
            <button key={s.id} type="button" className="bo-slot" role="radio" aria-checked={on} onClick={() => onChange(s.time)}>
              <Icon name={s.icon} fill={on} />
              <span className="bo-slot__name">{desk ? s.desk : s.name}</span>
              <span className="bo-slot__time">{formatSlot(s.time)}</span>
            </button>
          );
        })}
      </div>
      <label className="bo-exact">
        <Icon name="schedule" />Pick an exact time
        <input className="bo-exact__input" type="time" value={time} onChange={(e) => e.target.value && onChange(e.target.value)} />
      </label>
    </>
  );
}

function formatSlot(t: string) {
  const h = Number(t.slice(0, 2));
  return `${h % 12 === 0 ? 12 : h % 12}:${t.slice(3)} ${h < 12 ? "am" : "pm"}`;
}
