"use client";
import { useEffect, useRef, useState } from "react";

export type MicStatus = "starting" | "live" | "blocked" | "no-device";

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

/** Opens the mic with Bolo's capture settings (echo cancellation, noise suppression, auto gain) and meters it. */
export function useMicLevel(deviceId: string | null): MicLevel {
  const [status, setStatus] = useState<MicStatus>("starting");
  const [devices, setDevices] = useState<MediaDeviceInfo[]>([]);
  const [activeDeviceId, setActive] = useState<string | null>(null);
  const [levelDb, setLevel] = useState(-100);
  const [history, setHistory] = useState<number[]>([]);
  const [attempt, setAttempt] = useState(0);
  const historyRef = useRef<number[]>([]);

  useEffect(() => {
    let stream: MediaStream | null = null;
    let ctx: AudioContext | null = null;
    let timer: ReturnType<typeof setInterval> | null = null;
    let cancelled = false;
    let removeResume = () => {};

    async function open() {
      setStatus("starting");
      try {
        stream = await navigator.mediaDevices.getUserMedia({
          audio: {
            deviceId: deviceId ? { exact: deviceId } : undefined,
            echoCancellation: true,
            noiseSuppression: true,
            autoGainControl: true,
          },
        });
      } catch (e) {
        if (cancelled) return;
        const name = e instanceof DOMException ? e.name : "";
        setStatus(name === "NotFoundError" || name === "OverconstrainedError" ? "no-device" : "blocked");
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

  // US-1.3: when the learner unblocks the mic in the browser, continue with no extra click.
  useEffect(() => {
    if (status !== "blocked" || !navigator.permissions) return;
    let perm: PermissionStatus | null = null;
    const onChange = () => { if (perm?.state === "granted") setAttempt((a) => a + 1); };
    navigator.permissions.query({ name: "microphone" as PermissionName })
      .then((p) => { perm = p; p.addEventListener("change", onChange); })
      .catch(() => {});
    return () => perm?.removeEventListener("change", onChange);
  }, [status]);

  return { status, devices, activeDeviceId, levelDb, history, retry: () => setAttempt((a) => a + 1) };
}
