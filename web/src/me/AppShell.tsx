"use client";
import type { ReactNode } from "react";
import { Icon } from "@/scene/Scene";
import { WORDMARK_SMALL_SVG } from "@/scene/art";

/** The day wordmark (prototype wm-day): the same mark, letters in ink instead of the night scene's near-white. */
const DAY_WORDMARK = WORDMARK_SMALL_SVG.replaceAll("#FBF5FF", "#1D1236");

export type Tab = "talk" | "progress" | "me";

/** The desktop app shell (prototype dk-app): a sidebar with Talk, Progress and Me (NAV), then the page. */
/** {@code foot}: the sidebar's cards (free plan, Bolo on your phone); hidden on phones. */
export function AppShell({ tab, onTab, children, foot }: { tab: Tab; onTab: (t: Tab) => void; children: ReactNode; foot?: ReactNode }) {
  const nav = (t: Tab, icon: string, label: string) => (
    <button className="dk-nav" aria-current={tab === t ? "page" : undefined} onClick={() => onTab(t)}>
      <Icon name={icon} />{label}
    </button>
  );
  return (
    <section className="dk-screen on" data-screen={tab}>
      <div className="dk-app">
        <aside className="dk-side" aria-label="Bolo">
          <div className="dk-logo" dangerouslySetInnerHTML={{ __html: DAY_WORDMARK }} />
          {nav("talk", "graphic_eq", "Talk")}
          {nav("progress", "insights", "Progress")}
          {nav("me", "person", "Me")}
          {foot && <div className="dk-side__foot">{foot}</div>}
        </aside>
        <main className="dk-main"><div className="dk-page">{children}</div></main>
      </div>
    </section>
  );
}
