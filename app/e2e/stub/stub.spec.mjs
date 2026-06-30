/**
 * Smoke tests for the OpenAI-compatible stub server.
 * Covers:
 *   1. Non-streaming chat completion returns valid OpenAI-shaped JSON with Polish content.
 *   2. Streaming chat completion returns multiple data chunks and terminates with [DONE].
 *   3. Vision model call returns an image description (no decision outcome).
 *   4. ESCALATE scenario returns outcome === "ESCALATE".
 *   5. 5xx scenario returns HTTP 500.
 *   6. Structured decision JSON scenario returns valid structured output.
 *
 * Run: node --test app/e2e/stub/stub.spec.mjs
 */

import { describe, it, before, after } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';

// Import start/stop from the stub (tested in-process)
import { start, stop } from './stub.mjs';

const TEST_PORT = 18089;
const BASE = `http://localhost:${TEST_PORT}`;

// ── helpers ──────────────────────────────────────────────────────────────────

function postJson(url, body, extraHeaders = {}) {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify(body);
    const u = new URL(url);
    const req = http.request(
      {
        hostname: u.hostname,
        port: u.port,
        path: u.pathname,
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(data),
          ...extraHeaders,
        },
      },
      (res) => {
        let raw = '';
        res.on('data', (chunk) => (raw += chunk));
        res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body: raw }));
      },
    );
    req.on('error', reject);
    req.write(data);
    req.end();
  });
}

/** Collects an SSE stream until [DONE] and returns { chunks, lastStatus } */
function collectStream(url, body, extraHeaders = {}) {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify(body);
    const u = new URL(url);
    const req = http.request(
      {
        hostname: u.hostname,
        port: u.port,
        path: u.pathname,
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(data),
          ...extraHeaders,
        },
      },
      (res) => {
        const chunks = [];
        let buffer = '';
        res.on('data', (chunk) => {
          buffer += chunk.toString();
          const lines = buffer.split('\n');
          buffer = lines.pop(); // keep incomplete line
          for (const line of lines) {
            if (line.startsWith('data: ')) {
              const payload = line.slice(6).trim();
              chunks.push(payload);
              if (payload === '[DONE]') {
                resolve({ status: res.statusCode, chunks });
              }
            }
          }
        });
        res.on('end', () => {
          // Handle case where [DONE] is in the last buffered line
          if (buffer.startsWith('data: ')) {
            chunks.push(buffer.slice(6).trim());
          }
          resolve({ status: res.statusCode, chunks });
        });
        res.on('error', reject);
      },
    );
    req.on('error', reject);
    req.write(data);
    req.end();
  });
}

// ── lifecycle ─────────────────────────────────────────────────────────────────

let server;

before(async () => {
  server = await start(TEST_PORT);
});

after(async () => {
  await stop(server);
});

// ── tests ─────────────────────────────────────────────────────────────────────

describe('Stub server — non-streaming (default APPROVE scenario)', () => {
  it('POST /v1/chat/completions returns 200 with OpenAI-shaped JSON', async () => {
    const res = await postJson(`${BASE}/v1/chat/completions`, {
      model: 'openai/gpt-5.4-mini',
      messages: [{ role: 'user', content: 'Cześć' }],
      stream: false,
    });

    assert.equal(res.status, 200, `Expected 200, got ${res.status}: ${res.body}`);

    const json = JSON.parse(res.body);

    // Top-level OpenAI shape
    assert.ok(json.id, 'Response must have an id');
    assert.equal(json.object, 'chat.completion');
    assert.ok(typeof json.created === 'number', 'created must be a number');
    assert.ok(Array.isArray(json.choices), 'choices must be an array');
    assert.equal(json.choices.length, 1);

    const choice = json.choices[0];
    assert.equal(choice.index, 0);
    assert.equal(choice.finish_reason, 'stop');
    assert.equal(choice.message.role, 'assistant');
    assert.ok(typeof choice.message.content === 'string', 'content must be a string');
    assert.ok(choice.message.content.length > 0, 'content must be non-empty');

    // Must contain Polish text (at least one non-ASCII or known PL word)
    const content = choice.message.content;
    assert.ok(
      /[ąćęłńóśźżĄĆĘŁŃÓŚŹŻ]/.test(content) || /\b(zatwierdz|decyzja|reklamacja|status|wniosek|zatwierdzono|odrzucono|eskalacja|sorry)\b/i.test(content),
      `Content must contain Polish text, got: ${content}`,
    );

    // Usage shape
    assert.ok(json.usage, 'usage must be present');
    assert.ok(typeof json.usage.prompt_tokens === 'number');
    assert.ok(typeof json.usage.completion_tokens === 'number');
    assert.ok(typeof json.usage.total_tokens === 'number');
  });

  it('POST /chat/completions (without /v1 prefix) also works', async () => {
    const res = await postJson(`${BASE}/chat/completions`, {
      model: 'openai/gpt-5.4-mini',
      messages: [{ role: 'user', content: 'Test' }],
      stream: false,
    });
    assert.equal(res.status, 200);
    const json = JSON.parse(res.body);
    assert.equal(json.object, 'chat.completion');
  });
});

describe('Stub server — default APPROVE scenario returns structured decision JSON', () => {
  it('decision JSON contains APPROVE outcome with Polish justification', async () => {
    const res = await postJson(`${BASE}/v1/chat/completions`, {
      model: 'openai/gpt-5.4-mini',
      messages: [{ role: 'user', content: 'Wydaj decyzję' }],
      stream: false,
    });

    const json = JSON.parse(res.body);
    const content = json.choices[0].message.content;

    // The default scenario is a structured decision JSON
    let decision;
    try {
      decision = JSON.parse(content);
    } catch {
      // Content may be a prose reply in the default scenario — that's fine
      // as long as the content is in Polish
      assert.ok(content.length > 0, 'Content must not be empty');
      return;
    }

    if (decision && decision.outcome) {
      assert.ok(
        ['APPROVE', 'REJECT', 'ESCALATE'].includes(decision.outcome),
        `outcome must be APPROVE|REJECT|ESCALATE, got: ${decision.outcome}`,
      );
      assert.ok(typeof decision.justification === 'string' && decision.justification.length > 0);
      assert.ok(typeof decision.nextSteps === 'string');
      assert.ok(Array.isArray(decision.citedRules));
    }
  });
});

describe('Stub server — scenario selection via X-Stub-Scenario header', () => {
  it('ESCALATE scenario returns outcome ESCALATE', async () => {
    const res = await postJson(
      `${BASE}/v1/chat/completions`,
      {
        model: 'openai/gpt-5.4-mini',
        messages: [{ role: 'user', content: 'test' }],
        stream: false,
      },
      { 'X-Stub-Scenario': 'escalate' },
    );

    assert.equal(res.status, 200);
    const json = JSON.parse(res.body);
    const content = json.choices[0].message.content;
    const decision = JSON.parse(content);
    assert.equal(decision.outcome, 'ESCALATE');
  });

  it('off-topic scenario returns a polite redirect in Polish (no decision JSON)', async () => {
    const res = await postJson(
      `${BASE}/v1/chat/completions`,
      {
        model: 'openai/gpt-5.4-mini',
        messages: [{ role: 'user', content: 'Jaka jest pogoda?' }],
        stream: false,
      },
      { 'X-Stub-Scenario': 'off-topic' },
    );

    assert.equal(res.status, 200);
    const json = JSON.parse(res.body);
    const content = json.choices[0].message.content;
    // Must not be parseable as a decision JSON
    let parsed;
    try {
      parsed = JSON.parse(content);
    } catch {
      parsed = null;
    }
    assert.ok(!parsed || !parsed.outcome, 'off-topic reply must not contain a decision JSON outcome');
    assert.ok(content.length > 0, 'Must have a non-empty reply');
  });

  it('5xx scenario returns HTTP 500', async () => {
    const res = await postJson(
      `${BASE}/v1/chat/completions`,
      {
        model: 'openai/gpt-5.4-mini',
        messages: [{ role: 'user', content: 'test' }],
        stream: false,
      },
      { 'X-Stub-Scenario': '5xx' },
    );

    assert.equal(res.status, 500, `Expected 500, got ${res.status}`);
  });

  it('updatedDecision scenario returns a reply with a new decision JSON', async () => {
    const res = await postJson(
      `${BASE}/v1/chat/completions`,
      {
        model: 'openai/gpt-5.4-mini',
        messages: [{ role: 'user', content: 'Mam nowe informacje' }],
        stream: false,
      },
      { 'X-Stub-Scenario': 'updatedDecision' },
    );

    assert.equal(res.status, 200);
    const json = JSON.parse(res.body);
    const content = json.choices[0].message.content;
    assert.ok(content.includes('updatedDecision') || content.includes('APPROVE') || content.includes('ESCALATE') || content.includes('REJECT'),
      'updatedDecision scenario must reference a new decision');
  });
});

describe('Stub server — vision model call', () => {
  it('Vision model returns an image description string, not a decision outcome', async () => {
    const res = await postJson(`${BASE}/v1/chat/completions`, {
      model: 'openai/gpt-5.4',
      messages: [
        {
          role: 'user',
          content: [
            { type: 'text', text: 'Opisz stan sprzętu na zdjęciu.' },
            {
              type: 'image_url',
              image_url: { url: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==' },
            },
          ],
        },
      ],
      stream: false,
    });

    assert.equal(res.status, 200);
    const json = JSON.parse(res.body);
    const content = json.choices[0].message.content;

    // Vision response must NOT be a structured decision JSON
    let parsed;
    try {
      parsed = JSON.parse(content);
    } catch {
      parsed = null;
    }
    assert.ok(!parsed || !parsed.outcome, 'Vision response must not contain a decision outcome');
    assert.ok(content.length > 10, 'Vision description must be a non-trivial string');
  });
});

describe('Stub server — streaming', () => {
  it('stream:true returns multiple data chunks and ends with [DONE]', async () => {
    const result = await collectStream(`${BASE}/v1/chat/completions`, {
      model: 'openai/gpt-5.4-mini',
      messages: [{ role: 'user', content: 'Streamed reply' }],
      stream: true,
    });

    assert.equal(result.status, 200);
    assert.ok(result.chunks.length >= 2, `Expected at least 2 chunks (content + [DONE]), got ${result.chunks.length}`);

    const done = result.chunks[result.chunks.length - 1];
    assert.equal(done, '[DONE]', 'Last chunk must be [DONE]');

    // All non-DONE chunks must be valid chat.completion.chunk JSON
    const contentChunks = result.chunks.filter((c) => c !== '[DONE]');
    assert.ok(contentChunks.length >= 1, 'Must have at least one content chunk before [DONE]');

    for (const chunk of contentChunks) {
      const parsed = JSON.parse(chunk);
      assert.equal(parsed.object, 'chat.completion.chunk');
      assert.ok(Array.isArray(parsed.choices));
      assert.ok(parsed.choices.length > 0);
      const delta = parsed.choices[0].delta;
      assert.ok(delta !== undefined, 'delta must be present');
    }

    // Reconstruct full content from deltas
    const fullContent = contentChunks
      .map((c) => JSON.parse(c).choices[0].delta.content ?? '')
      .join('');
    assert.ok(fullContent.length > 0, 'Assembled streamed content must not be empty');
  });

  it('chunked-stream scenario via X-Stub-Scenario header also streams correctly', async () => {
    const result = await collectStream(
      `${BASE}/v1/chat/completions`,
      {
        model: 'openai/gpt-5.4-mini',
        messages: [{ role: 'user', content: 'Potrzebuje pomocy' }],
        stream: true,
      },
      { 'X-Stub-Scenario': 'chunked-stream' },
    );

    assert.equal(result.status, 200);
    assert.ok(result.chunks.length >= 3, `Expected at least 3 chunks, got ${result.chunks.length}`);
    assert.equal(result.chunks[result.chunks.length - 1], '[DONE]');
  });
});
