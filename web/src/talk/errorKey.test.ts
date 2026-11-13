import { describe, expect, it } from "vitest";
import { errorKey } from "./useTalk";

describe("errorKey (diagnosis 2026-11-14)", () => {
  it("turns a browser error into a short key the event rules accept", () => {
    expect(errorKey(new DOMException("denied", "NotAllowedError"))).toBe("notallowederror");
    expect(errorKey(new RangeError("byte length"))).toBe("rangeerror");
    expect(errorKey("oops")).toBe("unknown");
    expect(errorKey(null)).toBe("unknown");
  });
});
