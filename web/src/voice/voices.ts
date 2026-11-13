import type { AnswerSpeed, AnswerTime, Mood, Settings, VoiceAccent, VoiceGender } from "@/settings/settings";

/** P3c (SET-5, SET-6; design system v38 ScreenVoicePicker, ScreenMoodPicker). */
type VoicePart = Pick<Settings, "voiceGender" | "voiceAccent" | "mood">;

export const GENDERS: { value: VoiceGender; label: string }[] = [
  { value: "female", label: "Female" },
  { value: "male", label: "Male" },
];

export const ACCENTS: { value: VoiceAccent; label: string; sub: string }[] = [
  { value: "british", label: "British", sub: "English from the UK" },
  { value: "american", label: "American", sub: "English from the US" },
  { value: "indian", label: "Indian", sub: "English from India" },
];

export const MOODS: { value: Mood; label: string; desc: string; example: string }[] = [
  { value: "friendly", label: "Friendly", desc: "Warm and curious, like a good friend", example: "Hi Rafi! How did the demo go? I've been wondering all week." },
  { value: "funny", label: "Funny", desc: "Playful, with light jokes. Laughs with you, never at you", example: "Hi Rafi! So, did the demo go well, or did the bug win this round?" },
  { value: "professional", label: "Professional", desc: "Like a friendly colleague. Good for interviews and meetings", example: "Good to see you, Rafi. How did the client demo go?" },
  { value: "calm", label: "Calm", desc: "Gentle and patient, with simple words. No rush", example: "Hi Rafi. Take your time. How did the demo go?" },
];

const gender = (s: VoicePart): VoiceGender => s.voiceGender ?? "female";
const accent = (s: VoicePart): VoiceAccent => s.voiceAccent ?? "british";

/** The server's voice key, e.g. "american-male". */
export function voiceKey(s: VoicePart, a: VoiceAccent = accent(s), g: VoiceGender = gender(s)): string {
  return `${a}-${g}`;
}

/** "British · Female", as the Settings row shows it. */
export function voiceSummary(s: VoicePart): string {
  const a = ACCENTS.find((x) => x.value === accent(s))!.label;
  const g = GENDERS.find((x) => x.value === gender(s))!.label;
  return `${a} · ${g}`;
}

export function moodLabel(s: VoicePart): string {
  return (MOODS.find((m) => m.value === (s.mood ?? "friendly")) ?? MOODS[0]).label;
}

/** AX-7 (changed 2026-11-14; design system v41 ScreenAnswerTime). */
export const ANSWER_TIMES: { value: AnswerTime; label: string; desc: string }[] = [
  { value: "auto", label: "Automatic", desc: "Bolo learns how long you take to start answering and matches it." },
  { value: "quick", label: "Quick", desc: "Bolo replies as soon as you stop. Help comes after 5 seconds of silence" },
  { value: "normal", label: "Normal", desc: "A short wait after you stop. Help comes after 8 seconds" },
  { value: "relaxed", label: "Relaxed", desc: "Plenty of time to think. Help comes after 30 seconds" },
];

type AnswerPart = Pick<Settings, "answerTime" | "answerTimeAuto">;

/** Bolo's current automatic pick, e.g. "Relaxed". */
export function autoSpeedLabel(s: AnswerPart): string {
  const pick: AnswerSpeed = s.answerTimeAuto ?? "normal";
  return ANSWER_TIMES.find((a) => a.value === pick)!.label;
}

/** "Automatic · Relaxed" or the chosen speed, as the Settings row shows it. */
export function answerTimeSummary(s: AnswerPart): string {
  const t = s.answerTime ?? "auto";
  if (t === "auto") return `Automatic · ${autoSpeedLabel(s)}`;
  return ANSWER_TIMES.find((a) => a.value === t)!.label;
}

/** One sample at a time across the app: starting one stops the last. */
let current: HTMLAudioElement | null = null;

export function playSample(key: string, onEnd: () => void): void {
  stopSample();
  const audio = new Audio(`/api/voices/${encodeURIComponent(key)}/sample`);
  current = audio;
  const done = () => { if (current === audio) current = null; onEnd(); };
  audio.addEventListener("ended", done);
  audio.addEventListener("error", done);
  void audio.play().catch(done);
}

export function stopSample(): void {
  if (!current) return;
  const a = current;
  current = null;
  a.pause();
  a.dispatchEvent(new Event("ended"));
}
