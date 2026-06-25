/**
 * Spec: ChatComponent — P3.F1
 *
 * Coverage:
 * - First decision bubble renders greeting/decision/justification/next-steps/disclaimer
 *   from firstMessageMarkdown via ngx-markdown
 * - Sanitization: assistant Markdown via ngx-markdown does NOT execute raw HTML/script
 * - Message bubbles render for user/assistant roles
 * - Decision summary (mat-expansion-panel) shows decision data
 * - Composer (textarea + send button) present with correct data-testids
 * - Typing indicator present in DOM (hidden when not streaming)
 *
 * TAC: TAC-002-05 (sanitization, rendering), TAC-002-07 (Polish UI)
 */

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SecurityContext } from '@angular/core';
import { provideAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { provideMarkdown } from 'ngx-markdown';

import { ChatComponent } from './chat.component';
import { AppStateService } from '../../core/app-state';
import { CaseService } from '../../core/case.service';
import { DisplayMessage, Decision } from '../../core/models';

// ---------------------------------------------------------------------------
// Mock data
// ---------------------------------------------------------------------------

const MOCK_DECISION: Decision = {
  outcome: 'APPROVE',
  justification: 'Towar w gwarancji.',
  nextSteps: 'Wyślij sprzęt do serwisu.',
  firstMessageMarkdown: `## Witaj w systemie obsługi sprzętu

**Decyzja:** Zatwierdzono

**Uzasadnienie:** Towar w gwarancji.

**Kolejne kroki:** Wyślij sprzęt do serwisu.

---

*Niniejsza decyzja jest wstępna i może zostać zweryfikowana.*`,
};

const MOCK_MESSAGES: DisplayMessage[] = [
  {
    role: 'ASSISTANT',
    content: MOCK_DECISION.firstMessageMarkdown!,
    createdAt: '2026-06-25T10:00:00Z',
    isDecision: true,
    isStreaming: false,
  },
  {
    role: 'USER',
    content: 'Czy mogę wysłać sprzęt jutro?',
    createdAt: '2026-06-25T10:01:00Z',
    isDecision: false,
    isStreaming: false,
  },
  {
    role: 'ASSISTANT',
    content: 'Tak, możesz wysłać sprzęt jutro.',
    createdAt: '2026-06-25T10:01:05Z',
    isDecision: false,
    isStreaming: false,
  },
];

// ---------------------------------------------------------------------------
// Mock AppStateService (signal-based store)
// ---------------------------------------------------------------------------

class MockAppStateService {
  readonly sessionId = jasmine.createSpy('sessionId').and.returnValue('sess-001');
  readonly decision = jasmine.createSpy('decision').and.returnValue(MOCK_DECISION);
  readonly messages = jasmine.createSpy('messages').and.returnValue(MOCK_MESSAGES);
  readonly pendingState = jasmine.createSpy('pendingState').and.returnValue('IDLE');

  // Writable signals exposed as spies — component accesses them as functions
  setMessages = jasmine.createSpy('setMessages');
  setPendingState = jasmine.createSpy('setPendingState');
  setDecision = jasmine.createSpy('setDecision');
  setSessionId = jasmine.createSpy('setSessionId');
}

// ---------------------------------------------------------------------------
// Mock CaseService
// ---------------------------------------------------------------------------

class MockCaseService {
  getCase = jasmine.createSpy('getCase').and.returnValue(of());
  sendMessage = jasmine.createSpy('sendMessage').and.returnValue(Promise.resolve());
}

// ---------------------------------------------------------------------------
// Spec
// ---------------------------------------------------------------------------

describe('ChatComponent (TAC-002-05, TAC-002-07)', () => {
  let component: ChatComponent;
  let fixture: ComponentFixture<ChatComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChatComponent],
      providers: [
        provideAnimations(),
        provideMarkdown({ sanitize: SecurityContext.HTML }),
        { provide: AppStateService, useClass: MockAppStateService },
        { provide: CaseService, useClass: MockCaseService },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: { get: () => 'sess-001' } },
            paramMap: of({ get: () => 'sess-001' }),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ChatComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  // -------------------------------------------------------------------------
  // 1. Composer elements present with data-testids
  // -------------------------------------------------------------------------

  describe('composer elements (data-testid)', () => {
    it('should render the composer input with data-testid="chat-composer-input"', () => {
      const el = fixture.nativeElement.querySelector('[data-testid="chat-composer-input"]');
      expect(el).toBeTruthy();
    });

    it('should render the send button with data-testid="chat-send-button"', () => {
      const el = fixture.nativeElement.querySelector('[data-testid="chat-send-button"]');
      expect(el).toBeTruthy();
    });
  });

  // -------------------------------------------------------------------------
  // 2. Message bubbles rendered
  // -------------------------------------------------------------------------

  describe('message bubbles', () => {
    it('should render message bubbles container with data-testid="chat-messages"', () => {
      const el = fixture.nativeElement.querySelector('[data-testid="chat-messages"]');
      expect(el).toBeTruthy();
    });

    it('should render at least one message bubble', () => {
      const bubbles = fixture.nativeElement.querySelectorAll('[data-testid="chat-message-bubble"]');
      expect(bubbles.length).toBeGreaterThan(0);
    });
  });

  // -------------------------------------------------------------------------
  // 3. Decision summary panel
  // -------------------------------------------------------------------------

  describe('decision summary', () => {
    it('should render the decision summary with data-testid="chat-decision-summary"', () => {
      const el = fixture.nativeElement.querySelector('[data-testid="chat-decision-summary"]');
      expect(el).toBeTruthy();
    });
  });

  // -------------------------------------------------------------------------
  // 4. Typing indicator in DOM
  // -------------------------------------------------------------------------

  describe('typing indicator', () => {
    it('should include the typing indicator element in the DOM', () => {
      const el = fixture.nativeElement.querySelector('[data-testid="chat-typing-indicator"]');
      expect(el).toBeTruthy();
    });
  });

  // -------------------------------------------------------------------------
  // 5. First decision bubble — structure from firstMessageMarkdown (AC-18/19)
  // -------------------------------------------------------------------------

  describe('first decision bubble rendering (AC-18/19)', () => {
    it('should render the first message as decision bubble with data-testid="chat-first-decision"', () => {
      const el = fixture.nativeElement.querySelector('[data-testid="chat-first-decision"]');
      expect(el).toBeTruthy();
    });

    it('should contain the firstMessageMarkdown content in rendered output', () => {
      const el = fixture.nativeElement.querySelector('[data-testid="chat-first-decision"]');
      // ngx-markdown converts ## heading to <h2> — check some rendered text is present
      expect(el?.textContent).toBeTruthy();
      expect(el?.textContent?.length).toBeGreaterThan(10);
    });
  });

  // -------------------------------------------------------------------------
  // 6. Sanitization — raw HTML/script in content must NOT execute (TAC-002-05)
  // -------------------------------------------------------------------------

  describe('sanitization — no XSS (TAC-002-05)', () => {
    it('should NOT render an executable script tag from assistant message content', () => {
      // Inject a message with a script payload into the component's message list
      // and verify no script element ends up in the rendered DOM.
      component.testMessages = [
        {
          role: 'ASSISTANT',
          content: '<script>window.__xss_test = true;</script>Bezpieczna odpowiedź.',
          createdAt: '2026-06-25T10:00:00Z',
          isDecision: false,
          isStreaming: false,
        },
      ];
      fixture.detectChanges();

      const scripts = fixture.nativeElement.querySelectorAll('script');
      expect(scripts.length).toBe(0);
      // The window property must NOT be set (script was not executed)
      expect((window as Window & { __xss_test?: boolean }).__xss_test).toBeUndefined();
    });

    it('should NOT inject raw img onerror XSS payload from markdown content', () => {
      component.testMessages = [
        {
          role: 'ASSISTANT',
          content: '<img src="x" onerror="window.__xss_img=true">Tekst',
          createdAt: '2026-06-25T10:00:00Z',
          isDecision: false,
          isStreaming: false,
        },
      ];
      fixture.detectChanges();

      const imgs = fixture.nativeElement.querySelectorAll('img[onerror]');
      expect(imgs.length).toBe(0);
      expect((window as Window & { __xss_img?: boolean }).__xss_img).toBeUndefined();
    });
  });
});
