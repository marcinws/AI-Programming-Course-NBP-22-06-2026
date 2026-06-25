import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright E2E configuration for Hardware Service Decision Copilot.
 *
 * Target stack:
 *   Frontend  — Angular SPA at http://localhost:4200 (npm start in app/frontend)
 *   Backend   — Spring Boot at http://localhost:8080
 *   LLM stub  — OpenAI-compatible stub at http://127.0.0.1:8089 (app/e2e/stub)
 *
 * The full webServer orchestration (stub + BE + FE) is wired in P6.1.
 * For now the config expects a manually started stack (reuseExistingServer: true).
 */
export default defineConfig({
  testDir: './tests',
  testMatch: '**/*.spec.ts',

  /* Run tests in parallel within each file; file-level parallelism off by
     default to avoid racing the single Angular dev server. */
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : 1,

  reporter: process.env.CI
    ? [['html', { open: 'never' }], ['list']]
    : [['html', { open: 'on-failure' }], ['list']],

  /* Global timeouts — conservative for a dev-mode Angular app + Spring Boot. */
  timeout: 60_000,
  expect: { timeout: 10_000 },

  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:4200',
    actionTimeout: 15_000,
    navigationTimeout: 30_000,

    /* Collect traces on the first retry so failures are diagnosable. */
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',

    /* Polish locale to match the app's language. */
    locale: 'pl-PL',
    timezoneId: 'Europe/Warsaw',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    // Additional browsers added in P6.1 once the suite stabilises:
    // { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    // { name: 'webkit',  use: { ...devices['Desktop Safari'] } },
  ],

  /*
   * webServer block is intentionally commented out for the scaffold phase.
   * In P6.1 replace with the real orchestration:
   *
   * webServer: [
   *   {
   *     // 1. LLM stub
   *     command: 'node stub/stub.mjs',
   *     url: 'http://127.0.0.1:8089/health',
   *     reuseExistingServer: true,
   *     timeout: 10_000,
   *   },
   *   {
   *     // 2. Spring Boot backend
   *     command: 'cd ../backend && ./mvnw spring-boot:run',
   *     url: 'http://localhost:8080/actuator/health',
   *     env: { OPENROUTER_BASE_URL: 'http://127.0.0.1:8089' },
   *     reuseExistingServer: true,
   *     timeout: 120_000,
   *   },
   *   {
   *     // 3. Angular frontend
   *     command: 'cd ../frontend && npm start',
   *     url: 'http://localhost:4200',
   *     reuseExistingServer: true,
   *     timeout: 120_000,
   *   },
   * ],
   */
});
