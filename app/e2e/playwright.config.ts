import { defineConfig, devices } from '@playwright/test';
import path from 'path';

/**
 * Playwright E2E configuration for Hardware Service Decision Copilot.
 *
 * Stack (P6.1):
 *   1. LLM stub     — OpenAI-compatible stub at http://127.0.0.1:8089
 *   2. Backend      — Spring Boot at http://localhost:8080 (pointed at the stub)
 *   3. Frontend     — Angular SPA at http://localhost:4200 (proxies /api → :8080)
 *
 * All three servers are managed by the webServer array below.
 * Each uses reuseExistingServer: true so a running dev stack is not killed.
 *
 * STUB SUBSTITUTION: The backend is started with OPENROUTER_BASE_URL=http://127.0.0.1:8089
 * so all LLM calls go to the local stub, never to the real OpenRouter.
 * This is logged by the stub on startup and visible in Playwright's server output.
 */

const e2eRoot = path.resolve(import.meta.dirname ?? __dirname);
const backendRoot = path.resolve(e2eRoot, '..', 'backend');
const frontendRoot = path.resolve(e2eRoot, '..', 'frontend');

// Windows vs Unix: Maven wrapper has different file name.
const mvnwCmd =
  process.platform === 'win32'
    ? 'cmd /c mvnw.cmd spring-boot:run'
    : './mvnw spring-boot:run';

export default defineConfig({
  testDir: './tests',
  testMatch: '**/*.spec.ts',

  /* Sequential within a file; single worker keeps the shared dev-mode stack stable. */
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,

  reporter: process.env.CI
    ? [['html', { open: 'never' }], ['list']]
    : [['html', { open: 'on-failure' }], ['list']],

  /* Global timeouts — Spring Boot cold-start + Angular compile both take time. */
  timeout: 120_000,
  expect: { timeout: 15_000 },

  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:4200',
    actionTimeout: 20_000,
    navigationTimeout: 60_000,

    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',

    /* Polish locale matches the app language. */
    locale: 'pl-PL',
    timezoneId: 'Europe/Warsaw',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  /*
   * ── Web-server orchestration (P6.1) ──────────────────────────────────────
   *
   * Order matters: stub → backend → frontend.
   * Each entry blocks until its `url` returns HTTP 200 (or `timeout` is reached).
   *
   * STUB SUBSTITUTION is explicit: the backend env vars point to the local stub.
   * The stub logs "[stub] OpenAI-compatible stub server listening …" at startup.
   */
  webServer: [
    // 1. LLM stub (fast — Node.js, ready in <2 s)
    {
      command: 'node stub/stub.mjs',
      url: 'http://127.0.0.1:8089/health',
      reuseExistingServer: true,
      timeout: 15_000,
      stdout: 'pipe',
      stderr: 'pipe',
    },
    // 2. Spring Boot backend (slow cold-start ~60 s)
    {
      command: `${mvnwCmd} -pl . -am`,
      cwd: backendRoot,
      url: 'http://localhost:8080/actuator/health',
      reuseExistingServer: true,
      timeout: 180_000,
      env: {
        // Redirect all LLM calls to the local stub — NEVER hits real OpenRouter
        OPENROUTER_BASE_URL: 'http://127.0.0.1:8089',
        OPENROUTER_API_KEY: 'e2e-test-key',
        OPENROUTER_VISION_MODEL: 'vision-e2e',
        OPENROUTER_TEXT_MODEL: 'text-e2e',
      },
      stdout: 'pipe',
      stderr: 'pipe',
    },
    // 3. Angular frontend (ng serve, ~60 s first compile)
    {
      command: 'npm start',
      cwd: frontendRoot,
      url: 'http://localhost:4200',
      reuseExistingServer: true,
      timeout: 180_000,
      stdout: 'pipe',
      stderr: 'pipe',
    },
  ],
});
