import { Icon, Scene } from "@/scene/Scene";

/** Where this build ends: the three setup questions arrive in the next build (P0-1 scope). */
export function SetupNextScreen({ onBack }: { onBack: () => void }) {
  return (
    <section className="dk-screen on" data-screen="setup">
      <Scene />
      <div className="dk-dim" />
      <div className="dk-card" role="dialog" aria-modal="true" aria-labelledby="setup-title">
        <div className="dk-steps"><span>Getting to know you</span><span>3 of 5</span></div>
        <div className="bo-hero-icon bo-hero-icon--coach" style={{ width: 64, height: 64, marginBottom: 18 }}>
          <Icon name="construction" />
        </div>
        <h1 className="bo-flow__title" id="setup-title">Next: three quick questions</h1>
        <p className="bo-flow__body">Your consent and microphone are saved. The setup questions arrive in the next build.</p>
        <button className="bo-btn bo-btn--secondary bo-btn--block" style={{ marginTop: 20 }} onClick={onBack}>
          Back to the microphone
        </button>
      </div>
    </section>
  );
}
