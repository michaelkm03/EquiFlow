import { defineConfig, devices } from '@playwright/test';

// Service URLs — override via environment variables when running against non-default ports
// api-gateway:       BASE_URL       (default :8080)
// auth-service:      AUTH_URL       (default :8081)
// market-data:       MARKET_URL     (default :8083)
// compliance:        COMPLIANCE_URL (default :8084)
// ledger:            LEDGER_URL     (default :8085)
// audit:             AUDIT_URL      (default :8087)

export default defineConfig({
  testDir: './tests',
  fullyParallel: false, // Trading tests must run sequentially due to shared state
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: [
    ['html'],
    ['list']
  ],
  timeout: 30000,
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:8080',
    extraHTTPHeaders: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'api-tests',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
