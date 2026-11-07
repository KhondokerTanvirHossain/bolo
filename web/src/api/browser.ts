/** Browser → Java writes go straight through Caddy; every write carries X-Bolo: 1 (the API's CSRF guard). */
export type ApiResult<T> =
  | { kind: "ok"; value: T }
  | { kind: "refused"; code: string }
  | { kind: "signed-out" }
  | { kind: "offline" };

async function call<T>(method: "GET" | "POST", path: string, body?: unknown): Promise<ApiResult<T>> {
  let res: Response;
  try {
    res = await fetch(path, {
      method,
      credentials: "same-origin",
      headers: method === "POST" ? { "Content-Type": "application/json", "X-Bolo": "1" } : {},
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
  if (res.status === 204) return { kind: "ok", value: undefined as T };
  return { kind: "ok", value: (await res.json()) as T };
}

export const api = {
  get: <T>(path: string) => call<T>("GET", path),
  post: <T>(path: string, body: unknown = {}) => call<T>("POST", path, body),
};

export type LearnerView = {
  learnerId: string;
  kind: "guest" | "account";
  consent: { keepRecordings: boolean; policyVersion: string } | null;
};

export const POLICY_VERSION = "2026-11-v1";
