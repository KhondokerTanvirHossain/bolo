import { openMic } from "./openMic";
import { createDownsampler, downsampleTo16k } from "@/talk/pcm";
import { toWav } from "./wav";

export type PcmRecording = { stop(): Promise<Blob>; cancel(): void };

/**
 * Records the mic as 16 kHz mono PCM (the talk's worklet path) and hands back a WAV, for "Try it" and practice.
 * Stops by itself after {@code maxMs}; {@code onAutoStop} then gets the WAV.
 */
export async function startPcmRecording(deviceId: string | null, maxMs: number, onAutoStop?: (wav: Blob) => void): Promise<PcmRecording> {
  const ctx = new AudioContext();
  void ctx.resume().catch(() => {});
  let stream: MediaStream;
  try {
    stream = (await openMic(deviceId)).stream;
    await ctx.audioWorklet.addModule("/worklets/pcm-capture.js");
  } catch (e) {
    void ctx.close().catch(() => {});
    throw e;
  }
  const source = ctx.createMediaStreamSource(stream);
  const node = new AudioWorkletNode(ctx, "pcm-capture");
  const mute = ctx.createGain();
  mute.gain.value = 0;
  source.connect(node);
  node.connect(mute);
  mute.connect(ctx.destination);
  const down = createDownsampler(ctx.sampleRate);
  const frames: Int16Array[] = [];
  node.port.onmessage = (ev: MessageEvent<Float32Array>) => { frames.push(...downsampleTo16k(ev.data, down)); };

  let done = false;
  const finish = (): Blob | null => {
    if (done) return null;
    done = true;
    clearTimeout(timer);
    node.port.close();
    node.disconnect();
    stream.getTracks().forEach((t) => t.stop());
    void ctx.close().catch(() => {});
    return new Blob([toWav(frames, 16000)], { type: "audio/wav" });
  };
  const timer = setTimeout(() => { const wav = finish(); if (wav) onAutoStop?.(wav); }, maxMs);
  return {
    stop: async () => finish() ?? new Blob([toWav(frames, 16000)], { type: "audio/wav" }),
    cancel: () => { finish(); },
  };
}
