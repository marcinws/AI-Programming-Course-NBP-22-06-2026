/**
 * CaseService — REST + SSE client for P3.F1
 *
 * Wraps HttpClient for REST endpoints and @microsoft/fetch-event-source for
 * POST-based SSE streaming (native EventSource is GET-only).
 *
 * Reference: ADR-002 §3/§5/§6, ADR-001 API contract.
 */

import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  MetadataResponse,
  CreateCaseResponse,
  SessionResponse,
  CaseFormValues,
  Decision,
  ChatMessage,
  SseEvent,
} from './models';
import { fetchEventSource, EventSourceMessage } from '@microsoft/fetch-event-source';

/** Callback handlers supplied by the caller of sendMessage(). */
export interface SendMessageHandlers {
  /** Called once per SSE `token` event with the incremental delta string. */
  onToken(delta: string): void;
  /** Called once when the SSE `done` event arrives with the final message. */
  onDone(message: ChatMessage, updatedDecision?: Decision): void;
  /** Called when an SSE `error` event arrives with the code and message. */
  onError(code: string, message: string): void;
}

/**
 * Shape of the options object passed to fetchEventSource (or the test seam).
 * Mirrors the PostEventSourceInit from @microsoft/fetch-event-source but kept
 * minimal so the seam is easy to mock.
 */
export interface FetchEventSourceInit {
  method?: string;
  headers?: Record<string, string>;
  body?: string | null;
  onmessage?(ev: EventSourceMessage): void;
  onerror?(error: unknown): number | null | undefined | void;
  onclose?(): void;
  signal?: AbortSignal;
}

@Injectable({ providedIn: 'root' })
export class CaseService {
  private readonly http = inject(HttpClient);

  /**
   * Seam for unit tests: replace with a spy/fake to avoid real network calls.
   * In production this delegates to the real @microsoft/fetch-event-source.
   */
  _fetchEventSourceImpl = fetchEventSource as unknown as (
    url: string,
    opts: FetchEventSourceInit,
  ) => Promise<void>;

  // ---------------------------------------------------------------------------
  // GET /api/metadata
  // ---------------------------------------------------------------------------

  /**
   * Fetches form options and image constraints from the backend.
   * Response drives the caseType/equipmentCategory selectors and file validation.
   * Reference: ADR-002 §5, ADR-001 GET /api/metadata.
   */
  getMetadata(): Observable<MetadataResponse> {
    return this.http.get<MetadataResponse>('/api/metadata');
  }

  // ---------------------------------------------------------------------------
  // POST /api/cases
  // ---------------------------------------------------------------------------

  /**
   * Creates a new case by POSTing multipart/form-data to /api/cases.
   * On 201 returns CreateCaseResponse containing sessionId + initial decision.
   * On error propagates the HttpErrorResponse (intercepted by httpErrorInterceptor).
   *
   * Reference: ADR-002 §5, ADR-001 POST /api/cases.
   */
  createCase(formValues: CaseFormValues, imageFile: File): Observable<CreateCaseResponse> {
    const formData = new FormData();
    formData.append('caseType', formValues.caseType);
    formData.append('equipmentCategory', formValues.equipmentCategory);
    formData.append('modelName', formValues.modelName);
    formData.append('purchaseDate', formValues.purchaseDate);
    if (formValues.reason !== undefined && formValues.reason !== '') {
      formData.append('reason', formValues.reason);
    }
    formData.append('image', imageFile);

    return this.http.post<CreateCaseResponse>('/api/cases', formData);
  }

  // ---------------------------------------------------------------------------
  // GET /api/cases/{id}
  // ---------------------------------------------------------------------------

  /**
   * Fetches a full session by ID for rehydrating the chat on reload.
   * On 404 propagates the HttpErrorResponse.
   *
   * Reference: ADR-002 §5, ADR-001 GET /api/cases/{id}.
   */
  getCase(sessionId: string): Observable<SessionResponse> {
    return this.http.get<SessionResponse>(`/api/cases/${sessionId}`);
  }

  // ---------------------------------------------------------------------------
  // POST /api/cases/{id}/messages — SSE streaming
  // ---------------------------------------------------------------------------

  /**
   * Opens a POST SSE stream to /api/cases/{id}/messages and calls the
   * provided handlers as events arrive.
   *
   * Uses @microsoft/fetch-event-source (via _fetchEventSourceImpl seam) because
   * the native EventSource API only supports GET requests.
   *
   * Reference: ADR-002 §6 "Consume SSE over POST via a fetch-based stream".
   */
  async sendMessage(
    sessionId: string,
    content: string,
    handlers: SendMessageHandlers,
    signal?: AbortSignal,
  ): Promise<void> {
    const url = `/api/cases/${sessionId}/messages`;

    await this._fetchEventSourceImpl(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
      },
      body: JSON.stringify({ content }),
      signal,
      onmessage: (event: EventSourceMessage) => {
        let parsed: SseEvent;
        try {
          parsed = JSON.parse(event.data) as SseEvent;
        } catch {
          // Malformed event — skip silently
          return;
        }

        switch (parsed.type) {
          case 'token':
            handlers.onToken(parsed.delta);
            break;
          case 'done':
            handlers.onDone(parsed.message, parsed.updatedDecision);
            break;
          case 'error':
            handlers.onError(parsed.code, parsed.message);
            break;
        }
      },
      onerror: (err: unknown) => {
        // Rethrow to stop reconnection attempts; caller handles the error
        throw err;
      },
    });
  }
}
