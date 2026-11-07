import { Icon, Scene } from "@/scene/Scene";
import { WORDMARK_SVG } from "@/scene/art";

/** First visit: one click before any sound, because browsers block autoplay (design system, Voice in a browser). */
export function StartScreen({ onMeet }: { onMeet: () => void }) {
  return (
    <section className="dk-screen on" data-screen="start">
      <Scene />
      <div className="dk-dim" style={{ background: "rgba(20,12,38,.35)", backdropFilter: "none" }} />
      <div style={{ position: "absolute", left: "50%", top: "50%", transform: "translate(-50%,-58%)", zIndex: 4,
        textAlign: "center", color: "var(--on-scene)", width: "min(620px, calc(100vw - 32px))" }}>
        <div style={{ display: "flex", justifyContent: "center" }} dangerouslySetInnerHTML={{ __html: WORDMARK_SVG }} />
        <p style={{ font: "600 30px/38px var(--font-sans)", margin: "22px 0 8px", letterSpacing: "-.01em" }}>
          Talk first. Get better after.
        </p>
        <p style={{ font: "500 17px/26px var(--font-sans)", color: "var(--on-scene-secondary)", margin: "0 0 28px" }}>
          Practise speaking English with a friend who never judges. Bolo talks out loud, so turn your sound on.
        </p>
        <button className="bo-btn bo-btn--primary" style={{ minWidth: 240, height: 60, fontSize: 19 }} onClick={onMeet} autoFocus>
          <Icon name="volume_up" fill />Meet Bolo
        </button>
      </div>
    </section>
  );
}
