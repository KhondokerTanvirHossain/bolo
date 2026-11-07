import { HILLTOP_SCENE_SVG } from "./art";

/** The hilltop at dusk, drawn wide; the important parts sit in the middle (design system, Presence). */
export function Scene() {
  return <div className="bo-scene" style={{ position: "absolute", inset: 0 }} dangerouslySetInnerHTML={{ __html: HILLTOP_SCENE_SVG }} />;
}

export function Icon({ name, fill = false, size }: { name: string; fill?: boolean; size?: "sm" | "lg" }) {
  const cls = ["bo-icon", fill ? "bo-icon--fill" : "", size ? `bo-icon--${size}` : ""].filter(Boolean).join(" ");
  return <span className={cls} aria-hidden="true">{name}</span>;
}
