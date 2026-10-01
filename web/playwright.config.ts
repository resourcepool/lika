import { defineConfig, devices } from "@playwright/test"

/**
 * End-to-end tests against a running Lika API (in-memory database, default
 * configuration) and a running web server. See README.md.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["list"], ["junit", { outputFile: "test-results/e2e-junit.xml" }]] : "list",
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    trace: "retain-on-failure",
    colorScheme: "dark",
  },
  projects: [
    {
      name: "chromium",
      // E2E_BROWSER_CHANNEL=chrome runs the locally installed Chrome instead of Playwright's
      use: { ...devices["Desktop Chrome"], ...(process.env.E2E_BROWSER_CHANNEL ? { channel: process.env.E2E_BROWSER_CHANNEL } : {}) },
    },
  ],
})
