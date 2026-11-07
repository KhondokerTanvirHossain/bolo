"use client";
import { useCallback, useEffect, useReducer, useRef, useState } from "react";
import { api, POLICY_VERSION, type ApiResult, type LearnerView } from "@/api/browser";
import { initialState, isLastQuestion, load, onboarding, reconcile, save, type OnboardingState } from "./steps";
import { StartScreen } from "./screens/StartScreen";
import { WelcomeScreen } from "./screens/WelcomeScreen";
import { ConsentScreen } from "./screens/ConsentScreen";
import { MicScreen } from "./screens/MicScreen";
import { SetupScreen } from "./screens/SetupScreen";
import { Day1Screen } from "./screens/Day1Screen";
import { FirstTalkScreen } from "./screens/FirstTalkScreen";

type Boot = { kind: "loading" } | { kind: "ready" } | { kind: "offline" };

export default function Onboarding() {
  const [state, dispatch] = useReducer(
    (s: OnboardingState, a: Parameters<typeof onboarding>[1] | { type: "replace"; state: OnboardingState }) =>
      a.type === "replace" ? a.state : onboarding(s, a),
    initialState,
  );
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);
  const hasSession = useRef(false);

  // Resume the stored step only if the server agrees (US-1.7). Reading /api/me creates nothing: a guest is created
  // only when the learner actually gives consent, so page views alone never add rows.
  useEffect(() => {
    void (async () => {
      const stored = load(window.localStorage);
      const me = await api.get<LearnerView>("/api/me");
      if (me.kind === "offline" || me.kind === "refused") { setBoot({ kind: "offline" }); return; }
      const server = me.kind === "ok"
        ? { consent: me.value.consent !== null, profile: me.value.profile !== null }
        : { consent: false, profile: false };
      hasSession.current = me.kind === "ok";
      dispatch({ type: "replace", state: reconcile(stored, server) });
      setBoot({ kind: "ready" });
    })();
  }, []);

  /** A write that needs a session: starts one first if there is none, and once more if it expired meanwhile. */
  const withSession = useCallback(async (write: () => Promise<ApiResult<void>>): Promise<boolean> => {
    const start = async () => {
      const started = await api.post<LearnerView>("/api/session");
      hasSession.current = started.kind === "ok";
      return hasSession.current;
    };
    if (!hasSession.current && !(await start())) return false;
    let res = await write();
    if (res.kind === "signed-out" && (await start())) res = await write();
    return res.kind === "ok";
  }, []);

  useEffect(() => { if (boot.kind === "ready") save(window.localStorage, state); }, [state, boot]);

  const chooseConsent = useCallback(async (keep: boolean) => {
    setBusy(true);
    setFailed(false);
    const ok = await withSession(() =>
      api.post<void>("/api/consent", { keepRecordings: keep, over13: state.over13, policyVersion: POLICY_VERSION }));
    setBusy(false);
    if (ok) dispatch({ type: "chooseConsent", keep }); else setFailed(true);
  }, [state.over13, withSession]);

  const next = useCallback(async () => {
    if (!isLastQuestion(state)) { dispatch({ type: "nextQuestion" }); return; }
    const { goal, fearedMoment, talkMinutes } = state.answers;
    if (!goal || !fearedMoment || !talkMinutes) return;
    setBusy(true);
    setFailed(false);
    const ok = await withSession(() =>
      api.put<void>("/api/profile", { goal, fearedMoment, talkMinutes: Number(talkMinutes) }));
    setBusy(false);
    if (ok) dispatch({ type: "profileSaved" }); else setFailed(true);
  }, [state, withSession]);
  const answer = useCallback((value: string) => dispatch({ type: "answer", value }), []);

  const toConsent = useCallback(() => dispatch({ type: "go", step: "consent" }), []);
  const forgetMic = useCallback(() => dispatch({ type: "forgetMic" }), []);
  const toTalk = useCallback(() => dispatch({ type: "go", step: "talk" }), []);

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
      return <ConsentScreen over13={state.over13} busy={busy} failed={failed}
        onToggleOver13={() => dispatch({ type: "toggleOver13" })} onChoose={(k) => void chooseConsent(k)} />;
    case "mic":
      return <MicScreen deviceId={state.micDeviceId} headphones={state.headphones}
        onChooseMic={(id) => dispatch({ type: "chooseMic", deviceId: id })}
        onForgetMic={forgetMic}
        onHeadphones={(h) => dispatch({ type: "setHeadphones", headphones: h })}
        onContinue={(verdict) => { dispatch({ type: "micChecked", result: verdict }); dispatch({ type: "go", step: "setup" }); }} />;
    case "setup":
      return <SetupScreen state={state} busy={busy} failed={failed} onAnswer={answer} onNext={() => void next()}
        onBack={() => dispatch({ type: "prevQuestion" })} />;
    case "day1":
      return <Day1Screen deviceId={state.micDeviceId} onSaved={toTalk} />;
    case "talk":
      return <FirstTalkScreen />;
  }
}
