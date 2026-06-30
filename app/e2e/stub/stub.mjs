/**
 * OpenAI-compatible stub server for deterministic E2E and backend integration tests.
 *
 * Implements:
 *   POST /v1/chat/completions    (also /chat/completions without the prefix)
 *   GET  /health                 (liveness check)
 *
 * Scenario selection (priority order):
 *   1. Request header  X-Stub-Scenario: <name>
 *   2. Body field      scenario: "<name>"
 *   3. Model field / image content part → vision scenario
 *   4. Default → "approve" (structured APPROVE decision JSON)
 *
 * Available scenarios: approve | escalate | off-topic | updatedDecision |
 *                       5xx | timeout | chunked-stream | vision
 *
 * Default port: 8089 (override via PORT env var or the `start(port)` argument).
 *
 * Usage:
 *   # CLI
 *   node app/e2e/stub/stub.mjs
 *   PORT=9999 node app/e2e/stub/stub.mjs
 *
 *   # Programmatic (in tests)
 *   import { start, stop } from './stub.mjs';
 *   const server = await start(8089);
 *   await stop(server);
 */

import http from 'node:http';
import { SCENARIOS, resolveScenario } from './scenarios.mjs';

const DEFAULT_PORT = parseInt(process.env.PORT ?? '8089', 10);

// Fixed ID prefix (deterministic, not random)
const STUB_ID_PREFIX = 'stub-chatcmpl-';
let requestCounter = 0;

// ── OpenAI response builders ──────────────────────────────────────────────────

function makeId() {
  return `${STUB_ID_PREFIX}${++requestCounter}`.padEnd(32, '0').slice(0, 32);
}

/**
 * Builds a non-streaming OpenAI chat.completion JSON response.
 */
function buildCompletion(content, model) {
  const id = makeId();
  const created = Math.floor(Date.now() / 1000);
  return {
    id,
    object: 'chat.completion',
    created,
    model: model ?? 'stub-model',
    choices: [
      {
        index: 0,
        message: {
          role: 'assistant',
          content,
        },
        finish_reason: 'stop',
      },
    ],
    usage: {
      prompt_tokens: 42,
      completion_tokens: content.length > 0 ? Math.ceil(content.split(' ').length * 1.3) : 0,
      total_tokens: 42 + (content.length > 0 ? Math.ceil(content.split(' ').length * 1.3) : 0),
    },
  };
}

/**
 * Builds a single streaming chunk (chat.completion.chunk shape).
 */
function buildChunk(deltaContent, model, id, created, finishReason = null) {
  return {
    id,
    object: 'chat.completion.chunk',
    created,
    model: model ?? 'stub-model',
    choices: [
      {
        index: 0,
        delta: finishReason !== null ? {} : { content: deltaContent },
        finish_reason: finishReason,
      },
    ],
  };
}

// ── Stream helper ─────────────────────────────────────────────────────────────

/**
 * Sends a sequence of SSE events for a streaming response.
 * @param {http.ServerResponse} res
 * @param {string[]} tokens   Array of string pieces to emit as individual deltas
 * @param {string}   model
 */
function sendStream(res, tokens, model) {
  const id = makeId();
  const created = Math.floor(Date.now() / 1000);

  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive',
    'Transfer-Encoding': 'chunked',
  });

  // Opening chunk with role
  const roleChunk = {
    id,
    object: 'chat.completion.chunk',
    created,
    model: model ?? 'stub-model',
    choices: [{ index: 0, delta: { role: 'assistant', content: '' }, finish_reason: null }],
  };
  res.write(`data: ${JSON.stringify(roleChunk)}\n\n`);

  // Content chunks — one per token
  for (const token of tokens) {
    const chunk = buildChunk(token, model, id, created, null);
    res.write(`data: ${JSON.stringify(chunk)}\n\n`);
  }

  // Final chunk with finish_reason: stop
  const stopChunk = buildChunk('', model, id, created, 'stop');
  res.write(`data: ${JSON.stringify(stopChunk)}\n\n`);

  // SSE termination
  res.write('data: [DONE]\n\n');
  res.end();
}

// ── Error response helpers ────────────────────────────────────────────────────

function sendError(res, statusCode, message) {
  const body = JSON.stringify({
    error: {
      message,
      type: 'server_error',
      code: statusCode === 500 ? 'llm_unavailable' : 'error',
    },
  });
  res.writeHead(statusCode, { 'Content-Type': 'application/json' });
  res.end(body);
}

function sendJson(res, statusCode, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(statusCode, { 'Content-Type': 'application/json' });
  res.end(body);
}

// ── Request body parser ───────────────────────────────────────────────────────

function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', (chunk) => (data += chunk));
    req.on('end', () => {
      try {
        resolve(data.length > 0 ? JSON.parse(data) : {});
      } catch {
        resolve({});
      }
    });
    req.on('error', reject);
  });
}

// ── Main request handler ──────────────────────────────────────────────────────

async function handleRequest(req, res) {
  const { method, url, headers } = req;

  // Health check
  if (method === 'GET' && url === '/health') {
    sendJson(res, 200, { status: 'ok', stub: true });
    return;
  }

  // Chat completions endpoint (with and without /v1 prefix)
  const isChatCompletions =
    method === 'POST' &&
    (url === '/v1/chat/completions' || url === '/chat/completions');

  if (!isChatCompletions) {
    sendError(res, 404, `Stub: endpoint not found: ${method} ${url}`);
    return;
  }

  const body = await readBody(req);
  const scenarioKey = resolveScenario(body, headers);
  const scenario = SCENARIOS[scenarioKey];

  if (!scenario) {
    sendError(res, 500, `Stub: unknown scenario "${scenarioKey}"`);
    return;
  }

  // Handle delay (timeout scenario)
  if (scenario.delayMs && scenario.delayMs > 0) {
    await new Promise((r) => setTimeout(r, scenario.delayMs));
  }

  // Handle 5xx scenarios
  if (scenario.statusCode >= 500) {
    const content =
      typeof scenario.content === 'function' ? scenario.content(body) : scenario.content;
    res.writeHead(scenario.statusCode, { 'Content-Type': 'application/json' });
    res.end(content);
    return;
  }

  const content =
    typeof scenario.content === 'function' ? scenario.content(body) : scenario.content;

  // Streaming response
  if (body.stream === true) {
    const tokens = scenario.streaming ?? content.split(' ').map((w, i, a) => i < a.length - 1 ? w + ' ' : w);
    sendStream(res, tokens, body.model);
    return;
  }

  // Non-streaming response
  const completion = buildCompletion(content, body.model);
  sendJson(res, 200, completion);
}

// ── Server lifecycle ──────────────────────────────────────────────────────────

/**
 * Starts the stub HTTP server on the given port.
 * Returns a Promise that resolves with the `http.Server` instance once listening.
 *
 * @param {number} [port]   Port to listen on (default: DEFAULT_PORT / PORT env var)
 * @returns {Promise<http.Server>}
 */
export function start(port) {
  const listenPort = port ?? DEFAULT_PORT;
  return new Promise((resolve, reject) => {
    const server = http.createServer(async (req, res) => {
      try {
        await handleRequest(req, res);
      } catch (err) {
        console.error('[stub] Unhandled error:', err);
        if (!res.headersSent) {
          sendError(res, 500, 'Stub internal error');
        }
      }
    });

    server.on('error', reject);
    server.listen(listenPort, '127.0.0.1', () => {
      console.log(`[stub] OpenAI-compatible stub server listening on http://127.0.0.1:${listenPort}`);
      console.log(`[stub] Default scenario: approve | Port: ${listenPort}`);
      console.log(`[stub] Scenario selection: X-Stub-Scenario header | body.scenario | model/image-part | default=approve`);
      resolve(server);
    });
  });
}

/**
 * Gracefully stops the stub server.
 *
 * @param {http.Server} server
 * @returns {Promise<void>}
 */
export function stop(server) {
  return new Promise((resolve, reject) => {
    if (!server || !server.listening) {
      resolve();
      return;
    }
    server.close((err) => {
      if (err) reject(err);
      else resolve();
    });
  });
}

// ── CLI entry point ───────────────────────────────────────────────────────────

// When run directly (node stub.mjs) — not imported — start the server.
// ESM does not have `require.main === module`; use the import.meta check.
const isMain = process.argv[1]?.replace(/\\/g, '/').endsWith('stub/stub.mjs') ||
               process.argv[1]?.replace(/\\/g, '/').endsWith('stub.mjs');

if (isMain) {
  start(DEFAULT_PORT).catch((err) => {
    console.error('[stub] Failed to start:', err);
    process.exit(1);
  });
}
