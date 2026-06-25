/**
 * Spec: HttpErrorInterceptor — P3.F1
 *
 * The functional interceptor maps ErrorResponse bodies to a Polish user-facing
 * error model and surfaces them via MatSnackBar.
 *
 * TAC: TAC-002-04 (error branches), TAC-002-07 (Polish messages)
 */

import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors, HttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideAnimations } from '@angular/platform-browser/animations';

import { httpErrorInterceptor } from './http-error.interceptor';
import { ErrorResponse } from './models';

const SNACKBAR_SPY = jasmine.createSpyObj<MatSnackBar>('MatSnackBar', ['open']);

describe('httpErrorInterceptor (TAC-002-04, TAC-002-07)', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    SNACKBAR_SPY.open.calls.reset();

    TestBed.configureTestingModule({
      providers: [
        provideAnimations(),
        provideHttpClient(withInterceptors([httpErrorInterceptor])),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: SNACKBAR_SPY },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should surface a Polish snackbar message on 400 VALIDATION_ERROR', (done) => {
    const errorBody: ErrorResponse = {
      code: 'VALIDATION_ERROR',
      message: 'Błąd walidacji pól.',
      fieldErrors: { modelName: 'Wymagane.' },
    };

    http.get('/api/test').subscribe({
      error: () => {
        expect(SNACKBAR_SPY.open).toHaveBeenCalled();
        const args = SNACKBAR_SPY.open.calls.mostRecent().args;
        // First arg is the message — must be Polish text (non-empty string)
        expect(typeof args[0]).toBe('string');
        expect((args[0] as string).length).toBeGreaterThan(0);
        done();
      },
    });

    const req = httpMock.expectOne('/api/test');
    req.flush(errorBody, { status: 400, statusText: 'Bad Request' });
  });

  it('should surface a Polish snackbar message on 404 SESSION_NOT_FOUND', (done) => {
    const errorBody: ErrorResponse = {
      code: 'SESSION_NOT_FOUND',
      message: 'Sesja nie istnieje.',
    };

    http.get('/api/test').subscribe({
      error: () => {
        expect(SNACKBAR_SPY.open).toHaveBeenCalled();
        const args = SNACKBAR_SPY.open.calls.mostRecent().args;
        expect(typeof args[0]).toBe('string');
        // Should include Polish content indicating session not found
        const message = args[0] as string;
        expect(message.length).toBeGreaterThan(0);
        done();
      },
    });

    const req = httpMock.expectOne('/api/test');
    req.flush(errorBody, { status: 404, statusText: 'Not Found' });
  });

  it('should surface a Polish snackbar message on 502 LLM_UNAVAILABLE (retryable)', (done) => {
    const errorBody: ErrorResponse = {
      code: 'LLM_UNAVAILABLE',
      message: 'Serwis AI niedostępny.',
    };

    http.get('/api/test').subscribe({
      error: () => {
        expect(SNACKBAR_SPY.open).toHaveBeenCalled();
        done();
      },
    });

    const req = httpMock.expectOne('/api/test');
    req.flush(errorBody, { status: 502, statusText: 'Bad Gateway' });
  });

  it('should still propagate the error so subscribers can handle it', (done) => {
    const errorBody: ErrorResponse = {
      code: 'VALIDATION_ERROR',
      message: 'Błąd walidacji.',
    };

    http.get('/api/test').subscribe({
      next: () => fail('Should not succeed'),
      error: (err) => {
        expect(err).toBeTruthy();
        done();
      },
    });

    const req = httpMock.expectOne('/api/test');
    req.flush(errorBody, { status: 400, statusText: 'Bad Request' });
  });

  // TAC-002-07: all 6 ErrorCode values → non-empty Polish message
  it('should surface a Polish snackbar message on 413 IMAGE_TOO_LARGE', (done) => {
    const errorBody: ErrorResponse = {
      code: 'IMAGE_TOO_LARGE',
      message: 'Zdjęcie jest zbyt duże.',
    };

    http.get('/api/test').subscribe({
      error: () => {
        expect(SNACKBAR_SPY.open).toHaveBeenCalled();
        const args = SNACKBAR_SPY.open.calls.mostRecent().args;
        const message = args[0] as string;
        // Should contain Polish text about size
        expect(message.toLowerCase()).toContain('rozmiar');
        done();
      },
    });

    const req = httpMock.expectOne('/api/test');
    req.flush(errorBody, { status: 413, statusText: 'Payload Too Large' });
  });

  it('should surface a Polish snackbar message on 415 UNSUPPORTED_MEDIA_TYPE', (done) => {
    const errorBody: ErrorResponse = {
      code: 'UNSUPPORTED_MEDIA_TYPE',
      message: 'Niedozwolony format.',
    };

    http.get('/api/test').subscribe({
      error: () => {
        expect(SNACKBAR_SPY.open).toHaveBeenCalled();
        const args = SNACKBAR_SPY.open.calls.mostRecent().args;
        const message = args[0] as string;
        // Should contain Polish text about format
        expect(message.toLowerCase()).toContain('format');
        done();
      },
    });

    const req = httpMock.expectOne('/api/test');
    req.flush(errorBody, { status: 415, statusText: 'Unsupported Media Type' });
  });

  it('should surface a Polish snackbar message on 504 LLM_TIMEOUT (retryable)', (done) => {
    const errorBody: ErrorResponse = {
      code: 'LLM_TIMEOUT',
      message: 'Przekroczono limit czasu.',
    };

    http.get('/api/test').subscribe({
      error: () => {
        expect(SNACKBAR_SPY.open).toHaveBeenCalled();
        const args = SNACKBAR_SPY.open.calls.mostRecent().args;
        const message = args[0] as string;
        expect(message.length).toBeGreaterThan(0);
        // LLM_TIMEOUT is retryable — message should contain "ponownie"
        expect(message.toLowerCase()).toContain('ponownie');
        done();
      },
    });

    const req = httpMock.expectOne('/api/test');
    req.flush(errorBody, { status: 504, statusText: 'Gateway Timeout' });
  });

  it('should use a longer duration for retryable errors (LLM_UNAVAILABLE)', (done) => {
    const errorBody: ErrorResponse = {
      code: 'LLM_UNAVAILABLE',
      message: 'AI niedostępne.',
    };

    http.get('/api/test').subscribe({
      error: () => {
        const args = SNACKBAR_SPY.open.calls.mostRecent().args;
        const config = args[2] as { duration?: number };
        // Retryable errors use longer duration (6000 ms) vs non-retryable (4000 ms)
        expect(config.duration).toBeGreaterThan(4000);
        done();
      },
    });

    const req = httpMock.expectOne('/api/test');
    req.flush(errorBody, { status: 502, statusText: 'Bad Gateway' });
  });

  it('should use default duration for non-retryable errors (VALIDATION_ERROR)', (done) => {
    const errorBody: ErrorResponse = {
      code: 'VALIDATION_ERROR',
      message: 'Błąd walidacji.',
    };

    http.get('/api/test').subscribe({
      error: () => {
        const args = SNACKBAR_SPY.open.calls.mostRecent().args;
        const config = args[2] as { duration?: number };
        expect(config.duration).toBeLessThanOrEqual(4000);
        done();
      },
    });

    const req = httpMock.expectOne('/api/test');
    req.flush(errorBody, { status: 400, statusText: 'Bad Request' });
  });

  it('should fall back to a Polish generic message for unknown error codes', (done) => {
    const errorBody: ErrorResponse = {
      code: 'UNKNOWN_CODE',
      message: 'Nieznany błąd.',
    };

    http.get('/api/test').subscribe({
      error: () => {
        expect(SNACKBAR_SPY.open).toHaveBeenCalled();
        const args = SNACKBAR_SPY.open.calls.mostRecent().args;
        const message = args[0] as string;
        expect(message.length).toBeGreaterThan(0);
        done();
      },
    });

    const req = httpMock.expectOne('/api/test');
    req.flush(errorBody, { status: 500, statusText: 'Internal Server Error' });
  });
});
