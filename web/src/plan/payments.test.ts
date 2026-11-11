import { describe, expect, it } from "vitest";
import { cleanTrxId, payerOk, paymentLine, trxOk } from "./payments";

describe("bKash fields", () => {
  it("trxIdAndNumberChecks", () => {
    expect(cleanTrxId(" 9ab3cd7ef1 ")).toBe("9AB3CD7EF1");
    expect(trxOk("9AB3CD7EF1")).toBe(true);
    expect(trxOk("AB-12")).toBe(false);
    expect(trxOk("ABCDEFG")).toBe(false);
    expect(payerOk("01812345678")).toBe(true);
    expect(payerOk("01212345678")).toBe(false);
    expect(payerOk("0181234567")).toBe(false);
  });
});

describe("the status line", () => {
  /** Review (P2e): the money came, but not the right amount: say it goes back, and how to pay again. */
  it("an amount that didn't match is sent back", () => {
    expect(paymentLine({ status: "rejected", plan: "basic", trxId: "9AB3CD7EF1", reason: "Amount doesn't match" }))
      .toBe("The amount didn't match the plan's price, so we'll send it back to the number you paid from within 7 days. Then send the exact amount as a new payment.");
  });
});
