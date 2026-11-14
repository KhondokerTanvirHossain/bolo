import type { Metadata, Viewport } from "next";
import "../styles/bolo.css";
import "../styles/app.css";

// Discretion at work: the tab title is always just "Bolo" (design system, Desktop and web).
// Installable for reminders on iPhone (PRG-5): the Home Screen icon and name.
export const metadata: Metadata = {
  title: "Bolo",
  description: "Talk first. Get better after.",
  appleWebApp: { capable: true, title: "Bolo", statusBarStyle: "default" },
  icons: { icon: "/icons/icon-192.png", apple: "/icons/apple-touch-icon.png" },
};
export const viewport: Viewport = { width: "device-width", initialScale: 1, themeColor: "#1D1236" };

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <head>
        {/* Same font request as the locked prototype; self-hosting is a Phase 1c task (CSP pass). */}
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="" />
        <link
          rel="stylesheet"
          href="https://fonts.googleapis.com/css2?family=Anek+Bangla:wdth,wght@75..125,100..800&family=Noto+Sans+Bengali:wght@400..700&family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@20..48,300..700,0..1,0&display=swap"
        />
      </head>
      <body>
        <div id="root" className="dk-root" data-theme="daylight">{children}</div>
      </body>
    </html>
  );
}
