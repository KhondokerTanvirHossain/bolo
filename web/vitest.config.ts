import { defineConfig } from "vitest/config";
import path from "node:path";

export default defineConfig({
  // Component tests (*.test.tsx, jsdom per file) need JSX compiled the React 17+ way.
  esbuild: { jsx: "automatic" },
  resolve: { alias: { "@": path.resolve(__dirname, "src") } },
  test: { include: ["src/**/*.test.ts", "src/**/*.test.tsx"] },
});
