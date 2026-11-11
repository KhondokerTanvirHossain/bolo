import { describe, expect, it } from "vitest";
import { cleanTrxId, payerOk, trxOk } from "./payments";

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
