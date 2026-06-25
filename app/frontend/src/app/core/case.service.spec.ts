/**
 * Spec: CaseService — P3.F1
 *
 * Coverage:
 * - createCase() POSTs multipart to /api/cases → CreateCaseResponse (success + 400 error branch)
 * - getCase()    GETs /api/cases/{id}            → SessionResponse (success + 404 error branch)
 * - sendMessage() consumes SSE via fetch-event-source: token events append deltas,
 *   done event finalizes, error event surfaces an error.
 *
 * TAC: TAC-002-04, TAC-002-05
 */

import { TestBed } from '@angular/core/testing';
import {
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';

import { CaseService } from './case.service';
import {
  CreateCaseResponse,
  SessionResponse,
  ErrorResponse,
  CaseFormValues,
  ChatMessage,
  Decision,
  SseEvent,
} from './models';

// ---------------------------------------------------------------------------
// Mock @microsoft/fetch-event-source
// The module is mocked via a jasmine spy so no real network is hit.
// ---------------------------------------------------------------------------

// We need to spy on the module import. Angular test environment uses CommonJS
// at runtime, so we patch the module export directly via a module-level spy object.
// The service imports `fetchEventSource` from '@microsoft/fetch-event-source';
// we intercept it by providing a jasmine spy in the test and injecting via DI pattern.
// Since the service uses a module-level import we expose a seam: the service
// delegates to a protected method `_fetchEventSource` that tests can replace.

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const MOCK_FORM: CaseFormValues = {
  caseType: 'COMPLAINT',
  equipmentCategory: 'SMARTPHONE',
  modelName: 'iPhone 15',
  purchaseDate: '2024-01-15',
  reason: 'Wadliwy produkt',
};

const MOCK_IMAGE_FILE = new File([new Uint8Array(1024)], 'photo.jpg', {
  type: 'image/jpeg',
});

const MOCK_CREATE_RESPONSE: CreateCaseResponse = {
  sessionId: 'sess-001',
  decision: {
    outcome: 'APPROVE',
    justification: 'Towar w gwarancji.',
    nextSteps: 'Wyślij sprzęt do serwisu.',
    firstMessageMarkdown: '## Decyzja\nZatwierdzono.',
  },
  imageAnalysisSummary: 'Wyraźne uszkodzenie ekranu.',
};

const MOCK_SESSION_RESPONSE: SessionResponse = {
  sessionId: 'sess-001',
  form: MOCK_FORM,
  imageAnalysisSummary: 'Wyraźne uszkodzenie ekranu.',
  decision: {
    outcome: 'APPROVE',
    justification: 'Towar w gwarancji.',
    nextSteps: 'Wyślij sprzęt do serwisu.',
  },
  messages: [
    {
      role: 'ASSISTANT',
      content: '## Decyzja\nZatwierdzono.',
      createdAt: '2026-06-25T10:00:00Z',
    },
  ],
};

const MOCK_ERROR_400: ErrorResponse = {
  code: 'VALIDATION_ERROR',
  message: 'Błąd walidacji.',
  fieldErrors: { modelName: 'Pole wymagane.' },
};

const MOCK_ERROR_404: ErrorResponse = {
  code: 'SESSION_NOT_FOUND',
  message: 'Sesja nie istnieje.',
};

// ---------------------------------------------------------------------------
// Spec
// ---------------------------------------------------------------------------

describe('CaseService — REST (TAC-002-04)', () => {
  let service: CaseService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        CaseService,
        provideHttpClient(withInterceptors([])),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(CaseService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  // -------------------------------------------------------------------------
  // createCase — success (201)
  // -------------------------------------------------------------------------

  describe('createCase()', () => {
    it('should POST to /api/cases with multipart FormData and return CreateCaseResponse', () => {
      let result: CreateCaseResponse | undefined;

      service.createCase(MOCK_FORM, MOCK_IMAGE_FILE).subscribe((res) => {
        result = res;
      });

      const req = httpMock.expectOne('/api/cases');
      expect(req.request.method).toBe('POST');
      // Body must be FormData (multipart)
      expect(req.request.body instanceof FormData).toBeTrue();

      req.flush(MOCK_CREATE_RESPONSE, { status: 201, statusText: 'Created' });

      expect(result).toEqual(MOCK_CREATE_RESPONSE);
    });

    it('should include form fields in the FormData payload', () => {
      service.createCase(MOCK_FORM, MOCK_IMAGE_FILE).subscribe();

      const req = httpMock.expectOne('/api/cases');
      const body = req.request.body as FormData;
      expect(body.get('caseType')).toBe('COMPLAINT');
      expect(body.get('equipmentCategory')).toBe('SMARTPHONE');
      expect(body.get('modelName')).toBe('iPhone 15');
      expect(body.get('purchaseDate')).toBe('2024-01-15');
      expect(body.get('reason')).toBe('Wadliwy produkt');

      req.flush(MOCK_CREATE_RESPONSE, { status: 201, statusText: 'Created' });
    });

    it('should include the image file in the FormData payload', () => {
      service.createCase(MOCK_FORM, MOCK_IMAGE_FILE).subscribe();

      const req = httpMock.expectOne('/api/cases');
      const body = req.request.body as FormData;
      const imageEntry = body.get('image');
      expect(imageEntry).toBeTruthy();
      expect(imageEntry instanceof File).toBeTrue();

      req.flush(MOCK_CREATE_RESPONSE, { status: 201, statusText: 'Created' });
    });

    it('should propagate error on 400 (bad request / validation)', (done) => {
      service.createCase(MOCK_FORM, MOCK_IMAGE_FILE).subscribe({
        next: () => fail('Expected error, got success'),
        error: (err) => {
          // The raw HttpErrorResponse body should be accessible
          expect(err).toBeTruthy();
          done();
        },
      });

      const req = httpMock.expectOne('/api/cases');
      req.flush(MOCK_ERROR_400, { status: 400, statusText: 'Bad Request' });
    });
  });

  // -------------------------------------------------------------------------
  // getCase — success + 404
  // -------------------------------------------------------------------------

  describe('getCase()', () => {
    it('should GET /api/cases/{id} and return SessionResponse on success', () => {
      let result: SessionResponse | undefined;

      service.getCase('sess-001').subscribe((res) => {
        result = res;
      });

      const req = httpMock.expectOne('/api/cases/sess-001');
      expect(req.request.method).toBe('GET');
      req.flush(MOCK_SESSION_RESPONSE, { status: 200, statusText: 'OK' });

      expect(result).toEqual(MOCK_SESSION_RESPONSE);
    });

    it('should propagate error on 404 (session not found)', (done) => {
      service.getCase('nonexistent-id').subscribe({
        next: () => fail('Expected error, got success'),
        error: (err) => {
          expect(err).toBeTruthy();
          done();
        },
      });

      const req = httpMock.expectOne('/api/cases/nonexistent-id');
      req.flush(MOCK_ERROR_404, { status: 404, statusText: 'Not Found' });
    });
  });
});

// ---------------------------------------------------------------------------
// sendMessage — SSE via mocked _fetchEventSource seam
// ---------------------------------------------------------------------------

describe('CaseService — SSE sendMessage (TAC-002-05)', () => {
  let service: CaseService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        CaseService,
        provideHttpClient(withInterceptors([])),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(CaseService);
  });

  afterEach(() => {
    // No HTTP requests expected for SSE (uses fetch-event-source directly)
    TestBed.inject(HttpTestingController).verify();
  });

  /**
   * Helper that replaces the service's protected _fetchEventSource seam
   * with a fake that synchronously fires the provided sequence of SSE events.
   */
  function mockSseEvents(events: SseEvent[]): void {
    service._fetchEventSourceImpl = async (_url: string, opts: FetchEventSourceInitForTest) => {
      for (const ev of events) {
        const data = JSON.stringify(ev);
        if (opts.onmessage) {
          opts.onmessage({ event: ev.type, data, id: '', retry: undefined });
        }
      }
    };
  }

  it('should append deltas incrementally on token events', async () => {
    const deltas: string[] = [];
    let finalMessage: ChatMessage | undefined;
    let errorSeen = false;

    mockSseEvents([
      { type: 'token', delta: 'Cześć' },
      { type: 'token', delta: ', jak' },
      { type: 'token', delta: ' mogę pomóc?' },
      {
        type: 'done',
        message: { role: 'ASSISTANT', content: 'Cześć, jak mogę pomóc?', createdAt: '2026-06-25T10:00:00Z' },
      },
    ]);

    await service.sendMessage('sess-001', 'Witaj', {
      onToken: (delta) => deltas.push(delta),
      onDone: (msg, updatedDecision) => {
        finalMessage = msg;
        void updatedDecision; // may be undefined
      },
      onError: () => { errorSeen = true; },
    });

    expect(deltas).toEqual(['Cześć', ', jak', ' mogę pomóc?']);
    expect(finalMessage?.content).toBe('Cześć, jak mogę pomóc?');
    expect(errorSeen).toBeFalse();
  });

  it('should surface an error event via onError callback', async () => {
    let errorCode: string | undefined;
    let errorMsg: string | undefined;

    mockSseEvents([
      { type: 'error', code: 'LLM_TIMEOUT', message: 'Upłynął limit czasu.' },
    ]);

    await service.sendMessage('sess-001', 'Pytanie', {
      onToken: () => {},
      onDone: () => {},
      onError: (code, message) => {
        errorCode = code;
        errorMsg = message;
      },
    });

    expect(errorCode).toBe('LLM_TIMEOUT');
    expect(errorMsg).toBe('Upłynął limit czasu.');
  });

  it('should finalize message with updatedDecision when done event carries one', async () => {
    let capturedDecision: Decision | undefined;

    const updatedDecision: Decision = {
      outcome: 'ESCALATE',
      justification: 'Wymaga weryfikacji.',
      nextSteps: 'Skontaktuj się z menedżerem.',
    };

    mockSseEvents([
      {
        type: 'done',
        message: { role: 'ASSISTANT', content: 'Eskalacja.', createdAt: '2026-06-25T10:00:00Z' },
        updatedDecision,
      },
    ]);

    await service.sendMessage('sess-001', 'Pytanie', {
      onToken: () => {},
      onDone: (_msg, dec) => { capturedDecision = dec; },
      onError: () => {},
    });

    expect(capturedDecision).toEqual(updatedDecision);
  });

  it('should POST to /api/cases/{id}/messages with JSON content body', async () => {
    let capturedUrl: string | undefined;
    let capturedBody: string | undefined;

    service._fetchEventSourceImpl = async (url: string, opts: FetchEventSourceInitForTest) => {
      capturedUrl = url;
      capturedBody = opts.body as string;
      // Fire done immediately so the promise resolves
      if (opts.onmessage) {
        opts.onmessage({
          event: 'done',
          data: JSON.stringify({
            type: 'done',
            message: { role: 'ASSISTANT', content: 'OK', createdAt: '2026-06-25T10:00:00Z' },
          }),
          id: '',
          retry: undefined,
        });
      }
    };

    await service.sendMessage('sess-001', 'Moje pytanie', {
      onToken: () => {},
      onDone: () => {},
      onError: () => {},
    });

    expect(capturedUrl).toBe('/api/cases/sess-001/messages');
    const parsedBody = JSON.parse(capturedBody!);
    expect(parsedBody.content).toBe('Moje pytanie');
  });
});

// ---------------------------------------------------------------------------
// Type helper for the mock seam — matches the shape the service uses
// ---------------------------------------------------------------------------

interface FetchEventSourceInitForTest {
  method?: string;
  headers?: Record<string, string>;
  body?: string | null;
  onmessage?(ev: { event: string; data: string; id: string; retry: number | undefined }): void;
  onerror?(error: unknown): void;
  onclose?(): void;
  signal?: AbortSignal;
}
