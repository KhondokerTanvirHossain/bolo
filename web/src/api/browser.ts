/** Browser → Java writes go straight through Caddy; every write carries X-Bolo: 1 (the API's CSRF guard). */
export type ApiResult<T> =
  | { kind: "ok"; value: T }
  | { kind: "refused"; code: string }
  | { kind: "signed-out" }
  | { kind: "offline" };

async function call<T>(method: "GET" | "POST" | "PUT", path: string, body?: unknown): Promise<ApiResult<T>> {
  let res: Response;
  try {
    res = await fetch(path, {
      method,
      credentials: "same-origin",
      headers: method !== "GET" ? { "Content-Type": "application/json", "X-Bolo": "1" } : {},
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    return { kind: "offline" };
  }
  if (res.status === 401) return { kind: "signed-out" };
  if (!res.ok) {
    const code = await res.json().then((j: { code?: string }) => j.code ?? "unknown").catch(() => "unknown");
    return { kind: "refused", code };
  }
  // 202/204 and other empty answers carry no body.
  const text = await res.text().catch(() => "");
  return { kind: "ok", value: (text ? JSON.parse(text) : undefined) as T };
}

export const api = {
  get: <T>(path: string) => call<T>("GET", path),
  post: <T>(path: string, body: unknown = {}) => call<T>("POST", path, body),
  put: <T>(path: string, body: unknown) => call<T>("PUT", path, body),
};

export type LearnerView = {
  learnerId: string;
  kind: "guest" | "account";
  consent: { keepRecordings: boolean; policyVersion: string } | null;
  profile: { goal: string; fearedMoment: string; talkMinutes: 5 | 10 } | null;
  settings?: { buddySpeed: "normal" | "slower"; captions: boolean };
  /** The Google account (ACC-1); null for guests. */
  account?: { email: string; name: string | null } | null;
};

export const POLICY_VERSION = "2026-11-v1";
