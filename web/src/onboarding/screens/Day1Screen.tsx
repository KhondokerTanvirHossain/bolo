import { Scene } from "@/scene/Scene";

/** Placeholder until the recorder lands (P1a-1 Task 3). */
export function Day1Screen() {
  return (
    <section className="dk-screen on" data-screen="day1">
      <Scene />
      <div className="dk-dim" />
      <div className="dk-card" role="dialog" aria-modal="true" aria-labelledby="day1-title">
        <div className="dk-steps"><span>Your Day 1 clip</span><span>Last step</span></div>
        <h1 className="bo-flow__title" id="day1-title">Introduce yourself, like at a new job</h1>
        <p className="bo-flow__body">30 seconds. We&apos;ll play it back to you on Day 7.</p>
      </div>
    </section>
  );
}
