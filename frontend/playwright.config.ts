import { defineConfig, devices } from '@playwright/test'

// End-to-end tests: a real browser against the real app (nginx + Spring Boot + Postgres + Redis).
// Start the app first:
//   cd infra && APP_RATELIMIT_ENABLED=false docker compose --profile app up -d --build
// then:  npm run test:e2e
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8000',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    locale: 'zh-TW',
  },
  projects: [
    {
      name: 'chrome',
      use: {
        ...devices['Desktop Chrome'],
        // Locally, use the Chrome that's already installed instead of downloading another browser.
        ...(process.env.CI ? {} : { channel: 'chrome' }),
      },
    },
  ],
})
