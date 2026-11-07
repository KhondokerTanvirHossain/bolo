import { openMic } from "./openMic";
import { createDownsampler, downsampleTo16k } from "@/talk/pcm";
import { toWav } from "./wav";

/** The frames up to {@code max} samples; the stop timer can fire late, and the server refuses anything past 15 s. */
export function trimToSamples(frames: Int16Array[], max: number): Int16Array[] {
  const out: Int16Array[] = [];
  let n = 0;
  for (const f of frames) {
    if (n >= max) break;
    const take = Math.min(f.length, max - n);
    out.push(take === f.length ? f : f.subarray(0, take));
    n += take;
  }
  return out;
}

export type PcmRecording = { stop(): Promise<Blob>; cancel(): void };

/**
 * Records the mic as 16 kHz mono PCM (the talk's worklet path) and hands back a WAV, for "Try it" and practice.
 * Stops by itself after {@code maxMs}; {@code onAutoStop} then gets the WAV.
 */
export async function startPcmRecording(deviceId: string | null, maxMs: number, onAutoStop?: (wav: Blob) => void): Promise<PcmRecording> {
  const ctx = new AudioContext();
  void ctx.resume().catch(() => {});
  let stream: MediaStream | null = null;
  let source: MediaStreamAudioSourceNode;
  let node: AudioWorkletNode;
  try {
    stream = (await openMic(deviceId)).stream;
    await ctx.audioWorklet.addModule("/worklets/pcm-capture.js");
    source = ctx.createMediaStreamSource(stream);
    node = new AudioWorkletNode(ctx, "pcm-capture");
  } catch (e) {
    // Release the mic too, or the browser keeps its light on.
    stream?.getTracks().forEach((t) => t.stop());
    void ctx.close().catch(() => {});
    throw e;
  }
  const mic = stream;
  const mute = ctx.createGain();
  mute.gain.value = 0;
  source.connect(node);
  node.connect(mute);
  mute.connect(ctx.destination);
  const down = createDownsampler(ctx.sampleRate);
  const frames: Int16Array[] = [];
  const wavOf = () => new Blob([toWav(trimToSamples(frames, (maxMs * 16000) / 1000), 16000)], { type: "audio/wav" });
  node.port.onmessage = (ev: MessageEvent<Float32Array>) => { frames.push(...downsampleTo16k(ev.data, down)); };

  let done = false;
  const finish = (): Blob | null => {
    if (done) return null;
    done = true;
    clearTimeout(timer);
    node.port.close();
    node.disconnect();
    mic.getTracks().forEach((t) => t.stop());
    void ctx.close().catch(() => {});
    return wavOf();
  };
  const timer = setTimeout(() => { const wav = finish(); if (wav) onAutoStop?.(wav); }, maxMs);
  return {
    stop: async () => finish() ?? wavOf(),
    cancel: () => { finish(); },
  };
}
