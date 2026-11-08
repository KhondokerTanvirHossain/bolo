import { Icon } from "@/scene/Scene";

export type KeepRow = { icon: string; title: string; sub: string; tone?: "voice" | "coach" | "buddy" };

/** What the learner keeps by signing in (KeepList): at most three rows, concrete sub lines. */
export function KeepList({ rows }: { rows: KeepRow[] }) {
  return (
    <div className="bo-keep" style={{ marginTop: 16 }}>
      {rows.slice(0, 3).map((r) => (
        <div className="bo-keep__row" key={r.title}>
          <span className={`bo-keep__icon${r.tone === "coach" ? " bo-keep__icon--coach" : r.tone === "buddy" ? " bo-keep__icon--buddy" : ""}`}>
            <Icon name={r.icon} size="sm" />
          </span>
          <span><span className="bo-keep__title" style={{ display: "block" }}>{r.title}</span><span className="bo-keep__sub">{r.sub}</span></span>
        </div>
      ))}
    </div>
  );
}
