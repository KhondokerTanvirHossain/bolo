import type { Metadata } from "next";
import { AdminApp } from "@/admin/AdminApp";

export const metadata: Metadata = { title: "Bolo" };

/** The founder's admin portal (P2a); everyone else sees "Not found". */
export default function AdminPage() {
  return <div style={{ height: "100%", overflowY: "auto" }}><AdminApp /></div>;
}
