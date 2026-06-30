#!/usr/bin/env node
/**
 * run-e2e.mjs — E2E orchestration helper
 *
 * Starts the full stack required for E2E tests, runs Playwright, then tears
 * everything down.
 *
 * Usage:
 *   node scripts/run-e2e.mjs
 *   node scripts/run-e2e.mjs --headed
 *   node scripts/run-e2e.mjs --grep @smoke
 *
 * Environment variables:
 *   STUB_PORT          LLM stub port (default 8089)
 *   BACKEND_PORT       Spring Boot port (default 8080)
 *   FRONTEND_PORT      Angular dev server port (default 4200)
 *   OPENROUTER_BASE_URL  Injected into backend env (default http://127.0.0.1:${STUB_PORT})
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * STATUS: SCAFFOLD — stub wiring is complete; BE and FE start commands are
 * TODO stubs to be filled in P6.1.
 * ─────────────────────────────────────────────────────────────────────────────
 */

import { start as startStub, stop as stopStub } from '../stub/stub.mjs';
import { spawn }   from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const e2eRoot   = path.resolve(__dirname, '..');
const appRoot   = path.resolve(e2eRoot, '..');

const STUB_PORT     = parseInt(process.env.STUB_PORT     ?? '8089', 10);
const BACKEND_PORT  = parseInt(process.env.BACKEND_PORT  ?? '8080', 10);
const FRONTEND_PORT = parseInt(process.env.FRONTEND_PORT ?? '4200', 10);
const STUB_BASE_URL = `http://127.0.0.1:${STUB_PORT}`;

const playwrightArgs = process.argv.slice(2); // forward extra flags to playwright

let stubServer  = null;
let beProcess   = null;
let feProcess   = null;

// ── Helpers ──────────────────────────────────────────────────────────────────

function log(msg) {
  console.log(`[run-e2e] ${msg}`);
}

/**
 * Spawn a child process and wait until a URL returns HTTP 200 (liveness).
 * TODO (P6.1): extract into a shared waitForUrl() utility if needed.
 */
async function waitForUrl(url, timeoutMs = 120_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const res = await fetch(url);
      if (res.ok) return;
    } catch {
      // not ready yet
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`Timed out waiting for ${url} after ${timeoutMs}ms`);
}

function spawnAndForward(cmd, args, env = {}) {
  const proc = spawn(cmd, args, {
    cwd: appRoot,
    env: { ...process.env, ...env },
    stdio: ['ignore', 'inherit', 'inherit'],
    shell: true,
  });
  proc.on('error', (err) => log(`Process error (${cmd}): ${err.message}`));
  return proc;
}

// ── Teardown ─────────────────────────────────────────────────────────────────

async function teardown() {
  log('Tearing down…');
  if (feProcess)  { feProcess.kill('SIGTERM');  feProcess  = null; }
  if (beProcess)  { beProcess.kill('SIGTERM');  beProcess  = null; }
  if (stubServer) { await stopStub(stubServer); stubServer = null; }
  log('Done.');
}

process.on('SIGINT',  () => teardown().then(() => process.exit(130)));
process.on('SIGTERM', () => teardown().then(() => process.exit(143)));

// ── Main ─────────────────────────────────────────────────────────────────────

async function main() {
  // 1. Start LLM stub
  log(`Starting LLM stub on port ${STUB_PORT}…`);
  stubServer = await startStub(STUB_PORT);
  log('LLM stub ready.');

  // 2. Start Spring Boot backend
  // TODO (P6.1): Uncomment and adjust once backend start command is confirmed.
  // log(`Starting Spring Boot backend on port ${BACKEND_PORT}…`);
  // beProcess = spawnAndForward(
  //   process.platform === 'win32' ? 'mvnw.cmd' : './mvnw',
  //   ['spring-boot:run'],
  //   {
  //     OPENROUTER_BASE_URL: STUB_BASE_URL,
  //     SERVER_PORT: String(BACKEND_PORT),
  //   },
  // );
  // await waitForUrl(`http://localhost:${BACKEND_PORT}/actuator/health`);
  // log('Backend ready.');

  // 3. Start Angular frontend
  // TODO (P6.1): Uncomment and adjust once frontend start command is confirmed.
  // log(`Starting Angular frontend on port ${FRONTEND_PORT}…`);
  // feProcess = spawnAndForward('npm', ['start', '--prefix', 'frontend']);
  // await waitForUrl(`http://localhost:${FRONTEND_PORT}`);
  // log('Frontend ready.');

  log('Stack is up. Running Playwright…');
  log('NOTE (scaffold): Backend and Frontend are NOT started by this script yet.');
  log('Start them manually: cd app/frontend && npm start  |  cd app/backend && ./mvnw spring-boot:run');

  // 4. Run Playwright
  const { execa } = await import('execa').catch(() => {
    // execa not installed — fall back to built-in spawn
    return { execa: null };
  });

  let exitCode = 0;
  if (execa) {
    try {
      await execa(
        'npx', ['playwright', 'test', ...playwrightArgs],
        { cwd: e2eRoot, stdio: 'inherit' },
      );
    } catch (err) {
      exitCode = err.exitCode ?? 1;
    }
  } else {
    // Plain spawn fallback
    exitCode = await new Promise((resolve) => {
      const pw = spawn(
        'npx',
        ['playwright', 'test', ...playwrightArgs],
        { cwd: e2eRoot, stdio: 'inherit', shell: true },
      );
      pw.on('close', resolve);
    });
  }

  await teardown();
  process.exit(exitCode);
}

main().catch(async (err) => {
  console.error('[run-e2e] Fatal error:', err);
  await teardown();
  process.exit(1);
});
