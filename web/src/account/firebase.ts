import type { Auth } from "firebase/auth";

/** Firebase's public web config (not secret), read from GET /api/auth/config at runtime. */
export type FirebaseWebConfig = { apiKey: string; authDomain: string; projectId: string; appId: string };
export type GoogleToken = { kind: "token"; idToken: string } | { kind: "cancelled" } | { kind: "blocked" } | { kind: "failed" };

let auth: Auth | null = null;

/**
 * Continue with Google (ACC-1), the ddinter2 way: the Firebase SDK loads only when used, keeps nothing (in-memory),
 * and we sign out of Firebase straight after: Bolo's own session cookie is the session. End-to-end tests set
 * window.__boloFakeIdToken; the server still verifies every token, so the hook grants nothing in production.
 */
export async function googleIdToken(config: FirebaseWebConfig): Promise<GoogleToken> {
  const fake = (window as unknown as { __boloFakeIdToken?: string }).__boloFakeIdToken;
  if (fake) return { kind: "token", idToken: fake };
  try {
    const [{ getApps, initializeApp }, fb] = await Promise.all([import("firebase/app"), import("firebase/auth")]);
    if (!auth) {
      const app = getApps()[0] ?? initializeApp(config);
      auth = fb.initializeAuth(app, { persistence: fb.inMemoryPersistence, popupRedirectResolver: fb.browserPopupRedirectResolver });
    }
    const provider = new fb.GoogleAuthProvider();
    provider.setCustomParameters({ prompt: "select_account" });
    const result = await fb.signInWithPopup(auth, provider);
    const idToken = await result.user.getIdToken();
    void fb.signOut(auth).catch(() => {});
    return { kind: "token", idToken };
  } catch (e) {
    const code = (e as { code?: string }).code ?? "";
    if (code === "auth/popup-closed-by-user" || code === "auth/cancelled-popup-request" || code === "auth/user-cancelled") return { kind: "cancelled" };
    if (code === "auth/popup-blocked") return { kind: "blocked" };
    return { kind: "failed" };
  }
}
