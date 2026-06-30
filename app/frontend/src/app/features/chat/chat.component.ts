/**
 * ChatComponent — P3.F1 skeleton + P4.F2 streaming turn
 *
 * Message bubbles (user/assistant), decision summary in mat-expansion-panel,
 * composer (textarea + send), typing indicator, Markdown via ngx-markdown.
 *
 * P4.F2: onSend() → CaseService.sendMessage() → SSE handlers update AppStateService.
 * Rehydrate-on-empty: on init, if messages are empty, calls getCase(id) to rehydrate.
 * Session expired: 404 on rehydrate or SESSION_NOT_FOUND SSE error → sessionExpired.
 * "Start new case": resets state + navigates to /.
 *
 * AC-18/19/20/21/22/24/25 · TAC-002-04/05/06/07
 */

import {
  Component,
  inject,
  computed,
  signal,
  OnInit,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

// Angular Material
import { MatCardModule } from '@angular/material/card';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatListModule } from '@angular/material/list';
import { MatDividerModule } from '@angular/material/divider';

// ngx-markdown
import { MarkdownComponent } from 'ngx-markdown';

import { AppStateService } from '../../core/app-state';
import { CaseService } from '../../core/case.service';
import { DisplayMessage } from '../../core/models';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatExpansionModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatListModule,
    MatDividerModule,
    MarkdownComponent,
  ],
  templateUrl: './chat.component.html',
  styleUrl: './chat.component.scss',
})
export class ChatComponent implements OnInit {
  protected readonly appState = inject(AppStateService);
  protected readonly caseService = inject(CaseService);
  protected readonly route = inject(ActivatedRoute);
  protected readonly router = inject(Router);

  /** Composer textarea value — two-way bound via ngModel. */
  composerText = '';

  // -------------------------------------------------------------------------
  // P4.F2 signals
  // -------------------------------------------------------------------------

  /**
   * Non-null when an SSE error or network error arrives during streaming.
   * Cleared on the next send attempt.
   */
  readonly sseError = signal<string | null>(null);

  /**
   * True when a 404/SESSION_NOT_FOUND indicates the session has expired.
   * Shows the "Sesja wygasła" UI with a "rozpocznij nową sprawę" action.
   */
  readonly sessionExpired = signal<boolean>(false);

  /**
   * Test seam: when set, overrides the messages from the store.
   * Used in unit tests to inject message lists with XSS payloads.
   * Setting this signal triggers change detection in computed().
   */
  private readonly _testMessages = signal<DisplayMessage[] | null>(null);

  /** Public setter for test seam — accessed by unit tests via public property. */
  set testMessages(value: DisplayMessage[] | null) {
    this._testMessages.set(value);
  }

  /** Effective message list — test seam takes priority over the store. */
  readonly effectiveMessages = computed<DisplayMessage[]>(() => {
    const override = this._testMessages();
    if (override !== null) return override;
    return this.appState.messages();
  });

  /** True while the last assistant message is streaming. */
  readonly isStreaming = computed(
    () => this.appState.pendingState() === 'STREAMING',
  );

  /** Send button disabled when streaming, empty composer, or submitting. */
  get isSendDisabled(): boolean {
    const state = this.appState.pendingState();
    return (
      state === 'STREAMING' ||
      state === 'SUBMITTING' ||
      this.composerText.trim().length === 0
    );
  }

  // -------------------------------------------------------------------------
  // Lifecycle
  // -------------------------------------------------------------------------

  ngOnInit(): void {
    // Rehydrate from backend if the store is empty (e.g. page refresh)
    const sessionId = this.route.snapshot.paramMap.get('sessionId') ?? '';
    if (this.appState.messages().length === 0 && sessionId) {
      this.caseService.getCase(sessionId).subscribe({
        next: (session) => {
          this.appState.hydrateFromSession(session);
        },
        error: (err: HttpErrorResponse) => {
          if (err.status === 404) {
            this.sessionExpired.set(true);
          }
        },
      });
    }
  }

  // -------------------------------------------------------------------------
  // Send handler — P4.F2 streaming turn
  // -------------------------------------------------------------------------

  onSend(): void {
    if (this.isSendDisabled) return;

    const sessionId = this.appState.sessionId() ?? this.route.snapshot.paramMap.get('sessionId') ?? '';
    const content = this.composerText.trim();

    // Clear previous SSE error
    this.sseError.set(null);

    // 1. Append USER bubble
    this.appState.appendMessage({
      role: 'USER',
      content,
      createdAt: new Date().toISOString(),
      isDecision: false,
      isStreaming: false,
    });

    // 2. Append empty ASSISTANT streaming bubble
    this.appState.appendMessage({
      role: 'ASSISTANT',
      content: '',
      createdAt: new Date().toISOString(),
      isDecision: false,
      isStreaming: true,
    });

    // 3. Clear composer and enter STREAMING state
    this.composerText = '';
    this.appState.setPendingState('STREAMING');

    // 4. Open SSE stream
    this.caseService
      .sendMessage(sessionId, content, {
        onToken: (delta: string) => {
          this.appState.updateLastMessage((msg) => ({
            ...msg,
            content: msg.content + delta,
          }));
        },
        onDone: (message, updatedDecision) => {
          // Finalize the streaming bubble with the canonical message content
          this.appState.updateLastMessage((msg) => ({
            ...msg,
            content: message.content,
            createdAt: message.createdAt,
            isStreaming: false,
          }));
          if (updatedDecision) {
            this.appState.setDecision(updatedDecision);
            // AC-21: when the updated decision carries Markdown, append an inline
            // decision bubble so the change is visible in the conversation thread.
            if (updatedDecision.firstMessageMarkdown) {
              this.appState.appendMessage({
                role: 'ASSISTANT',
                content: updatedDecision.firstMessageMarkdown,
                createdAt: message.createdAt,
                isDecision: true,
                isStreaming: false,
              });
            }
          }
          this.appState.setPendingState('IDLE');
        },
        onError: (code: string, message: string) => {
          if (code === 'SESSION_NOT_FOUND') {
            this.sessionExpired.set(true);
          }
          this.sseError.set(message);
          this.appState.setPendingState('ERROR');
        },
      })
      .catch((err: unknown) => {
        // Network-level error (fetch threw)
        const msg =
          err instanceof Error
            ? err.message
            : 'Błąd połączenia. Spróbuj ponownie.';
        this.sseError.set(msg);
        this.appState.setPendingState('ERROR');
      });
  }

  /** Keyboard: Ctrl+Enter / Cmd+Enter submits the composer. */
  onComposerKeydown(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
      this.onSend();
    }
  }

  // -------------------------------------------------------------------------
  // "Rozpocznij nową sprawę" — reset state + navigate to form
  // -------------------------------------------------------------------------

  startNewCase(): void {
    this.appState.reset();
    this.router.navigate(['/']);
  }

  /** Outcome label in Polish. */
  decisionOutcomeLabel(outcome: string): string {
    switch (outcome) {
      case 'APPROVE':
        return 'Zatwierdzona';
      case 'REJECT':
        return 'Odrzucona';
      case 'ESCALATE':
        return 'Do eskalacji';
      default:
        return outcome;
    }
  }
}
