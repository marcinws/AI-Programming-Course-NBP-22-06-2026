/**
 * Guard spec: validates that models.ts interfaces match the backend contract.
 * These are compile-time (type assignments) + runtime shape assertions.
 * Fails if the types do not exist or do not accept the expected payload shapes.
 */

import {
  CaseType,
  DecisionOutcome,
  EquipmentCategory,
  ChatRole,
  ErrorCode,
  Option,
  MetadataResponse,
  Decision,
  CreateCaseResponse,
  ChatRequest,
  ChatMessage,
  SessionResponse,
  CaseFormValues,
  SseEvent,
  ErrorResponse,
  PendingState,
  DisplayMessage,
} from './models';

describe('models — contract guard', () => {
  // --- compile-time assignments (fails at tsc if types are wrong) ---

  const _caseType: CaseType = 'COMPLAINT';
  const _caseTypeReturn: CaseType = 'RETURN';
  const _outcome: DecisionOutcome = 'APPROVE';
  const _outcomeReject: DecisionOutcome = 'REJECT';
  const _outcomeEscalate: DecisionOutcome = 'ESCALATE';
  const _category: EquipmentCategory = 'SMARTPHONE';
  const _categoryOther: EquipmentCategory = 'OTHER';
  const _chatRole: ChatRole = 'SYSTEM_ASSISTANT';
  const _chatRoleUser: ChatRole = 'USER';
  const _chatRoleAssistant: ChatRole = 'ASSISTANT';
  const _errorCode: ErrorCode = 'VALIDATION_ERROR';
  const _pendingState: PendingState = 'IDLE';
  const _pendingStateStreaming: PendingState = 'STREAMING';

  // Suppress unused variable warnings for pure compile-time type checks
  void [
    _caseType, _caseTypeReturn, _outcome, _outcomeReject, _outcomeEscalate,
    _category, _categoryOther, _chatRole, _chatRoleUser, _chatRoleAssistant,
    _errorCode, _pendingState, _pendingStateStreaming,
  ];

  // --- runtime shape assertions ---

  describe('MetadataResponse — sample payload', () => {
    const raw = {
      caseTypes: [
        { id: 'COMPLAINT', labelPl: 'Reklamacja' },
        { id: 'RETURN', labelPl: 'Zwrot' },
      ],
      equipmentCategories: [
        { id: 'SMARTPHONE', labelPl: 'Smartfon' },
      ],
      imageConstraints: {
        acceptedTypes: ['image/jpeg', 'image/png'],
        maxBytes: 10485760,
      },
    };

    it('should accept a valid MetadataResponse shape', () => {
      const meta: MetadataResponse = raw;
      expect(meta.caseTypes.length).toBe(2);
      expect(meta.imageConstraints.maxBytes).toBe(10485760);
    });

    it('caseType options should carry labelPl', () => {
      const opt: Option = raw.caseTypes[0];
      expect(opt.id).toBe('COMPLAINT');
      expect(opt.labelPl).toBe('Reklamacja');
    });
  });

  describe('CreateCaseResponse — sample payload', () => {
    const raw = {
      sessionId: 'sess-001',
      decision: {
        outcome: 'APPROVE' as DecisionOutcome,
        justification: 'Towar w gwarancji.',
        nextSteps: 'Wyślij sprzęt do serwisu.',
        firstMessageMarkdown: '## Decyzja\nZatwierdzono.',
      },
      imageAnalysisSummary: 'Wyraźne uszkodzenie ekranu.',
    };

    it('should accept a valid CreateCaseResponse shape', () => {
      const resp: CreateCaseResponse = raw;
      expect(resp.sessionId).toBe('sess-001');
      expect(resp.decision.outcome).toBe('APPROVE');
      expect(resp.decision.firstMessageMarkdown).toBeDefined();
    });
  });

  describe('SSE event union — done payload', () => {
    const donePayload = {
      type: 'done' as const,
      message: {
        role: 'ASSISTANT' as ChatRole,
        content: 'Odpowiedź asystenta.',
        createdAt: '2026-06-25T10:00:00Z',
      },
      updatedDecision: {
        outcome: 'ESCALATE' as DecisionOutcome,
        justification: 'Wymaga weryfikacji.',
        nextSteps: 'Skontaktuj się z menedżerem.',
      } as Decision,
    };

    it('should accept a valid SseEvent done payload', () => {
      const event: SseEvent = donePayload;
      if (event.type === 'done') {
        expect(event.message.role).toBe('ASSISTANT');
        expect(event.updatedDecision?.outcome).toBe('ESCALATE');
      } else {
        fail('Expected type to be done');
      }
    });

    it('should accept a token SseEvent', () => {
      const tokenEvent: SseEvent = { type: 'token', delta: 'Czę' };
      expect(tokenEvent.type).toBe('token');
    });

    it('should accept an error SseEvent', () => {
      const errorEvent: SseEvent = {
        type: 'error',
        code: 'LLM_TIMEOUT',
        message: 'Upłynął limit czasu.',
      };
      expect(errorEvent.type).toBe('error');
    });
  });

  describe('SessionResponse — sample payload', () => {
    const raw = {
      sessionId: 'sess-002',
      form: {
        caseType: 'RETURN' as CaseType,
        equipmentCategory: 'LAPTOP' as EquipmentCategory,
        modelName: 'ThinkPad X1',
        purchaseDate: '2024-01-15',
      } as CaseFormValues,
      imageAnalysisSummary: 'Brak widocznych uszkodzeń.',
      decision: {
        outcome: 'REJECT' as DecisionOutcome,
        justification: 'Poza gwarancją.',
        nextSteps: 'Prosimy o kontakt z serwisem komercyjnym.',
      },
      messages: [
        {
          role: 'ASSISTANT' as ChatRole,
          content: 'Witaj!',
          createdAt: '2026-06-25T09:00:00Z',
        },
      ] as ChatMessage[],
    };

    it('should accept a valid SessionResponse shape', () => {
      const session: SessionResponse = raw;
      expect(session.messages.length).toBe(1);
      expect(session.form.caseType).toBe('RETURN');
    });
  });

  describe('ErrorResponse — sample payload', () => {
    it('should accept ErrorResponse with fieldErrors', () => {
      const err: ErrorResponse = {
        code: 'VALIDATION_ERROR',
        message: 'Błąd walidacji.',
        fieldErrors: { modelName: 'Pole wymagane.' },
      };
      expect(err.fieldErrors?.['modelName']).toBe('Pole wymagane.');
    });

    it('should accept ErrorResponse without fieldErrors', () => {
      const err: ErrorResponse = {
        code: 'LLM_UNAVAILABLE',
        message: 'Serwis niedostępny.',
      };
      expect(err.code).toBe('LLM_UNAVAILABLE');
    });
  });

  describe('ChatRequest', () => {
    it('should accept a ChatRequest shape', () => {
      const req: ChatRequest = { content: 'Mam pytanie.' };
      expect(req.content).toBe('Mam pytanie.');
    });
  });

  describe('DisplayMessage — client-only view model', () => {
    it('should accept a streaming DisplayMessage', () => {
      const msg: DisplayMessage = {
        role: 'ASSISTANT',
        content: 'Asystent pisze...',
        createdAt: '2026-06-25T10:00:00Z',
        isDecision: false,
        isStreaming: true,
      };
      expect(msg.isStreaming).toBeTrue();
    });

    it('should accept a decision DisplayMessage', () => {
      const msg: DisplayMessage = {
        role: 'ASSISTANT',
        content: '## Decyzja\nZatwierdzono.',
        createdAt: '2026-06-25T10:00:00Z',
        isDecision: true,
        isStreaming: false,
      };
      expect(msg.isDecision).toBeTrue();
    });
  });

  describe('EquipmentCategory — all 13 values', () => {
    const allCategories: EquipmentCategory[] = [
      'SMARTPHONE',
      'TABLET',
      'LAPTOP',
      'DESKTOP_PC',
      'MONITOR',
      'TV',
      'HEADPHONES_AUDIO',
      'CAMERA',
      'PRINTER',
      'NETWORKING',
      'SMARTWATCH_WEARABLE',
      'SMALL_APPLIANCE',
      'OTHER',
    ];

    it('should have exactly 13 category values', () => {
      expect(allCategories.length).toBe(13);
    });

    allCategories.forEach((cat) => {
      it(`should accept category: ${cat}`, () => {
        const c: EquipmentCategory = cat;
        expect(c).toBe(cat);
      });
    });
  });

  describe('ErrorCode — all 6 values', () => {
    const allCodes: ErrorCode[] = [
      'VALIDATION_ERROR',
      'SESSION_NOT_FOUND',
      'IMAGE_TOO_LARGE',
      'UNSUPPORTED_MEDIA_TYPE',
      'LLM_UNAVAILABLE',
      'LLM_TIMEOUT',
    ];

    it('should have exactly 6 error code values', () => {
      expect(allCodes.length).toBe(6);
    });
  });
});
