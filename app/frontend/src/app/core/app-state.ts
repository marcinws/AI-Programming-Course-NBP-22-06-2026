/**
 * AppStateService — signal-based store for P3.F1
 *
 * Holds the current session, decision, message list, and per-action pending state.
 * Reference: ADR-002 §4 — "signals + a small store service (no NgRx)".
 *
 * All mutable state is exposed via signals; mutations are applied via setter
 * methods so the component never writes to private signal internals.
 */

import { Injectable, signal, computed } from '@angular/core';
import {
  PendingState,
  DisplayMessage,
  Decision,
  CreateCaseResponse,
  SessionResponse,
} from './models';

@Injectable({ providedIn: 'root' })
export class AppStateService {
  // ---------------------------------------------------------------------------
  // Private writable signals
  // ---------------------------------------------------------------------------

  private readonly _sessionId = signal<string | null>(null);
  private readonly _decision = signal<Decision | null>(null);
  private readonly _messages = signal<DisplayMessage[]>([]);
  private readonly _pendingState = signal<PendingState>('IDLE');

  // ---------------------------------------------------------------------------
  // Public read-only signal accessors
  // ---------------------------------------------------------------------------

  /** Current session ID, or null before first case is created. */
  readonly sessionId = computed(() => this._sessionId());

  /** Current AI decision (initial + updates from SSE done events). */
  readonly decision = computed(() => this._decision());

  /** Ordered list of display messages (user + assistant bubbles). */
  readonly messages = computed(() => this._messages());

  /** Async state driving spinner/disabled states and error banners. */
  readonly pendingState = computed(() => this._pendingState());

  // ---------------------------------------------------------------------------
  // Setters — called by form component (on createCase success) and chat
  // ---------------------------------------------------------------------------

  setSessionId(id: string | null): void {
    this._sessionId.set(id);
  }

  setDecision(decision: Decision | null): void {
    this._decision.set(decision);
  }

  setMessages(messages: DisplayMessage[]): void {
    this._messages.set(messages);
  }

  setPendingState(state: PendingState): void {
    this._pendingState.set(state);
  }

  // ---------------------------------------------------------------------------
  // Higher-level helpers used by ChatComponent
  // ---------------------------------------------------------------------------

  /** Appends a single message to the current list. */
  appendMessage(msg: DisplayMessage): void {
    this._messages.update((list) => [...list, msg]);
  }

  /**
   * Updates the last message in the list (used during SSE streaming to grow
   * the assistant bubble token-by-token).
   */
  updateLastMessage(updater: (m: DisplayMessage) => DisplayMessage): void {
    this._messages.update((list) => {
      if (list.length === 0) return list;
      const updated = [...list];
      updated[updated.length - 1] = updater(updated[updated.length - 1]);
      return updated;
    });
  }

  // ---------------------------------------------------------------------------
  // Hydrate from API responses
  // ---------------------------------------------------------------------------

  /**
   * Bootstraps state from POST /api/cases 201 response.
   * Called by IntakeFormComponent after successful case creation.
   */
  hydrateFromCreate(response: CreateCaseResponse): void {
    this._sessionId.set(response.sessionId);
    this._decision.set(response.decision);
    // The first message is the formatted decision bubble
    const firstMsg: DisplayMessage = {
      role: 'ASSISTANT',
      content: response.decision.firstMessageMarkdown ?? '',
      createdAt: new Date().toISOString(),
      isDecision: true,
      isStreaming: false,
    };
    this._messages.set([firstMsg]);
    this._pendingState.set('IDLE');
  }

  /**
   * Hydrates state from GET /api/cases/{id} on reload/navigation.
   * Called by ChatComponent on init if the store is empty.
   */
  hydrateFromSession(session: SessionResponse): void {
    this._sessionId.set(session.sessionId);
    this._decision.set(session.decision);

    const displayMessages: DisplayMessage[] = session.messages.map((msg, idx) => ({
      role: msg.role,
      content: msg.content,
      createdAt: msg.createdAt,
      // First ASSISTANT message is treated as the decision bubble
      isDecision: idx === 0 && msg.role === 'ASSISTANT',
      isStreaming: false,
    }));
    this._messages.set(displayMessages);
    this._pendingState.set('IDLE');
  }

  /** Resets all state — called on "Start new case". */
  reset(): void {
    this._sessionId.set(null);
    this._decision.set(null);
    this._messages.set([]);
    this._pendingState.set('IDLE');
  }
}
