// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { HomeDashboard } from "./HomeDashboard";
import { WeekCard } from "./WeekCard";
import { boloLine, greeting } from "./greeting";
import type { Home, Week } from "./home";

afterEach(cleanup);

const days = (states: string[]) => states.map((state, i) => ({ date: `2026-11-${String(7 + i).padStart(2, "0")}`, state })) as Week["days"];
const props = (home: Home | null) => ({ home, minutes: 5, hasDay1: false, keysOn: true, onTalk: vi.fn(), onFeedback: vi.fn(),
  onPractice: vi.fn(), onProgress: vi.fn(), onPlayDay1: vi.fn() });

describe("the dashboard Home (P1f)", () => {
  it("greetingFollowsTheClock", () => {
    expect(greeting(8, "Rafi Ahmed")).toBe("Good morning, Rafi");
    expect(greeting(14, null)).toBe("Good afternoon");
    expect(greeting(20, "Rafi")).toBe("Good evening, Rafi");
  });

  it("boloLineUsesTheLastTopic", () => {
    expect(boloLine("The payment bug demo")).toBe("Last time: The payment bug demo. Tell me what happened next.");
    expect(boloLine(null)).toBe("Bolo talks out loud, so turn your sound on.");
    expect(boloLine("Anything", "See you tomorrow. Bolo will be here.")).toBe("See you tomorrow. Bolo will be here.");
  });

  it("weekCardShowsRestAndRun", () => {
    render(<WeekCard week={{ run: 6, restUsed: true, welcomeBack: false, days: days(["done", "done", "rest", "done", "done", "today", "future"]) }} />);
    expect(screen.getByText("6-day run")).toBeTruthy();
    expect(screen.getByText("1 rest day used")).toBeTruthy();
    expect(screen.getByLabelText("Monday: rest day")).toBeTruthy();
  });

  it("welcomeBackHasNoGuilt", () => {
    const { container } = render(<WeekCard week={{ run: 0, restUsed: false, welcomeBack: true, days: days(["missed", "missed", "missed", "missed", "missed", "today", "future"]) }} />);
    expect(screen.getByText("Welcome back. Let's start a new run.")).toBeTruthy();
    expect(container.textContent).not.toMatch(/broke|lost|missed|fail/i);
  });

  it("has one primary, the Talk disc, and at most one Next up card", () => {
    const { container } = render(<HomeDashboard {...props({ primary: "talk", card: { kind: "feedback", talkId: "t1" } })} />);
    expect(container.querySelectorAll(".bo-btn--primary")).toHaveLength(0);
    expect(container.querySelectorAll(".bo-talk")).toHaveLength(1);
    expect(container.querySelectorAll("[data-home-card]")).toHaveLength(1);
  });

  it("opens the feedback, or practice on the card's sound", () => {
    const p = props({ primary: "talk", card: { kind: "feedback", talkId: "t1" } });
    render(<HomeDashboard {...p} />);
    fireEvent.click(screen.getByRole("button", { name: /finish your feedback/i }));
    expect(p.onFeedback).toHaveBeenCalledWith("t1");
    cleanup();
    const q = props({ primary: "talk", card: { kind: "practice", pattern: "V_BH", title: "The v sound" } });
    render(<HomeDashboard {...q} />);
    fireEvent.click(screen.getByRole("button", { name: /practise the v sound/i }));
    expect(q.onPractice).toHaveBeenCalledWith("V_BH");
  });

  it("Space starts a talk, but not while a sheet is open", () => {
    const p = props(null);
    render(<HomeDashboard {...p} />);
    fireEvent.keyDown(window, { key: " " });
    expect(p.onTalk).toHaveBeenCalledTimes(1);
    cleanup();
    const q = { ...props(null), keysOn: false };
    render(<HomeDashboard {...q} />);
    fireEvent.keyDown(window, { key: " " });
    expect(q.onTalk).not.toHaveBeenCalled();
  });
});

describe("Home review fixes", () => {
  it("Next up and This week are headings", () => {
    render(<HomeDashboard {...props({ primary: "talk", card: null })} />);
    expect(screen.getByRole("heading", { level: 2, name: "Next up" })).toBeTruthy();
    expect(screen.getByRole("heading", { level: 2, name: /This week/ })).toBeTruthy();
  });

  it("shows no Next up card while Home is loading", () => {
    const { container } = render(<HomeDashboard {...props(null)} />);
    expect(container.querySelectorAll("[data-home-card]")).toHaveLength(0);
  });
});
