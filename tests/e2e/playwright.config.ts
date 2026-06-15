import { defineConfig, devices } from '@playwright/test';
import path from 'path';

/**
 * Service URL defaults — all overridable via environment variables.
 * In CI each stage sets these to point at the appropriate environment.
 *
 * Local defaults:
 *   api-gateway       BASE_URL        http://localhost:8080
 *   auth-service      AUTH_URL        http://localhost:8081
 *   market-data       MARKET_URL      http://localhost:8083
 *   compliance        COMPLIANCE_URL  http://localhost:8084
 *   ledger            LEDGER_URL      http://localhost:8085
 *   audit             AUDIT_URL       http://localhost:8087
 */

export default defineConfig({
  testDir: './tests',

  webServer: {
    command: 'npm run dev',
    cwd: path.resolve(__dirname, '../../frontend'),
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 30_000,
  },

  // E2E tests are sequentially ordered — shared service state means parallel
  // runs can create order/ledger conflicts. Enable per-file parallelism once
  // each suite has proper data isolation (unique user-per-test seeding).
  fullyParallel: false,
  workers: 1,

  // Fail fast in CI if test.only was accidentally committed
  forbidOnly: !!process.env.CI,

  // Retry flaky tests twice in CI; no retries locally (fail fast for dev speed)
  retries: process.env.CI ? 2 : 0,

  // 30s per test; E2E saga tests may need up to 15s for polling
  timeout: 30000,

  reporter: [
    ['html', { open: 'never' }],
    ['list'],
    // GitHub Actions annotation reporter — makes failures visible inline on PRs
    ...(process.env.CI ? [['github'] as ['github']] : []),
  ],

  use: {
    headless: false,
    baseURL: process.env.BASE_URL || 'http://localhost:8080',
    extraHTTPHeaders: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
    // Capture trace on first retry — upload to trace.playwright.dev for debugging
    trace: 'on-first-retry',
  },

  projects: [
    /**
     * UI — @ui
     * Browser-level tests against the React frontend (localhost:5173).
     * Does not require Java backend services. Validates render, state
     * transitions, and interactive element behavior. Runs on every PR
     * alongside smoke tests.
     */
    {
      name: 'ui',
      grep: /@ui/,
      use: {
        ...devices['Desktop Chrome'],
        headless: false,
        baseURL: process.env.FRONTEND_URL || 'http://localhost:5173',
      },
    },

    /**
     * SMOKE — @smoke
     * Runs on every PR as a required gate. Fast (<60s). Tests auth and
     * market-data health only. No order submission, no cross-service flows.
     * If smoke fails, no point running the rest.
     */
    {
      name: 'smoke',
      grep: /@smoke/,
      use: { ...devices['Desktop Chrome'] },
    },

    /**
     * INTEGRATION — @integration
     * Runs on merge to main and in the staging environment gate.
     * Tests each service's API contract in isolation (within one service).
     * Requires all services running (docker-compose up).
     */
    {
      name: 'integration',
      grep: /@integration/,
      use: { ...devices['Desktop Chrome'] },
    },

    /**
     * E2E — @e2e
     * Full cross-service saga tests. Runs in staging and pre-prod gates,
     * and in the nightly regression suite. These are the highest-confidence
     * tests — they validate the entire order lifecycle end to end.
     */
    {
      name: 'e2e',
      grep: /@e2e/,
      use: { ...devices['Desktop Chrome'] },
    },

    /**
     * REGRESSION — no tag filter (runs everything)
     * Full suite: smoke + integration + e2e. Used for nightly runs and
     * pre-production release gates. Slowest but highest coverage.
     */
    {
      name: 'regression',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
