import type { NextConfig } from "next";

const devApi = process.env.BOLO_DEV_API; // e.g. http://localhost:8080 — in production Caddy routes /api.

const nextConfig: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  async rewrites() {
    return devApi ? [{ source: "/api/:path*", destination: `${devApi}/api/:path*` }] : [];
  },
};

export default nextConfig;
