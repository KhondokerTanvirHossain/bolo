import type { Metadata } from "next";
import { PricingScreen } from "@/plan/PricingScreen";

// Discretion: the tab title is always just "Bolo" (design system).
export const metadata: Metadata = { title: "Bolo" };

/** P2c: the public pricing page (works signed out). */
export default function PricingPage() {
  return <PricingScreen />;
}
