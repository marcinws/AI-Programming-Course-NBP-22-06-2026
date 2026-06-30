/**
 * httpErrorInterceptor — functional HTTP error interceptor for P3.F1
 *
 * Maps ErrorResponse.code values to user-facing Polish messages and surfaces
 * them via MatSnackBar. The error is re-thrown so subscribers can still handle
 * it (e.g. to display field-level errors or retain form state).
 *
 * Reference: ADR-002 §3 "http-error.interceptor — maps ErrorResponse.code →
 * Polish message + retryable flag; surfaces via MatSnackBar".
 *
 * TAC-002-07: All messages are in Polish.
 */

import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ErrorCode, ErrorResponse } from './models';

/** Maps an error code to a user-facing Polish message. */
function toPolishMessage(code: ErrorCode | string, defaultMsg: string): string {
  switch (code) {
    case 'VALIDATION_ERROR':
      return 'Błąd walidacji danych. Sprawdź poprawność wypełnionych pól.';
    case 'SESSION_NOT_FOUND':
      return 'Sesja wygasła lub nie istnieje. Rozpocznij nowe zgłoszenie.';
    case 'IMAGE_TOO_LARGE':
      return 'Przesłane zdjęcie jest zbyt duże. Maksymalny rozmiar to 10 MB.';
    case 'UNSUPPORTED_MEDIA_TYPE':
      return 'Niedozwolony format pliku. Akceptowane: JPEG, PNG, WebP.';
    case 'LLM_UNAVAILABLE':
      return 'Serwis AI jest chwilowo niedostępny. Spróbuj ponownie za chwilę.';
    case 'LLM_TIMEOUT':
      return 'Przekroczono limit czasu odpowiedzi AI. Spróbuj ponownie.';
    default:
      return defaultMsg || 'Wystąpił nieoczekiwany błąd. Spróbuj ponownie.';
  }
}

/** Duration in milliseconds to show retryable error snackbars. */
const RETRYABLE_DURATION_MS = 6000;
/** Duration in milliseconds for non-retryable errors. */
const DEFAULT_DURATION_MS = 4000;

const RETRYABLE_CODES: (ErrorCode | string)[] = ['LLM_UNAVAILABLE', 'LLM_TIMEOUT'];

export const httpErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const snackBar = inject(MatSnackBar);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // Only handle known 4xx/5xx responses with an ErrorResponse body
      if (error instanceof HttpErrorResponse) {
        const body = error.error as ErrorResponse | null;
        const code = body?.code ?? 'UNKNOWN';
        const defaultMsg = body?.message ?? '';
        const message = toPolishMessage(code, defaultMsg);
        const isRetryable = RETRYABLE_CODES.includes(code);
        const duration = isRetryable ? RETRYABLE_DURATION_MS : DEFAULT_DURATION_MS;

        snackBar.open(message, 'OK', {
          duration,
          panelClass: isRetryable ? 'snack-retryable' : 'snack-error',
          verticalPosition: 'bottom',
          horizontalPosition: 'center',
        });
      }

      return throwError(() => error);
    }),
  );
};
