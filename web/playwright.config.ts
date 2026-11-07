import { defineConfig, devices } from "@playwright/test";

const viewport = { width: 1280, height: 800 };

export default defineConfig({
  testDir: "./e2e",
  timeout: 30_000,
  use: { baseURL: "http://localhost:3100" },
  projects: [
    // Chromium runs on Linux only: on macOS its capture waits for the OS mic permission even with a fake device.
    ...(process.platform === "linux" ? [{
      name: "chromium",
      use: {
        ...devices["Desktop Chrome"],
        viewport,
        channel: "chromium",
        permissions: ["microphone"],
        launchOptions: { args: ["--use-fake-ui-for-media-stream", "--use-fake-device-for-media-stream"] },
      },
    }] : []),
    {
      // Runs everywhere, macOS included: Firefox's fake stream never touches the hardware.
      name: "firefox",
      use: {
        ...devices["Desktop Firefox"],
        viewport,
        launchOptions: {
          firefoxUserPrefs: { "media.navigator.streams.fake": true, "media.navigator.permission.disabled": true },
        },
      },
    },
  ],
  webServer: {
    command: "pnpm build && pnpm start -p 3100",
    url: "http://localhost:3100",
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
  },
});
