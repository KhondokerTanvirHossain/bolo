"use client";
import { useEffect, useRef, useState } from "react";
import { classifyMicError } from "./micErrors";
import { errorName, openMic } from "./openMic";

export type MicStatus = "starting" | "live" | "blocked" | "busy" | "no-device";

export type MicLevel = {
  status: MicStatus;
  devices: MediaDeviceInfo[];
  /** The device actually in use, for the picker. */
  activeDeviceId: string | null;
  /** Latest RMS level in dBFS. */
  levelDb: number;
  /** Last ~5 s of levels for the mic check. */
  history: number[];
  retry: () => void;
};

const SAMPLE_MS = 100;
const HISTORY = 50;

/** Opens the mic with Bolo's capture settings (echo cancellation, noise suppression, auto gain) and meters it.
 *  {@code onDeviceGone} fires when a remembered device is no longer there and the default mic was used instead. */
export function useMicLevel(deviceId: string | null, onDeviceGone: () => void): MicLevel {
  const [status, setStatus] = useState<MicStatus>("starting");
  const [devices, setDevices] = useState<MediaDeviceInfo[]>([]);
  const [activeDeviceId, setActive] = useState<string | null>(null);
  const [levelDb, setLevel] = useState(-100);
  const [history, setHistory] = useState<number[]>([]);
  const [attempt, setAttempt] = useState(0);
  const historyRef = useRef<number[]>([]);
  const goneRef = useRef(onDeviceGone);
  goneRef.current = onDeviceGone;

  useEffect(() => {
    let stream: MediaStream | null = null;
    let ctx: AudioContext | null = null;
    let timer: ReturnType<typeof setInterval> | null = null;
    let cancelled = false;
    let removeResume = () => {};

    async function open() {
      setStatus("starting");
      try {
        const opened = await openMic(deviceId);
        stream = opened.stream;
        if (opened.deviceGone) goneRef.current();
      } catch (e) {
        if (cancelled) return;
        setStatus(classifyMicError(errorName(e)));
        return;
      }
      if (cancelled) { stream.getTracks().forEach((t) => t.stop()); return; }
      const track = stream.getAudioTracks()[0];
      setActive(track?.getSettings().deviceId ?? null);
      // Never block the meter on the device list: some browsers are slow to enumerate.
      setDevices(track ? [{ deviceId: track.getSettings().deviceId ?? "", label: track.label, kind: "audioinput" } as MediaDeviceInfo] : []);
      void navigator.mediaDevices.enumerateDevices()
        .then((all) => { if (!cancelled) setDevices(all.filter((d) => d.kind === "audioinput")); })
        .catch(() => {});

      ctx = new AudioContext();
      // Autoplay rules can start the context suspended (e.g. after a reload with no click yet); a suspended
      // context feeds the analyser silence. Resume now, and again on the learner's next click or key.
      const audio = ctx;
      const resume = () => { if (audio.state === "suspended") void audio.resume().catch(() => {}); };
      resume();
      window.addEventListener("pointerdown", resume);
      window.addEventListener("keydown", resume);
      removeResume = () => {
        window.removeEventListener("pointerdown", resume);
        window.removeEventListener("keydown", resume);
      };
      const analyser = ctx.createAnalyser();
      analyser.fftSize = 1024;
      ctx.createMediaStreamSource(stream).connect(analyser);
      const buf = new Float32Array(analyser.fftSize);
      historyRef.current = [];
      timer = setInterval(() => {
        analyser.getFloatTimeDomainData(buf);
        let sum = 0;
        for (const v of buf) sum += v * v;
        const db = 20 * Math.log10(Math.sqrt(sum / buf.length) || 1e-6);
        historyRef.current = [...historyRef.current, db].slice(-HISTORY);
        setLevel(db);
        setHistory(historyRef.current);
      }, SAMPLE_MS);
      setStatus("live");
    }

    open().catch(() => setStatus("blocked"));
    return () => {
      cancelled = true;
      removeResume();
      if (timer) clearInterval(timer);
      stream?.getTracks().forEach((t) => t.stop());
      void ctx?.close();
    };
  }, [deviceId, attempt]);

  // US-1.3: once the learner fixes it (unblocks the mic, or closes the app holding it), carry on with no extra
  // click: on a permission change, and as a fallback whenever the tab regains focus.
  useEffect(() => {
    if (status !== "blocked" && status !== "busy") return;
    let perm: PermissionStatus | null = null;
    let disposed = false;
    const retry = () => setAttempt((a) => a + 1);
    const onChange = () => { if (perm?.state === "granted") retry(); };
    const onVisible = () => { if (document.visibilityState === "visible") retry(); };
    navigator.permissions?.query({ name: "microphone" as PermissionName })
      .then((p) => { if (disposed) return; perm = p; p.addEventListener("change", onChange); })
      .catch(() => {});
    window.addEventListener("focus", retry);
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      disposed = true;
      perm?.removeEventListener("change", onChange);
      window.removeEventListener("focus", retry);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [status]);

  return { status, devices, activeDeviceId, levelDb, history, retry: () => setAttempt((a) => a + 1) };
}
