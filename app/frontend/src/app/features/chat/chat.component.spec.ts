/**
 * Spec: ChatComponent — P3.F1 + P4.F2
 *
 * Coverage (P3.F1 — skeleton):
 * - First decision bubble renders greeting/decision/justification/next-steps/disclaimer
 *   from firstMessageMarkdown via ngx-markdown
 * - Sanitization: assistant Markdown via ngx-markdown does NOT execute raw HTML/script
 * - Message bubbles render for user/assistant roles
 * - Decision summary (mat-expansion-panel) shows decision data
 * - Composer (textarea + send button) present with correct data-testids
 * - Typing indicator present in DOM (hidden when not streaming)
 *
 * Coverage (P4.F2 — streaming turn):
 * - onSend(): appends USER bubble + empty ASSISTANT bubble + enters STREAMING
 * - token events grow the assistant bubble incrementally
 * - done event finalizes the message; updatedDecision rendered; composer re-enabled
 * - error event shows inline Polish error; composer re-enabled
 * - 404/SESSION_NOT_FOUND shows "Sesja wygasła" + "rozpocznij nową sprawę" action
 * - rehydrate-on-empty: getCase(id) called when store is empty on init
 * - 404 on getCase → "expired" error message
 *
 * TAC: TAC-002-04, 05, 06, 07
 */

import {
  ComponentFixture,
  TestBed,
  fakeAsync,
  tick,
  flush,
} from '@angular/core/testing';
import { SecurityContext } from '@angular/core';
import { provideAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { provideMarkdown } from 'ngx-markdown';

import { ChatComponent } from './chat.component';
import { AppStateService } from '../../core/app-state';
import { CaseService } from '../../core/case.service';
import { DisplayMessage, Decision, ChatMessage, SessionResponse } from '../../core/models';

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
// Mock data for P4.F2
// ---------------------------------------------------------------------------

const MOCK_CHAT_MESSAGE: ChatMessage = {
  role: 'ASSISTANT',
  content: 'Tak, możesz wysłać sprzęt jutro.',
  createdAt: '2026-06-25T10:05:00Z',
};

const MOCK_SESSION: SessionResponse = {
  sessionId: 'sess-001',
  form: {
    caseType: 'RETURN',
    equipmentCategory: 'SMARTPHONE',
    modelName: 'iPhone 15',
    purchaseDate: '2024-01-01',
  },
  imageAnalysisSummary: 'Brak uszkodzeń.',
  decision: MOCK_DECISION,
  messages: [
    {
      role: 'ASSISTANT',
      content: MOCK_DECISION.firstMessageMarkdown!,
      createdAt: '2026-06-25T10:00:00Z',
    },
  ],
};

// ---------------------------------------------------------------------------
// Mock AppStateService (signal-based store)
// ---------------------------------------------------------------------------

function makeMockAppState(
  initialMessages: DisplayMessage[] = MOCK_MESSAGES,
  initialSessionId = 'sess-001',
): {
  sessionId: jasmine.Spy;
  decision: jasmine.Spy;
  messages: jasmine.Spy;
  pendingState: jasmine.Spy;
  setMessages: jasmine.Spy;
  setPendingState: jasmine.Spy;
  setDecision: jasmine.Spy;
  setSessionId: jasmine.Spy;
  appendMessage: jasmine.Spy;
  updateLastMessage: jasmine.Spy;
  hydrateFromSession: jasmine.Spy;
  reset: jasmine.Spy;
} {
  const state = {
    _sessionId: initialSessionId,
    _decision: MOCK_DECISION as Decision | null,
    _messages: [...initialMessages] as DisplayMessage[],
    _pendingState: 'IDLE' as string,
  };

  const svc = {
    sessionId: jasmine.createSpy('sessionId').and.callFake(() => state._sessionId),
    decision: jasmine.createSpy('decision').and.callFake(() => state._decision),
    messages: jasmine.createSpy('messages').and.callFake(() => state._messages),
    pendingState: jasmine.createSpy('pendingState').and.callFake(() => state._pendingState),
    setMessages: jasmine.createSpy('setMessages').and.callFake((msgs: DisplayMessage[]) => {
      state._messages = msgs;
    }),
    setPendingState: jasmine.createSpy('setPendingState').and.callFake((s: string) => {
      state._pendingState = s;
    }),
    setDecision: jasmine.createSpy('setDecision').and.callFake((d: Decision | null) => {
      state._decision = d;
    }),
    setSessionId: jasmine.createSpy('setSessionId'),
    appendMessage: jasmine.createSpy('appendMessage').and.callFake((msg: DisplayMessage) => {
      state._messages = [...state._messages, msg];
    }),
    updateLastMessage: jasmine
      .createSpy('updateLastMessage')
      .and.callFake((updater: (msg: DisplayMessage) => DisplayMessage) => {
        if (state._messages.length === 0) return;
        const list = [...state._messages];
        list[list.length - 1] = updater(list[list.length - 1]);
        state._messages = list;
      }),
    hydrateFromSession: jasmine.createSpy('hydrateFromSession'),
    reset: jasmine.createSpy('reset').and.callFake(() => {
      state._sessionId = '';
      state._decision = null;
      state._messages = [];
      state._pendingState = 'IDLE';
    }),
  };

  return svc;
}

// ---------------------------------------------------------------------------
// Mock CaseService
// ---------------------------------------------------------------------------

class MockCaseService {
  getCase = jasmine.createSpy('getCase').and.returnValue(of(MOCK_SESSION));
  sendMessage = jasmine.createSpy('sendMessage').and.returnValue(Promise.resolve());
}

// ---------------------------------------------------------------------------
// Spec
// ---------------------------------------------------------------------------

describe('ChatComponent (TAC-002-05, TAC-002-07)', () => {
  let component: ChatComponent;
  let fixture: ComponentFixture<ChatComponent>;
  let mockAppState: ReturnType<typeof makeMockAppState>;
  let mockCaseService: MockCaseService;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    mockAppState = makeMockAppState();
    mockCaseService = new MockCaseService();
    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);
    routerSpy.navigate.and.returnValue(Promise.resolve(true));

    await TestBed.configureTestingModule({
      imports: [ChatComponent],
      providers: [
        provideAnimations(),
        provideMarkdown({ sanitize: SecurityContext.HTML }),
        { provide: AppStateService, useValue: mockAppState },
        { provide: CaseService, useValue: mockCaseService },
        { provide: Router, useValue: routerSpy },
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

  // -------------------------------------------------------------------------
  // 5b. First decision bubble — disclaimer text present (§8 row: "First bubble structure")
  // -------------------------------------------------------------------------

  describe('first decision bubble — disclaimer text (§8)', () => {
    it('should render the Polish disclaimer text in the decision bubble', () => {
      const decisionEl = fixture.nativeElement.querySelector('[data-testid="chat-first-decision"]');
      expect(decisionEl).toBeTruthy();
      // The disclaimer is always rendered beneath the Markdown in the decision bubble
      const disclaimerText = (decisionEl.textContent as string).toLowerCase();
      // "wstępna" or "zweryfikowana" must appear — comes from the disclaimer paragraph
      expect(
        disclaimerText.includes('wstępna') || disclaimerText.includes('zweryfikowana')
      ).toBeTrue();
    });
  });

  // -------------------------------------------------------------------------
  // 5c. Decision summary panel — shows outcome, justification, next-steps (§8)
  // -------------------------------------------------------------------------

  describe('decision summary panel — content (§8)', () => {
    it('should show the Polish outcome label in the summary panel', () => {
      const panel = fixture.nativeElement.querySelector('[data-testid="chat-decision-summary"]');
      expect(panel).toBeTruthy();
      const text = (panel.textContent as string);
      // decisionOutcomeLabel('APPROVE') === 'Zatwierdzona'
      expect(text).toContain('Zatwierdzona');
    });

    it('should show "Podsumowanie decyzji" heading in the expansion panel', () => {
      const panel = fixture.nativeElement.querySelector('[data-testid="chat-decision-summary"]');
      expect((panel.textContent as string)).toContain('Podsumowanie decyzji');
    });
  });

  // -------------------------------------------------------------------------
  // 5d. Accessibility labels on composer elements (TAC-002-07)
  // -------------------------------------------------------------------------

  describe('accessibility labels (AC-23 / TAC-002-07)', () => {
    it('should have aria-label on the composer textarea', () => {
      const textarea = fixture.nativeElement.querySelector('[data-testid="chat-composer-input"]');
      expect(textarea).toBeTruthy();
      const ariaLabel = textarea.getAttribute('aria-label');
      expect(ariaLabel).toBeTruthy();
      // Must be Polish
      expect((ariaLabel as string).length).toBeGreaterThan(0);
    });

    it('should have aria-label on the send button', () => {
      const button = fixture.nativeElement.querySelector('[data-testid="chat-send-button"]');
      expect(button).toBeTruthy();
      const ariaLabel = button.getAttribute('aria-label');
      expect(ariaLabel).toBeTruthy();
      expect((ariaLabel as string).length).toBeGreaterThan(0);
    });

    it('should have aria-live attribute on the typing indicator', () => {
      const indicator = fixture.nativeElement.querySelector('[data-testid="chat-typing-indicator"]');
      expect(indicator).toBeTruthy();
      expect(indicator.getAttribute('aria-live')).toBe('polite');
    });
  });

  // =========================================================================
  // P4.F2 — Streaming chat turn (TAC-002-04/05/06/07)
  // =========================================================================

  // -------------------------------------------------------------------------
  // 7. onSend → immediate USER + ASSISTANT bubbles + STREAMING state
  // -------------------------------------------------------------------------

  describe('P4.F2 — onSend: immediate bubbles + STREAMING state (TAC-002-06)', () => {
    it('should append USER bubble immediately on send', fakeAsync(() => {
      // sendMessage stays pending (never resolves in this test)
      mockCaseService.sendMessage.and.returnValue(new Promise(() => undefined));

      component.composerText = 'Kiedy mogę odebrać sprzęt?';
      component.onSend();
      tick();

      const msgs = mockAppState.messages() as DisplayMessage[];
      // Find the last USER message (the newly appended one, not from MOCK_MESSAGES)
      const userMsgs = msgs.filter((m) => m.role === 'USER');
      const userMsg = userMsgs[userMsgs.length - 1];
      expect(userMsg).toBeDefined();
      expect(userMsg!.content).toBe('Kiedy mogę odebrać sprzęt?');
      flush();
    }));

    it('should append an empty ASSISTANT bubble immediately on send', fakeAsync(() => {
      mockCaseService.sendMessage.and.returnValue(new Promise(() => undefined));

      component.composerText = 'Pytanie testowe';
      component.onSend();
      tick();

      const msgs = mockAppState.messages() as DisplayMessage[];
      const assistantMsgs = msgs.filter((m) => m.role === 'ASSISTANT' && !m.isDecision);
      expect(assistantMsgs.length).toBeGreaterThan(0);
      // The live streaming bubble is empty at first
      const liveMsg = assistantMsgs[assistantMsgs.length - 1];
      expect(liveMsg.isStreaming).toBeTrue();
      flush();
    }));

    it('should set pendingState to STREAMING on send', fakeAsync(() => {
      mockCaseService.sendMessage.and.returnValue(new Promise(() => undefined));

      component.composerText = 'Pytanie';
      component.onSend();
      tick();

      expect(mockAppState.setPendingState).toHaveBeenCalledWith('STREAMING');
      flush();
    }));

    it('should clear the composer text immediately on send', fakeAsync(() => {
      mockCaseService.sendMessage.and.returnValue(new Promise(() => undefined));

      component.composerText = 'Tekst';
      component.onSend();
      tick();

      expect(component.composerText).toBe('');
      flush();
    }));

    it('should disable the send button while streaming', fakeAsync(() => {
      mockCaseService.sendMessage.and.returnValue(new Promise(() => undefined));

      component.composerText = 'Tekst';
      component.onSend();
      tick();

      expect(component.isSendDisabled).toBeTrue();
      flush();
    }));
  });

  // -------------------------------------------------------------------------
  // 8. token events — grow assistant bubble incrementally
  // -------------------------------------------------------------------------

  describe('P4.F2 — token events grow assistant bubble (TAC-002-05)', () => {
    it('should grow the last assistant bubble when token handler is called', fakeAsync(() => {
      let capturedHandlers: Parameters<typeof mockCaseService.sendMessage>[2] | undefined;

      mockCaseService.sendMessage.and.callFake(
        (_id: string, _content: string, handlers: Parameters<typeof mockCaseService.sendMessage>[2]) => {
          capturedHandlers = handlers;
          return new Promise<void>(() => undefined);
        },
      );

      component.composerText = 'Pytanie';
      component.onSend();
      tick();

      // Fire token events
      capturedHandlers!.onToken('Tak');
      capturedHandlers!.onToken(', ');
      capturedHandlers!.onToken('możesz.');

      const msgs = mockAppState.messages();
      const lastMsg = msgs[msgs.length - 1];
      expect(lastMsg.content).toBe('Tak, możesz.');
      flush();
    }));
  });

  // -------------------------------------------------------------------------
  // 9. done event — finalize bubble; updatedDecision; composer re-enabled
  // -------------------------------------------------------------------------

  describe('P4.F2 — done event finalizes bubble and re-enables composer (TAC-002-06)', () => {
    it('should finalize the assistant bubble and set pendingState IDLE on done', fakeAsync(() => {
      let capturedHandlers: Parameters<typeof mockCaseService.sendMessage>[2] | undefined;

      mockCaseService.sendMessage.and.callFake(
        (_id: string, _content: string, handlers: Parameters<typeof mockCaseService.sendMessage>[2]) => {
          capturedHandlers = handlers;
          return Promise.resolve();
        },
      );

      component.composerText = 'Pytanie';
      component.onSend();
      tick();

      capturedHandlers!.onDone(MOCK_CHAT_MESSAGE, undefined);
      tick();

      // pendingState should be IDLE
      expect(mockAppState.setPendingState).toHaveBeenCalledWith('IDLE');
      // The last assistant bubble should no longer be streaming
      const msgs = mockAppState.messages();
      const lastMsg = msgs[msgs.length - 1];
      expect(lastMsg.isStreaming).toBeFalse();
      flush();
    }));

    it('should update decision and preserve history when updatedDecision arrives in done', fakeAsync(() => {
      const updatedDecision: Decision = {
        outcome: 'REJECT',
        justification: 'Towar poza gwarancją.',
        nextSteps: 'Skontaktuj się z serwisem.',
      };

      let capturedHandlers: Parameters<typeof mockCaseService.sendMessage>[2] | undefined;

      mockCaseService.sendMessage.and.callFake(
        (_id: string, _content: string, handlers: Parameters<typeof mockCaseService.sendMessage>[2]) => {
          capturedHandlers = handlers;
          return Promise.resolve();
        },
      );

      component.composerText = 'Pytanie o decyzję';
      component.onSend();
      tick();

      const messagesBefore = mockAppState.messages().length;

      capturedHandlers!.onDone(MOCK_CHAT_MESSAGE, updatedDecision);
      tick();

      // Decision should be updated
      expect(mockAppState.setDecision).toHaveBeenCalledWith(updatedDecision);
      // History is preserved (no messages removed)
      expect(mockAppState.messages().length).toBeGreaterThanOrEqual(messagesBefore);
      flush();
    }));

    it('should re-enable the composer (isSendDisabled=false with non-empty text) after done', fakeAsync(() => {
      let capturedHandlers: Parameters<typeof mockCaseService.sendMessage>[2] | undefined;

      mockCaseService.sendMessage.and.callFake(
        (_id: string, _content: string, handlers: Parameters<typeof mockCaseService.sendMessage>[2]) => {
          capturedHandlers = handlers;
          return Promise.resolve();
        },
      );

      component.composerText = 'Pytanie';
      component.onSend();
      tick();

      capturedHandlers!.onDone(MOCK_CHAT_MESSAGE, undefined);
      tick();

      // After done, pendingState is IDLE so composer should be enabled when text is present
      component.composerText = 'Nowe pytanie';
      expect(mockAppState.pendingState()).toBe('IDLE');
      expect(component.isSendDisabled).toBeFalse();
      flush();
    }));
  });

  // -------------------------------------------------------------------------
  // 10. error event → inline Polish error; composer re-enabled
  // -------------------------------------------------------------------------

  describe('P4.F2 — error event shows inline error; composer re-enabled (TAC-002-07)', () => {
    it('should expose a sseError signal and re-enable composer on error event', fakeAsync(() => {
      let capturedHandlers: Parameters<typeof mockCaseService.sendMessage>[2] | undefined;

      mockCaseService.sendMessage.and.callFake(
        (_id: string, _content: string, handlers: Parameters<typeof mockCaseService.sendMessage>[2]) => {
          capturedHandlers = handlers;
          return new Promise<void>(() => undefined); // never resolves
        },
      );

      component.composerText = 'Pytanie';
      component.onSend();
      tick();

      capturedHandlers!.onError('SOME_ERROR', 'Błąd serwera.');

      expect(component.sseError()).toBeTruthy();
      expect(mockAppState.setPendingState).toHaveBeenCalledWith('ERROR');
      // Composer re-enabled
      component.composerText = 'Retry';
      expect(component.isSendDisabled).toBeFalse();
      flush();
    }));
  });

  // -------------------------------------------------------------------------
  // 11. 404/SESSION_NOT_FOUND → "Sesja wygasła" + "rozpocznij nową sprawę"
  // -------------------------------------------------------------------------

  describe('P4.F2 — SESSION_NOT_FOUND → expired error + new-case action (TAC-002-07)', () => {
    it('should expose sessionExpired=true on SESSION_NOT_FOUND error code', fakeAsync(() => {
      let capturedHandlers: Parameters<typeof mockCaseService.sendMessage>[2] | undefined;

      mockCaseService.sendMessage.and.callFake(
        (_id: string, _content: string, handlers: Parameters<typeof mockCaseService.sendMessage>[2]) => {
          capturedHandlers = handlers;
          return new Promise<void>(() => undefined);
        },
      );

      component.composerText = 'Pytanie';
      component.onSend();
      tick();

      capturedHandlers!.onError('SESSION_NOT_FOUND', 'Sesja nie istnieje.');

      expect(component.sessionExpired()).toBeTrue();
      flush();
    }));

    it('should reset state and navigate to / when startNewCase() is called', fakeAsync(() => {
      component.startNewCase();
      tick();

      expect(mockAppState.reset).toHaveBeenCalled();
      expect(routerSpy.navigate).toHaveBeenCalledWith(['/']);
      flush();
    }));
  });

  // -------------------------------------------------------------------------
  // 11b. Duplicate send guard — onSend() is no-op while STREAMING (TAC-002-06)
  // -------------------------------------------------------------------------

  describe('P4.F2 — duplicate send prevention (TAC-002-06)', () => {
    it('should not call sendMessage again when already STREAMING', fakeAsync(() => {
      // First call sets STREAMING
      mockCaseService.sendMessage.and.returnValue(new Promise(() => undefined));

      component.composerText = 'Pierwsze pytanie';
      component.onSend();
      tick();

      const callsAfterFirst = mockCaseService.sendMessage.calls.count();

      // Second call while still streaming — should be ignored
      component.composerText = 'Drugie pytanie';
      component.onSend();
      tick();

      expect(mockCaseService.sendMessage.calls.count()).toBe(callsAfterFirst);
      flush();
    }));
  });

  // -------------------------------------------------------------------------
  // 11c. "Sesja wygasła" banner and "Rozpocznij nową sprawę" button in DOM
  // -------------------------------------------------------------------------

  describe('P4.F2 — session expired UI elements (TAC-002-07)', () => {
    it('should show data-testid="chat-session-expired" banner when sessionExpired is true', fakeAsync(() => {
      component.sessionExpired.set(true);
      fixture.detectChanges();

      const banner = fixture.nativeElement.querySelector('[data-testid="chat-session-expired"]');
      expect(banner).toBeTruthy();
      flush();
    }));

    it('should show "Sesja wygasła" text in the expired banner', fakeAsync(() => {
      component.sessionExpired.set(true);
      fixture.detectChanges();

      const banner = fixture.nativeElement.querySelector('[data-testid="chat-session-expired"]');
      expect((banner.textContent as string).toLowerCase()).toContain('sesja wygasła');
      flush();
    }));

    it('should show "Rozpocznij nową sprawę" button when sessionExpired is true', fakeAsync(() => {
      component.sessionExpired.set(true);
      fixture.detectChanges();

      const btn = fixture.nativeElement.querySelector('[data-testid="chat-new-case-button"]');
      expect(btn).toBeTruthy();
      expect((btn.textContent as string).toLowerCase()).toContain('nową sprawę');
      flush();
    }));

    it('should hide the composer when sessionExpired is true', fakeAsync(() => {
      component.sessionExpired.set(true);
      fixture.detectChanges();

      const composer = fixture.nativeElement.querySelector('[data-testid="chat-composer-input"]');
      expect(composer).toBeFalsy();
      flush();
    }));
  });

  // -------------------------------------------------------------------------
  // 12. Rehydrate-on-empty: getCase() called when store is empty on init
  // -------------------------------------------------------------------------

  describe('P4.F2 — rehydrate on empty store (TAC-002-04)', () => {
    it('should call getCase when messages are empty on init', fakeAsync(async () => {
      // Create a new component instance with an empty store
      const emptyState = makeMockAppState([], '');

      const emptyCaseService = new MockCaseService();
      emptyCaseService.getCase.and.returnValue(of(MOCK_SESSION));

      await TestBed.resetTestingModule().configureTestingModule({
        imports: [ChatComponent],
        providers: [
          provideAnimations(),
          provideMarkdown({ sanitize: SecurityContext.HTML }),
          { provide: AppStateService, useValue: emptyState },
          { provide: CaseService, useValue: emptyCaseService },
          { provide: Router, useValue: routerSpy },
          {
            provide: ActivatedRoute,
            useValue: {
              snapshot: { paramMap: { get: () => 'sess-001' } },
              paramMap: of({ get: () => 'sess-001' }),
            },
          },
        ],
      }).compileComponents();

      const emptyFixture = TestBed.createComponent(ChatComponent);
      emptyFixture.detectChanges();
      tick();

      expect(emptyCaseService.getCase).toHaveBeenCalledWith('sess-001');
      expect(emptyState.hydrateFromSession).toHaveBeenCalledWith(MOCK_SESSION);
      flush();
    }));

    it('should show expired error when getCase returns 404 on init', fakeAsync(async () => {
      const emptyState = makeMockAppState([], '');

      const emptyCaseService = new MockCaseService();
      emptyCaseService.getCase.and.returnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 404,
              error: { code: 'SESSION_NOT_FOUND', message: 'Sesja nie istnieje.' },
            }),
        ),
      );

      const emptyRouter = jasmine.createSpyObj<Router>('Router', ['navigate']);
      emptyRouter.navigate.and.returnValue(Promise.resolve(true));

      await TestBed.resetTestingModule().configureTestingModule({
        imports: [ChatComponent],
        providers: [
          provideAnimations(),
          provideMarkdown({ sanitize: SecurityContext.HTML }),
          { provide: AppStateService, useValue: emptyState },
          { provide: CaseService, useValue: emptyCaseService },
          { provide: Router, useValue: emptyRouter },
          {
            provide: ActivatedRoute,
            useValue: {
              snapshot: { paramMap: { get: () => 'sess-old' } },
              paramMap: of({ get: () => 'sess-old' }),
            },
          },
        ],
      }).compileComponents();

      const emptyFixture = TestBed.createComponent(ChatComponent);
      emptyFixture.detectChanges();
      tick();

      const comp = emptyFixture.componentInstance;
      expect(comp.sessionExpired()).toBeTrue();
      flush();
    }));
  });
});
