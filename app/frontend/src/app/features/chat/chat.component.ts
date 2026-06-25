/**
 * ChatComponent — P3.F1 skeleton
 *
 * Message bubbles (user/assistant), decision summary in mat-expansion-panel,
 * composer (textarea + send), typing indicator, Markdown via ngx-markdown.
 * Wired to AppStateService (signals) and CaseService (REST/SSE).
 *
 * The live send/streaming turn + navigation guard are implemented in P4.F2.
 * Here we build the skeleton + rendering + service, tested against mocks.
 *
 * AC-18/19/25 · TAC-002-05/06/07
 */

import {
  Component,
  inject,
  computed,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';

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
export class ChatComponent {
  protected readonly appState = inject(AppStateService);
  protected readonly caseService = inject(CaseService);
  protected readonly route = inject(ActivatedRoute);

  /** Composer textarea value — two-way bound via ngModel. */
  composerText = '';

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

  /** Send handler — full streaming implementation in P4.F2. */
  onSend(): void {
    if (this.isSendDisabled) return;
    // P4.F2: append user bubble, open SSE stream, grow assistant bubble, finalize.
    console.info('[ChatComponent] onSend — P4.F2 pending');
  }

  /** Keyboard: Ctrl+Enter / Cmd+Enter submits the composer. */
  onComposerKeydown(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
      this.onSend();
    }
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
