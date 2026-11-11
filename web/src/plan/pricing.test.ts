// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";
import { priceLabel, regionGuess } from "./pricing";

const basic = { plan: "basic", monthlyTalks: 8, priceBdt: 300, priceUsd: 3 };

describe("regionGuess", () => {
  afterEach(() => { vi.restoreAllMocks(); window.localStorage.clear(); });
  const zone = (timeZone: string) => vi.spyOn(Intl, "DateTimeFormat").mockReturnValue({ resolvedOptions: () => ({ timeZone }) } as Intl.DateTimeFormat);

  it("regionGuessReadsTheTimeZone", () => {
    zone("Asia/Dhaka");
    expect(regionGuess()).toBe("bd");
    zone("Europe/London");
    expect(regionGuess()).toBe("intl");
  });
  it("falls back to intl when the time zone can't be read", () => {
    vi.spyOn(Intl, "DateTimeFormat").mockImplementation(() => { throw new Error("no Intl"); });
    expect(regionGuess()).toBe("intl");
  });
  it("a saved choice wins over the time zone", () => {
    zone("Asia/Dhaka");
    window.localStorage.setItem("bolo.region", "intl");
    expect(regionGuess()).toBe("intl");
  });
});

describe("priceLabel", () => {
  it("priceLabelUsesTakaOrDollars", () => {
    expect(priceLabel(basic, "bd")).toBe("৳300 a month");
    expect(priceLabel(basic, "intl")).toBe("$3 a month");
    expect(priceLabel({ ...basic, priceUsd: 4.5 }, "intl")).toBe("$4.50 a month");
  });
});
