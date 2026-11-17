import { describe, expect, it } from "vitest";
import { langOf } from "./bangla";

/** Founder 2026-11-18 (DS v46 ScreenBanglaTurn): a turn said in Bangla shows in Bangla script, marked lang="bn". */
describe("langOf", () => {
  it("marks Bangla script", () => {
    expect(langOf("আমার বস শেষ মুহূর্তে পুরো পরিকল্পনা বদলে দিয়েছে।")).toBe("bn");
    expect(langOf("আমি next week এ interview দিতে যাচ্ছি")).toBe("bn");
  });
  it("leaves English alone", () => {
    expect(langOf("I went to অফিস today and met my manager")).toBeUndefined();
    expect(langOf("hello")).toBeUndefined();
    expect(langOf("")).toBeUndefined();
    expect(langOf(undefined)).toBeUndefined();
  });
});
