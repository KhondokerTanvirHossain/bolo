"use client";
import { useCallback, useEffect, useReducer, useState } from "react";
import { api, POLICY_VERSION, type LearnerView } from "@/api/browser";
import { initialState, isLastQuestion, load, onboarding, reconcile, save, type OnboardingState } from "./steps";
import { StartScreen } from "./screens/StartScreen";
import { WelcomeScreen } from "./screens/WelcomeScreen";
import { ConsentScreen } from "./screens/ConsentScreen";
import { MicScreen } from "./screens/MicScreen";
import { SetupScreen } from "./screens/SetupScreen";
import { Day1Screen } from "./screens/Day1Screen";

type Boot = { kind: "loading" } | { kind: "ready" } | { kind: "offline" };

export default function Onboarding() {
  const [state, dispatch] = useReducer(
    (s: OnboardingState, a: Parameters<typeof onboarding>[1] | { type: "replace"; state: OnboardingState }) =>
      a.type === "replace" ? a.state : onboarding(s, a),
    initialState,
  );
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [busy, setBusy] = useState(false);

  // Start or resume the guest session, then resume the stored step only if the server agrees (US-1.7).
  useEffect(() => {
    void (async () => {
      const stored = load(window.localStorage);
      const session = await api.post<LearnerView>("/api/session");
      if (session.kind !== "ok") { setBoot({ kind: "offline" }); return; }
      dispatch({ type: "replace", state: reconcile(stored, { consent: session.value.consent !== null, profile: session.value.profile !== null }) });
      setBoot({ kind: "ready" });
    })();
  }, []);

  useEffect(() => { if (boot.kind === "ready") save(window.localStorage, state); }, [state, boot]);

  const chooseConsent = useCallback(async (keep: boolean) => {
    setBusy(true);
    const res = await api.post<void>("/api/consent", { keepRecordings: keep, over13: state.over13, policyVersion: POLICY_VERSION });
    setBusy(false);
    if (res.kind === "ok") dispatch({ type: "chooseConsent", keep });
  }, [state.over13]);

  const next = useCallback(async () => {
    if (!isLastQuestion(state)) { dispatch({ type: "nextQuestion" }); return; }
    const { goal, fearedMoment, talkMinutes } = state.answers;
    if (!goal || !fearedMoment || !talkMinutes) return;
    setBusy(true);
    const res = await api.put<void>("/api/profile", { goal, fearedMoment, talkMinutes: Number(talkMinutes) });
    setBusy(false);
    if (res.kind === "ok") dispatch({ type: "profileSaved" });
  }, [state]);
  const answer = useCallback((value: string) => dispatch({ type: "answer", value }), []);

  const toConsent = useCallback(() => dispatch({ type: "go", step: "consent" }), []);

  if (boot.kind === "offline") {
    return (
      <section className="dk-screen on">
        <div className="dk-card" role="alert" style={{ position: "relative", margin: "20vh auto" }}>
          <h1 className="bo-flow__title">Bolo can&apos;t reach the internet</h1>
          <p className="bo-flow__body">Check your connection, then try again.</p>
          <button className="bo-btn bo-btn--primary bo-btn--block" onClick={() => window.location.reload()}>Try again</button>
        </div>
      </section>
    );
  }
  if (boot.kind === "loading") return null;

  switch (state.step) {
    case "start":
      return <StartScreen onMeet={() => dispatch({ type: "go", step: "welcome" })} />;
    case "welcome":
      return <WelcomeScreen onDone={toConsent} />;
    case "consent":
      return <ConsentScreen over13={state.over13} busy={busy}
        onToggleOver13={() => dispatch({ type: "toggleOver13" })} onChoose={(k) => void chooseConsent(k)} />;
    case "mic":
      return <MicScreen deviceId={state.micDeviceId} headphones={state.headphones}
        onChooseMic={(id) => dispatch({ type: "chooseMic", deviceId: id })}
        onHeadphones={(h) => dispatch({ type: "setHeadphones", headphones: h })}
        onContinue={() => dispatch({ type: "go", step: "setup" })} />;
    case "setup":
      return <SetupScreen state={state} busy={busy} onAnswer={answer} onNext={() => void next()}
        onBack={() => dispatch({ type: "prevQuestion" })} />;
    case "day1":
    case "talk":
      return <Day1Screen />;
  }
}
