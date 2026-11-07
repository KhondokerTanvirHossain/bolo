import type { NextConfig } from "next";

const devApi = process.env.BOLO_DEV_API; // e.g. http://localhost:8080 — in production Caddy routes /api.

const nextConfig: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  // No next/image: never load sharp (the standalone copy is built on macOS and would not run on the amd64 box).
  images: { unoptimized: true },
  async rewrites() {
    return devApi ? [{ source: "/api/:path*", destination: `${devApi}/api/:path*` }] : [];
  },
};

export default nextConfig;
