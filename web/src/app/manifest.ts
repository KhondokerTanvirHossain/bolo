import type { MetadataRoute } from "next";

/** Installable Bolo (iPhone needs it on the Home Screen for reminders, PRG-5). Colours: the night canvas. */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Bolo",
    short_name: "Bolo",
    description: "Talk first. Get better after.",
    start_url: "/",
    scope: "/",
    display: "standalone",
    background_color: "#1D1236",
    theme_color: "#1D1236",
    icons: [
      { src: "/icons/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
      { src: "/icons/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
      { src: "/icons/icon-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
  };
}
