/**
 * Canned response fixtures for the OpenAI-compatible stub server.
 *
 * All user-facing text is in Polish (per ADR-000 / AGENTS.md).
 *
 * Scenario selection (priority order):
 *   1. Request header  X-Stub-Scenario: <name>
 *   2. Body field      scenario: "<name>"  (anywhere in the JSON body)
 *   3. Model field     — if the model contains a vision keyword (e.g. "gpt-5.4" without "-mini",
 *                        or request has an image content part), use the "vision" scenario.
 *   4. Default: "approve"
 *
 * Available scenarios:
 *   approve         — structured APPROVE decision JSON (default)
 *   escalate        — structured ESCALATE decision JSON
 *   off-topic       — polite Polish prose redirect (no decision outcome)
 *   updatedDecision — prose reply embedding a new structured decision JSON
 *   5xx             — HTTP 500 error response
 *   timeout         — delays STUB_TIMEOUT_MS (default 10000) then responds 200
 *   chunked-stream  — (streaming only) chunked prose reply in Polish
 *   vision          — image description prose (no decision outcome)
 */

// ── Structured decision helpers ───────────────────────────────────────────────

export const APPROVE_DECISION = {
  outcome: 'APPROVE',
  justification:
    'Na podstawie przesłanego zdjęcia oraz opisu usterki sprzęt wykazuje wady fabryczne ' +
    'objęte gwarancją producenta. Reklamacja spełnia wszystkie wymagania procedury reklamacyjnej.',
  nextSteps:
    'Proszę dostarczyć sprzęt do serwisu w ciągu 14 dni. Pracownik przyjmie urządzenie i ' +
    'przekaże potwierdzenie przyjęcia reklamacji.',
  citedRules: ['§ 3 ust. 1 Procedury Reklamacyjnej', '§ 5 ust. 2 lit. a'],
};

export const ESCALATE_DECISION = {
  outcome: 'ESCALATE',
  justification:
    'Sprawa wymaga weryfikacji przez starszego specjalistę. Na zdjęciu widoczne są oznaki ' +
    'zewnętrznego uszkodzenia mechanicznego, którego ocena wykracza poza standardowy zakres ' +
    'uprawnień konsultanta. Decyzja zostaje eskalowana do przełożonego.',
  nextSteps:
    'Sprawa zostanie przekazana do starszego specjalisty serwisowego w ciągu 24 godzin ' +
    'roboczych. Otrzymają Państwo powiadomienie e-mail z informacją o dalszym postępowaniu.',
  citedRules: ['§ 7 ust. 3 Procedury Reklamacyjnej', '§ 2 ust. 4 — przypadki wymagające eskalacji'],
};

// ── Streaming chunks ──────────────────────────────────────────────────────────

/**
 * Splits a prose string into an array of small token-like pieces for streaming.
 * Splits on spaces so words arrive individually (realistic LLM streaming behaviour).
 */
function toChunks(text) {
  const words = text.split(' ');
  const chunks = [];
  for (let i = 0; i < words.length; i++) {
    chunks.push(i === words.length - 1 ? words[i] : words[i] + ' ');
  }
  return chunks;
}

export const STREAMING_TEXT =
  'Rozumiem Państwa pytanie. Analizuję dostępne informacje i przygotowuję odpowiedź. ' +
  'Jeżeli mają Państwo dodatkowe pytania dotyczące reklamacji, chętnie pomogę.';

export const CHUNKED_STREAM_TEXT =
  'Dziękuję za przesłane informacje. Na podstawie opisu oraz załączonego zdjęcia ' +
  'przygotowuję analizę Państwa sprawy. Proszę chwilę poczekać — pełna odpowiedź ' +
  'pojawi się za moment. Jeśli mają Państwo dodatkowe pytania, proszę śmiało pisać.';

// ── Scenario definitions ──────────────────────────────────────────────────────

/**
 * Each scenario is an object:
 *   { statusCode, content, streaming? }
 *
 * `content` is either:
 *   - a string  → used as-is for the assistant message
 *   - a function(requestBody) → called at request time to allow dynamic responses
 *
 * `streaming` (optional): array of string tokens to send as individual deltas.
 * When the request asks stream:true, the stub uses `streaming` if present, else
 * splits `content` into words.
 */
export const SCENARIOS = {
  /**
   * Default: structured APPROVE decision JSON (text model decision call).
   * The content is the JSON-stringified decision object.
   */
  approve: {
    statusCode: 200,
    content: JSON.stringify(APPROVE_DECISION),
    streaming: toChunks(JSON.stringify(APPROVE_DECISION)),
  },

  /**
   * Structured ESCALATE decision JSON.
   */
  escalate: {
    statusCode: 200,
    content: JSON.stringify(ESCALATE_DECISION),
    streaming: toChunks(JSON.stringify(ESCALATE_DECISION)),
  },

  /**
   * Off-topic: polite Polish redirect, no decision JSON.
   */
  'off-topic': {
    statusCode: 200,
    content:
      'Przepraszam, to pytanie wykracza poza zakres mojej pomocy w tym systemie. ' +
      'Jestem asystentem wsparcia technicznego i mogę pomóc wyłącznie w sprawach ' +
      'dotyczących reklamacji i zwrotów sprzętu. ' +
      'Czy mogę pomóc w kwestii zgłoszonej sprawy serwisowej?',
    streaming: toChunks(
      'Przepraszam, to pytanie wykracza poza zakres mojej pomocy w tym systemie. ' +
        'Jestem asystentem wsparcia technicznego i mogę pomóc wyłącznie w sprawach ' +
        'dotyczących reklamacji i zwrotów sprzętu. ' +
        'Czy mogę pomóc w kwestii zgłoszonej sprawy serwisowej?',
    ),
  },

  /**
   * Updated decision: prose reply containing a new embedded structured decision JSON.
   * Models the scenario where the backend detects material new info in the chat reply.
   */
  updatedDecision: {
    statusCode: 200,
    content: (function () {
      const newDecision = {
        outcome: 'APPROVE',
        justification:
          'Na podstawie nowych informacji przekazanych przez klienta (paragon zakupu i ' +
          'zdjęcie uszkodzenia) decyzja zostaje zmieniona na zatwierdzenie reklamacji.',
        nextSteps:
          'Proszę dostarczyć sprzęt do autoryzowanego serwisu z dowodem zakupu. ' +
          'Naprawa lub wymiana nastąpi w ciągu 14 dni roboczych.',
        citedRules: ['§ 3 ust. 2 Procedury Reklamacyjnej'],
      };
      return (
        'Dziękuję za przekazanie dodatkowych dokumentów. Na ich podstawie ' +
        'zaktualizowałem decyzję.\n\n' +
        'updatedDecision:' +
        JSON.stringify(newDecision)
      );
    })(),
    streaming: toChunks(
      'Dziękuję za przekazanie dodatkowych dokumentów. Na ich podstawie ' +
        'zaktualizowałem decyzję. updatedDecision:' +
        JSON.stringify({
          outcome: 'APPROVE',
          justification: 'Decyzja zmieniona na podstawie nowych dowodów.',
          nextSteps: 'Proszę dostarczyć sprzęt do serwisu.',
          citedRules: ['§ 3 ust. 2 Procedury Reklamacyjnej'],
        }),
    ),
  },

  /**
   * 5xx: returns HTTP 500 with an error body (simulates upstream LLM failure).
   */
  '5xx': {
    statusCode: 500,
    content: JSON.stringify({
      error: {
        message: 'Internal Server Error — upstream LLM unavailable (stub simulation)',
        type: 'server_error',
        code: 'llm_unavailable',
      },
    }),
  },

  /**
   * Timeout: the stub delays before responding.
   * Delay is controlled by the STUB_TIMEOUT_MS environment variable (default: 10000 ms).
   * Useful to test LLM_TIMEOUT handling in the backend.
   */
  timeout: {
    statusCode: 200,
    content:
      'Przepraszam za opóźnienie. Odpowiedź jest gotowa, jednak czas oczekiwania przekroczył ' +
      'dopuszczalny limit. Proszę spróbować ponownie.',
    delayMs: parseInt(process.env.STUB_TIMEOUT_MS ?? '10000', 10),
  },

  /**
   * Chunked-stream: a longer streaming prose reply, useful for asserting multi-chunk delivery.
   */
  'chunked-stream': {
    statusCode: 200,
    content: CHUNKED_STREAM_TEXT,
    streaming: toChunks(CHUNKED_STREAM_TEXT),
  },

  /**
   * Vision: returned when the request targets the vision model or contains an image content part.
   * Returns an image description string — never a decision outcome.
   */
  vision: {
    statusCode: 200,
    content:
      'Na zdjęciu widoczny jest laptop z uszkodzonym ekranem. W prawym górnym rogu ekranu ' +
      'widoczne jest pęknięcie matrycy o długości około 8 cm. Obudowa jest w dobrym stanie — ' +
      'brak zarysowań ani śladów użytkowania. Klawiatura wydaje się kompletna i nieuszkodzona. ' +
      'Ładowarka nie jest widoczna na zdjęciu. Ogólny stan sprzętu: dobry, z wyjątkiem uszkodzonej matrycy.',
    streaming: toChunks(
      'Na zdjęciu widoczny jest laptop z uszkodzonym ekranem. W prawym górnym rogu ekranu ' +
        'widoczne jest pęknięcie matrycy. Obudowa jest w dobrym stanie.',
    ),
  },
};

// ── Scenario resolution ───────────────────────────────────────────────────────

/**
 * Determines which scenario to use for a given request.
 *
 * @param {object} requestBody   Parsed JSON body from the POST request
 * @param {object} headers       Request headers (lower-cased keys)
 * @returns {string}             Scenario key from SCENARIOS
 */
export function resolveScenario(requestBody, headers) {
  // 1. Explicit header takes highest priority
  const headerScenario = headers['x-stub-scenario'];
  if (headerScenario && SCENARIOS[headerScenario]) {
    return headerScenario;
  }

  // 2. Explicit body field
  if (requestBody.scenario && SCENARIOS[requestBody.scenario]) {
    return requestBody.scenario;
  }

  // 3. Vision model detection:
  //    - model field contains a vision-model pattern (e.g. "gpt-5.4" but not "gpt-5.4-mini")
  //    - OR any message content contains an image_url part
  const model = (requestBody.model ?? '').toLowerCase();
  const isVisionModel =
    model.includes('vision') ||
    model.includes('gpt-4o') ||
    // A model that matches "gpt-5.4" but NOT "gpt-5.4-mini" → vision
    (/gpt-5\.4(?!-mini)/.test(model)) ||
    (model.includes('gpt-4') && !model.includes('mini'));

  const hasImagePart = (requestBody.messages ?? []).some((msg) => {
    if (!Array.isArray(msg.content)) return false;
    return msg.content.some((part) => part.type === 'image_url' || part.type === 'image');
  });

  if (isVisionModel || hasImagePart) {
    return 'vision';
  }

  // 4. Default
  return 'approve';
}
