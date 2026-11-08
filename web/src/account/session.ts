import { api, type LearnerView } from "@/api/browser";
import { loadClip, saveClip } from "@/audio/clipStore";
import { googleIdToken, type FirebaseWebConfig } from "./firebase";
import { currentInvite } from "./invite";

export type AuthConfig = ({ enabled: true } & FirebaseWebConfig) | { enabled: false };
export type SignInResult =
  | { kind: "ok"; learner: LearnerView }
  | { kind: "cancelled" }
  | { kind: "blocked" }
  | { kind: "invite" }
  | { kind: "off" }
  | { kind: "failed" };

/** Sign-in is on only when the server has Firebase's config; otherwise everyone stays a guest. */
export async function fetchAuthConfig(): Promise<AuthConfig> {
  const res = await api.get<AuthConfig>("/api/auth/config");
  return res.kind === "ok" ? res.value : { enabled: false };
}

export async function fetchMe(): Promise<LearnerView | null> {
  const res = await api.get<LearnerView>("/api/me");
  return res.kind === "ok" ? res.value : null;
}

/** ACC-1 / US-8.1 / US-8.4: Google, then Bolo's own session; guest data comes along on the server. */
export async function signInWithGoogle(): Promise<SignInResult> {
  const config = await fetchAuthConfig();
  if (!config.enabled) return { kind: "off" };
  const token = await googleIdToken(config);
  if (token.kind !== "token") return token;
  const res = await api.post<LearnerView>("/api/auth/google", { idToken: token.idToken, invite: currentInvite() });
  if (res.kind === "ok") {
    void uploadDay1(res.value);
    return { kind: "ok", learner: res.value };
  }
  return res.kind === "refused" && res.code === "invite" ? { kind: "invite" } : { kind: "failed" };
}

export async function signOut(): Promise<void> {
  await api.post<void>("/api/auth/sign-out");
}

/** ONB-4: the Day 1 clip goes with the account only if the learner chose to keep recordings. */
export async function uploadDay1(learner: LearnerView): Promise<void> {
  if (!learner.consent?.keepRecordings) return;
  try {
    const clip = await loadClip("day1");
    if (!clip) return;
    const form = new FormData();
    form.append("audio", clip, "day1");
    await fetch("/api/me/day1", { method: "PUT", credentials: "same-origin", headers: { "X-Bolo": "1" }, body: form });
  } catch {
    // the clip stays on this device
  }
}

/** ACC-7: on a new browser, the Day 1 clip comes back from the account. */
export async function restoreDay1(): Promise<void> {
  try {
    if (await loadClip("day1")) return;
    const res = await fetch("/api/me/day1", { credentials: "same-origin" });
    if (res.ok) await saveClip("day1", await res.blob());
  } catch {
    // nothing to restore
  }
}
