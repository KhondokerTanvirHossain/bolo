import { api } from "@/api/browser";

/** P2e: pay with bKash (Send Money to the founder's number, then the TrxID); checked by hand in the admin portal. */
export type MyPayment = { status: "pending" | "approved" | "rejected"; plan: string; trxId: string; reason: string | null };

export const cleanTrxId = (s: string) => s.replace(/\s+/g, "").toUpperCase();
export const trxOk = (s: string) => /^[A-Z0-9]{8,12}$/.test(s);
export const payerOk = (s: string) => /^01[3-9]\d{8}$/.test(s.trim());

/** The founder's bKash number, or null when bKash payments are off. */
export async function fetchBkash(): Promise<string | null> {
  const r = await api.get<{ number: string } | undefined>("/api/payments/bkash");
  return r.kind === "ok" && r.value ? r.value.number : null;
}

export async function myPayment(): Promise<MyPayment | null> {
  const r = await api.get<MyPayment | undefined>("/api/payments/me");
  return r.kind === "ok" && r.value ? r.value : null;
}

export type PayResult = "ok" | "trx-used" | "payment-pending" | "bad" | "too-many" | "sign-in" | "off" | "failed";

export async function submitPayment(plan: string, trxId: string, payerNumber: string): Promise<PayResult> {
  const r = await api.post<void>("/api/payments", { plan, trxId: cleanTrxId(trxId), payerNumber: payerNumber.trim() });
  if (r.kind === "ok") return "ok";
  if (r.kind === "signed-out") return "sign-in";
  if (r.kind !== "refused") return "failed";
  switch (r.code) {
    case "trx-used": return "trx-used";
    case "payment-pending": return "payment-pending";
    case "bad-payment": return "bad";
    case "slow-down": return "too-many";
    case "sign-in": return "sign-in";
    case "bkash-off": return "off";
    default: return "failed";
  }
}

/** One calm line per outcome (never red, ScreenPlanPay). */
export const payProblem: Record<Exclude<PayResult, "ok">, string> = {
  "trx-used": "That TrxID has already been used. Check it in bKash's message.",
  "payment-pending": "You already have a payment being checked. We'll confirm it soon.",
  bad: "Check the TrxID (letters and digits from bKash's message) and your 11-digit bKash number.",
  "too-many": "That's a lot of tries today. Please try again tomorrow, or report a problem.",
  "sign-in": "Please sign in again, then try once more.",
  off: "bKash payments aren't open right now. Join the list instead.",
  failed: "That didn't go through. Check your connection, then try again.",
};

/** The status line on /pricing and Me. */
export function paymentLine(p: MyPayment | null): string | null {
  if (!p) return null;
  if (p.status === "pending") return `Payment being checked (TrxID ${p.trxId}).`;
  if (p.status === "rejected") return `We couldn't confirm your payment (${p.reason ?? "no reason given"}). Check the TrxID and try again, or report a problem.`;
  return null;
}
