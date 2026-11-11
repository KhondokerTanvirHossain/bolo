"use client";
import { useCallback, useEffect, useReducer, useRef, useState } from "react";
import { api, POLICY_VERSION, type ApiResult, type LearnerView } from "@/api/browser";
import { toSaveFailure, type SaveFailure } from "./saveFailure";
import { firstWordSpoken, flushEvents, markFirstOpen, track } from "@/analytics/track";
import { currentInvite, takeInviteFromUrl } from "@/account/invite";
import { takeRefFromUrl } from "@/referral/referral";
import { fetchAuthConfig, restoreDay1, signInWithGoogle } from "@/account/session";
import { signInProblem } from "@/account/signInCopy";
import { initialState, isLastQuestion, load, onboarding, reconcile, save, STORAGE_KEY, type OnboardingState } from "./steps";
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
  const [failure, setFailure] = useState<SaveFailure>(null);
  const hasSession = useRef(false);
  const [signInOn, setSignInOn] = useState(false);
  const [closedBeta, setClosedBeta] = useState(false);
  const [restoring, setRestoring] = useState(false);
  const [restoreProblem, setRestoreProblem] = useState<string | null>(null);

  // Resume the stored step only if the server agrees (US-1.7). Reading /api/me creates nothing: a guest is created
  // only when the learner actually gives consent, so page views alone never add rows.
  useEffect(() => {
    takeInviteFromUrl();
    takeRefFromUrl();
    void fetchAuthConfig().then((c) => setSignInOn(c.enabled));
    // A browser with onboarding progress already stored used Bolo before tracking began.
    markFirstOpen(Date.now(), window.localStorage.getItem(STORAGE_KEY) !== null);
    void (async () => {
      const stored = load(window.localStorage);
      const me = await api.get<LearnerView>("/api/me");
      if (me.kind === "offline" || me.kind === "refused") { setBoot({ kind: "offline" }); return; }
      const server = me.kind === "ok"
        ? { consent: me.value.consent !== null, profile: me.value.profile !== null }
        : { consent: false, profile: false };
      hasSession.current = me.kind === "ok";
      if (hasSession.current) void flushEvents();
      const next = reconcile(stored, server);
      // Jumped forward to the app (set up elsewhere): bring the Day 1 clip back first, as "I already have an account" does.
      if (next.step === "talk" && stored.step !== "talk") await restoreDay1();
      dispatch({ type: "replace", state: next });
      setBoot({ kind: "ready" });
    })();
  }, []);

  /** The consent write is the one that may create the guest: start a session first if there is none, and once more
   *  if it expired meanwhile. (Later writes never create a session: see the profile save.) */
  const withSession = useCallback(async (write: () => Promise<ApiResult<void>>): Promise<ApiResult<void>> => {
    const start = async (): Promise<ApiResult<unknown>> => {
      const started = await api.post<LearnerView>("/api/session", { invite: currentInvite() });
      if (started.kind === "refused" && started.code === "invite") setClosedBeta(true);
      hasSession.current = started.kind === "ok";
      return started;
    };
    if (!hasSession.current) {
      const started = await start();
      if (started.kind !== "ok") return started as ApiResult<void>;
    }
    let res = await write();
    if (res.kind === "signed-out" && (await start()).kind === "ok") res = await write();
    return res;
  }, []);

  useEffect(() => { if (boot.kind === "ready") save(window.localStorage, state); }, [state, boot]);

  // §10 funnel: each onboarding step the learner leaves, and how long it took (no answers, only the step's name).
  const stepStart = useRef<{ step: string; at: number } | null>(null);
  useEffect(() => {
    if (boot.kind !== "ready") return;
    const prev = stepStart.current;
    if (prev && prev.step !== state.step) {
      void track("onboarding_step_completed", { step: prev.step, duration_s: Math.round((Date.now() - prev.at) / 1000) });
    }
    if (!prev || prev.step !== state.step) stepStart.current = { step: state.step, at: Date.now() };
  }, [state.step, boot]);

  const chooseConsent = useCallback(async (keep: boolean) => {
    setBusy(true);
    setFailure(null);
    const res = await withSession(() =>
      api.post<void>("/api/consent", { keepRecordings: keep, over13: state.over13, policyVersion: POLICY_VERSION }));
    setBusy(false);
    if (res.kind === "ok") { dispatch({ type: "chooseConsent", keep }); void flushEvents(); } else setFailure(toSaveFailure(res));
  }, [state.over13, withSession]);

  const next = useCallback(async () => {
    if (!isLastQuestion(state)) { dispatch({ type: "nextQuestion" }); return; }
    const { goal, fearedMoment, talkMinutes } = state.answers;
    if (!goal || !fearedMoment || !talkMinutes) return;
    setBusy(true);
    setFailure(null);
    const res = await api.put<void>("/api/profile", { goal, fearedMoment, talkMinutes: Number(talkMinutes) });
    setBusy(false);
    if (res.kind === "ok") { dispatch({ type: "profileSaved" }); return; }
    if (res.kind === "signed-out") {
      // The session (and with it the consent) is gone: never create a new guest here, ask for consent again.
      hasSession.current = false;
      dispatch({ type: "replace", state: reconcile(state, { consent: false, profile: false }) });
      return;
    }
    setFailure(toSaveFailure(res));
  }, [state]);
  const answer = useCallback((value: string) => dispatch({ type: "answer", value }), []);

  const toConsent = useCallback(() => dispatch({ type: "go", step: "consent" }), []);

  /** US-8.4 / ACC-7: "I already have an account" restores goals, history and the Day 1 clip and skips onboarding. */
  const restore = useCallback(async () => {
    setRestoring(true);
    setRestoreProblem(null);
    const r = await signInWithGoogle();
    setRestoring(false);
    if (r.kind !== "ok") {
      if (r.kind === "invite") setClosedBeta(true);
      else setRestoreProblem(signInProblem(r));
      return;
    }
    hasSession.current = true;
    void flushEvents();
    if (r.learner.consent && r.learner.profile) {
      await restoreDay1();
      dispatch({ type: "go", step: "talk" });
    } else {
      // A Google account with no setup yet: carry on from the consent step.
      dispatch({ type: "go", step: "consent" });
    }
  }, []);
  const forgetMic = useCallback(() => dispatch({ type: "forgetMic" }), []);
  // Stable: Day 1 restarts its "saved" timer whenever this changes.
  const day1Saved = useCallback(() => { void firstWordSpoken(); dispatch({ type: "go", step: "talk" }); }, []);

  if (boot.kind === "offline") {
    return (
      <section className="dk-screen on">
        <div className="dk-card" role="alert">
          <h1 className="bo-flow__title">Bolo can&apos;t reach the internet</h1>
          <p className="bo-flow__body">Check your connection, then try again.</p>
          <button className="bo-btn bo-btn--primary bo-btn--block" onClick={() => window.location.reload()}>Try again</button>
        </div>
      </section>
    );
  }
  if (boot.kind === "loading") return null;
  if (closedBeta) {
    return (
      <section className="dk-screen on" data-screen="closed">
        <div className="dk-card" role="alert">
          <h1 className="bo-flow__title">Bolo is in a closed beta</h1>
          <p className="bo-flow__body">It&apos;s invite-only for now. Ask the person who told you about Bolo for an invite link, then open it here.</p>
          {signInOn && (
            <button className="bo-btn bo-btn--secondary bo-btn--block" style={{ marginTop: 16 }} onClick={() => { setClosedBeta(false); void restore(); }}
              disabled={restoring}>I already have an account</button>
          )}
          {restoreProblem && <p className="bo-footnote" role="alert" style={{ marginTop: 12 }}>{restoreProblem}</p>}
        </div>
      </section>
    );
  }

  switch (state.step) {
    case "start":
      return <StartScreen onMeet={() => dispatch({ type: "go", step: "welcome" })} problem={restoreProblem}
        onRestore={signInOn ? () => void restore() : undefined} restoreBusy={restoring} />;
    case "welcome":
      return <WelcomeScreen onDone={toConsent} />;
    case "consent":
      return <ConsentScreen over13={state.over13} busy={busy} failure={failure}
        onToggleOver13={() => dispatch({ type: "toggleOver13" })} onChoose={(k) => void chooseConsent(k)} />;
    case "mic":
      return <MicScreen deviceId={state.micDeviceId} headphones={state.headphones}
        onChooseMic={(id) => dispatch({ type: "chooseMic", deviceId: id })}
        onForgetMic={forgetMic}
        onHeadphones={(h) => dispatch({ type: "setHeadphones", headphones: h })}
        onContinue={(verdict) => { dispatch({ type: "micChecked", result: verdict }); dispatch({ type: "go", step: "setup" }); }} />;
    case "setup":
      return <SetupScreen state={state} busy={busy} failure={failure} onAnswer={answer} onNext={() => void next()}
        onBack={() => dispatch({ type: "prevQuestion" })} />;
    case "day1":
      return <Day1Screen deviceId={state.micDeviceId} onSaved={day1Saved} />;
    case "talk":
      return <FirstTalkScreen deviceId={state.micDeviceId} />;
  }
}
