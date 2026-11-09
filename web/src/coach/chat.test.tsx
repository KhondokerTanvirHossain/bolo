// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { segments, type Chat } from "./chat";
import { ChatScreen } from "./ChatScreen";

describe("the full chat (DBF-6)", () => {
  afterEach(cleanup);

  it("segmentsSplitAtMarks", () => {
    const s = segments("I am very happy", [{ turn: 0, start: 5, end: 9, fix: 1, title: "The v sound" }]);
    expect(s.map((x) => x.text)).toEqual(["I am ", "very", " happy"]);
    expect(s[1].mark?.fix).toBe(1);
  });

  it("overlappingMarksKeepTheText", () => {
    const text = "I am very happy";
    const s = segments(text, [
      { turn: 0, start: 0, end: 15, fix: 2, title: "Say it better" },
      { turn: 0, start: 5, end: 9, fix: 1, title: "The v sound" },
    ]);
    expect(s.map((x) => x.text).join("")).toBe(text);
    expect(new Set(s.filter((x) => x.mark).map((x) => x.mark!.fix))).toEqual(new Set([1, 2]));
  });

  it("textIsNeverHtml", () => {
    const chat: Chat = { topic: "Tags", turns: [{ speaker: "learner", text: "I wrote <b>bold</b> & more" }], marks: [] };
    render(<ChatScreen chat={chat} onBack={() => {}} onOpenFix={() => {}} />);
    expect(screen.getByText("I wrote <b>bold</b> & more")).toBeTruthy();
    expect(document.querySelector(".dk-bubble b")).toBeNull();
  });
});
