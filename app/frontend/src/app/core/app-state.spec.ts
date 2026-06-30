/**
 * Spec: AppStateService — P3.F1 / P4.F1 / P4.F2
 *
 * Coverage:
 * - Initial state (signals default values)
 * - hydrateFromCreate: sets sessionId, decision, first message as isDecision=true
 * - hydrateFromSession: maps messages; first ASSISTANT message → isDecision=true
 * - reset(): clears all state to initial values
 * - appendMessage / updateLastMessage helpers
 * - setDecision / setPendingState / setMessages / setSessionId setters
 *
 * TAC: TAC-002-04 (state management), TAC-002-06 (SUBMITTING/STREAMING from store perspective)
 */

import { TestBed } from '@angular/core/testing';
import { AppStateService } from './app-state';
import {
  CreateCaseResponse,
  SessionResponse,
  DisplayMessage,
  Decision,
  PendingState,
} from './models';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const MOCK_DECISION: Decision = {
  outcome: 'APPROVE',
  justification: 'Towar w gwarancji.',
  nextSteps: 'Wyślij sprzęt do serwisu.',
  firstMessageMarkdown: '## Decyzja\n**Zatwierdzona**',
};

const MOCK_CREATE_RESPONSE: CreateCaseResponse = {
  sessionId: 'sess-create-001',
  decision: MOCK_DECISION,
  imageAnalysisSummary: 'Wyraźne uszkodzenie ekranu.',
};

const MOCK_SESSION_RESPONSE: SessionResponse = {
  sessionId: 'sess-session-002',
  form: {
    caseType: 'RETURN',
    equipmentCategory: 'LAPTOP',
    modelName: 'ThinkPad X1',
    purchaseDate: '2024-01-15',
  },
  imageAnalysisSummary: 'Brak uszkodzeń.',
  decision: {
    outcome: 'REJECT',
    justification: 'Poza gwarancją.',
    nextSteps: 'Prosimy o kontakt.',
  },
  messages: [
    {
      role: 'ASSISTANT',
      content: '## Decyzja\nOdrzucono.',
      createdAt: '2026-06-25T10:00:00Z',
    },
    {
      role: 'USER',
      content: 'Czy można się odwołać?',
      createdAt: '2026-06-25T10:01:00Z',
    },
    {
      role: 'ASSISTANT',
      content: 'Tak, można złożyć odwołanie.',
      createdAt: '2026-06-25T10:01:10Z',
    },
  ],
};

// ---------------------------------------------------------------------------
// Spec
// ---------------------------------------------------------------------------

describe('AppStateService', () => {
  let service: AppStateService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AppStateService],
    });
    service = TestBed.inject(AppStateService);
  });

  // -------------------------------------------------------------------------
  // 1. Initial state
  // -------------------------------------------------------------------------

  describe('initial state', () => {
    it('should have null sessionId initially', () => {
      expect(service.sessionId()).toBeNull();
    });

    it('should have null decision initially', () => {
      expect(service.decision()).toBeNull();
    });

    it('should have empty messages array initially', () => {
      expect(service.messages()).toEqual([]);
    });

    it('should have IDLE pendingState initially', () => {
      expect(service.pendingState()).toBe('IDLE');
    });
  });

  // -------------------------------------------------------------------------
  // 2. hydrateFromCreate — TAC-002-04, ADR-002 §3
  // -------------------------------------------------------------------------

  describe('hydrateFromCreate()', () => {
    beforeEach(() => {
      service.hydrateFromCreate(MOCK_CREATE_RESPONSE);
    });

    it('should set sessionId from CreateCaseResponse', () => {
      expect(service.sessionId()).toBe('sess-create-001');
    });

    it('should set decision from CreateCaseResponse', () => {
      expect(service.decision()).toEqual(MOCK_DECISION);
    });

    it('should create exactly one message (the decision bubble)', () => {
      expect(service.messages().length).toBe(1);
    });

    it('should mark the first message as isDecision=true', () => {
      const firstMsg = service.messages()[0];
      expect(firstMsg.isDecision).toBeTrue();
    });

    it('should mark the decision bubble as ASSISTANT role', () => {
      const firstMsg = service.messages()[0];
      expect(firstMsg.role).toBe('ASSISTANT');
    });

    it('should populate decision bubble content from firstMessageMarkdown', () => {
      const firstMsg = service.messages()[0];
      expect(firstMsg.content).toBe(MOCK_DECISION.firstMessageMarkdown!);
    });

    it('should mark the decision bubble isStreaming=false', () => {
      const firstMsg = service.messages()[0];
      expect(firstMsg.isStreaming).toBeFalse();
    });

    it('should set pendingState to IDLE after hydration', () => {
      expect(service.pendingState()).toBe('IDLE');
    });
  });

  // -------------------------------------------------------------------------
  // 3. hydrateFromSession — TAC-002-04, "rehydrate-on-refresh"
  // -------------------------------------------------------------------------

  describe('hydrateFromSession()', () => {
    beforeEach(() => {
      service.hydrateFromSession(MOCK_SESSION_RESPONSE);
    });

    it('should set sessionId from SessionResponse', () => {
      expect(service.sessionId()).toBe('sess-session-002');
    });

    it('should set decision from SessionResponse', () => {
      expect(service.decision()?.outcome).toBe('REJECT');
    });

    it('should map all session messages into DisplayMessages', () => {
      expect(service.messages().length).toBe(3);
    });

    it('should mark first ASSISTANT message as isDecision=true', () => {
      const firstMsg = service.messages()[0];
      expect(firstMsg.isDecision).toBeTrue();
      expect(firstMsg.role).toBe('ASSISTANT');
    });

    it('should mark remaining messages as isDecision=false', () => {
      const remaining = service.messages().slice(1);
      for (const msg of remaining) {
        expect(msg.isDecision).toBeFalse();
      }
    });

    it('should mark all messages as isStreaming=false', () => {
      for (const msg of service.messages()) {
        expect(msg.isStreaming).toBeFalse();
      }
    });

    it('should preserve message roles', () => {
      const msgs = service.messages();
      expect(msgs[0].role).toBe('ASSISTANT');
      expect(msgs[1].role).toBe('USER');
      expect(msgs[2].role).toBe('ASSISTANT');
    });

    it('should preserve message content', () => {
      expect(service.messages()[1].content).toBe('Czy można się odwołać?');
    });

    it('should set pendingState to IDLE after hydration', () => {
      expect(service.pendingState()).toBe('IDLE');
    });
  });

  // -------------------------------------------------------------------------
  // 4. reset() — "start new case" (TAC-002-06, ADR-002 §3)
  // -------------------------------------------------------------------------

  describe('reset()', () => {
    beforeEach(() => {
      // Hydrate first so there is state to clear
      service.hydrateFromCreate(MOCK_CREATE_RESPONSE);
      service.setPendingState('STREAMING');
      service.reset();
    });

    it('should clear sessionId to null', () => {
      expect(service.sessionId()).toBeNull();
    });

    it('should clear decision to null', () => {
      expect(service.decision()).toBeNull();
    });

    it('should clear messages to empty array', () => {
      expect(service.messages()).toEqual([]);
    });

    it('should reset pendingState to IDLE', () => {
      expect(service.pendingState()).toBe('IDLE');
    });
  });

  // -------------------------------------------------------------------------
  // 5. Setters
  // -------------------------------------------------------------------------

  describe('setters', () => {
    it('setSessionId() should update sessionId signal', () => {
      service.setSessionId('new-session-id');
      expect(service.sessionId()).toBe('new-session-id');
    });

    it('setDecision() should update decision signal', () => {
      service.setDecision(MOCK_DECISION);
      expect(service.decision()).toEqual(MOCK_DECISION);
    });

    it('setDecision(null) should clear decision', () => {
      service.setDecision(MOCK_DECISION);
      service.setDecision(null);
      expect(service.decision()).toBeNull();
    });

    const pendingStates: PendingState[] = ['IDLE', 'SUBMITTING', 'STREAMING', 'ERROR'];
    pendingStates.forEach((state) => {
      it(`setPendingState('${state}') should update pendingState`, () => {
        service.setPendingState(state);
        expect(service.pendingState()).toBe(state);
      });
    });

    it('setMessages() should replace message list', () => {
      const msgs: DisplayMessage[] = [
        {
          role: 'USER',
          content: 'Pytanie',
          createdAt: '2026-06-25T10:00:00Z',
          isDecision: false,
          isStreaming: false,
        },
      ];
      service.setMessages(msgs);
      expect(service.messages()).toEqual(msgs);
    });
  });

  // -------------------------------------------------------------------------
  // 6. appendMessage / updateLastMessage helpers
  // -------------------------------------------------------------------------

  describe('appendMessage()', () => {
    it('should append a message to the list', () => {
      const msg: DisplayMessage = {
        role: 'USER',
        content: 'Pierwsze pytanie',
        createdAt: '2026-06-25T10:00:00Z',
        isDecision: false,
        isStreaming: false,
      };
      service.appendMessage(msg);
      expect(service.messages().length).toBe(1);
      expect(service.messages()[0]).toEqual(msg);
    });

    it('should append subsequent messages without replacing previous ones', () => {
      const msg1: DisplayMessage = {
        role: 'USER',
        content: 'Pytanie 1',
        createdAt: '2026-06-25T10:00:00Z',
        isDecision: false,
        isStreaming: false,
      };
      const msg2: DisplayMessage = {
        role: 'ASSISTANT',
        content: '',
        createdAt: '2026-06-25T10:00:01Z',
        isDecision: false,
        isStreaming: true,
      };
      service.appendMessage(msg1);
      service.appendMessage(msg2);
      expect(service.messages().length).toBe(2);
      expect(service.messages()[0].role).toBe('USER');
      expect(service.messages()[1].role).toBe('ASSISTANT');
    });
  });

  describe('updateLastMessage()', () => {
    it('should update only the last message via the updater function', () => {
      service.appendMessage({
        role: 'USER',
        content: 'Pytanie',
        createdAt: '2026-06-25T10:00:00Z',
        isDecision: false,
        isStreaming: false,
      });
      service.appendMessage({
        role: 'ASSISTANT',
        content: 'Cześć',
        createdAt: '2026-06-25T10:00:01Z',
        isDecision: false,
        isStreaming: true,
      });

      service.updateLastMessage((msg) => ({ ...msg, content: msg.content + ', jak mogę pomóc?' }));

      expect(service.messages()[1].content).toBe('Cześć, jak mogę pomóc?');
      // First message unchanged
      expect(service.messages()[0].content).toBe('Pytanie');
    });

    it('should finalize streaming bubble (isStreaming=false) via updateLastMessage', () => {
      service.appendMessage({
        role: 'ASSISTANT',
        content: 'Odpowiedź',
        createdAt: '2026-06-25T10:00:00Z',
        isDecision: false,
        isStreaming: true,
      });

      service.updateLastMessage((msg) => ({ ...msg, isStreaming: false }));

      expect(service.messages()[0].isStreaming).toBeFalse();
    });

    it('should be a no-op when list is empty', () => {
      expect(() => {
        service.updateLastMessage((msg) => msg);
      }).not.toThrow();
      expect(service.messages()).toEqual([]);
    });
  });
});
