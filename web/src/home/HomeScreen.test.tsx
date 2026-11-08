// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { HomeScreen } from "./HomeScreen";

afterEach(cleanup);

const handlers = () => ({ onTalk: vi.fn(), onPractice: vi.fn(), onFeedback: vi.fn(), onSettings: vi.fn(), onPlayDay1: vi.fn() });

describe("HomeScreen", () => {
  /** HOM-1 / R1: exactly one filled button and at most one card. */
  it("shows one filled button and at most one card", () => {
    const { container } = render(<HomeScreen home={{ primary: "talk", card: { kind: "feedback", talkId: "t1" } }} hasDay1={true} {...handlers()} />);
    expect(container.querySelectorAll(".bo-btn--primary")).toHaveLength(1);
    expect(container.querySelectorAll("[data-home-card]")).toHaveLength(1);
  });

  it("opens the feedback from its card", () => {
    const h = handlers();
    render(<HomeScreen home={{ primary: "talk", card: { kind: "feedback", talkId: "t1" } }} hasDay1={false} {...h} />);
    fireEvent.click(screen.getByRole("button", { name: /finish your feedback/i }));
    expect(h.onFeedback).toHaveBeenCalledWith("t1");
  });

  it("opens practice on the card's sound", () => {
    const h = handlers();
    render(<HomeScreen home={{ primary: "talk", card: { kind: "practice", pattern: "V_BH", title: "The v sound" } }} hasDay1={false} {...h} />);
    fireEvent.click(screen.getByRole("button", { name: /practise the v sound/i }));
    expect(h.onPractice).toHaveBeenCalledWith("V_BH");
  });

  it("shows just the talk button when there is nothing else, or Home couldn't load", () => {
    const { container } = render(<HomeScreen home={null} hasDay1={false} {...handlers()} />);
    expect(container.querySelectorAll("[data-home-card]")).toHaveLength(0);
    expect(screen.getByRole("button", { name: /start talking/i })).toBeTruthy();
  });
});
