"use client";
import { formatTime } from "./slots";

/** NotificationPreview (DS v43): the reminder exactly as it will arrive. */
export function Preview({ time, text }: { time: string; text: string }) {
  return (
    <div className="bo-notif" aria-label="Example reminder">
      <span className="bo-notif__app" aria-hidden="true">B</span>
      <div><div className="bo-notif__meta">Bolo · {formatTime(time)}</div><p className="bo-notif__text">{text}</p></div>
    </div>
  );
}
