/**
 * TypeScript models mirroring the backend REST DTOs and SSE event payloads.
 * Names and enum values MUST remain byte-compatible with the Spring backend contract.
 * Client-only additions are marked with the "Client-only" comment.
 *
 * Reference: ADR-001 (backend contract), ADR-002 §4 (client-only additions).
 */

// ---------------------------------------------------------------------------
// Enums / string unions
// ---------------------------------------------------------------------------

/** Mirrors backend CaseType enum. */
export type CaseType = 'COMPLAINT' | 'RETURN';

/** Mirrors backend DecisionOutcome enum. */
export type DecisionOutcome = 'APPROVE' | 'REJECT' | 'ESCALATE';

/** Mirrors backend EquipmentCategory enum — 13 values. */
export type EquipmentCategory =
  | 'SMARTPHONE'
  | 'TABLET'
  | 'LAPTOP'
  | 'DESKTOP_PC'
  | 'MONITOR'
  | 'TV'
  | 'HEADPHONES_AUDIO'
  | 'CAMERA'
  | 'PRINTER'
  | 'NETWORKING'
  | 'SMARTWATCH_WEARABLE'
  | 'SMALL_APPLIANCE'
  | 'OTHER';

/** Mirrors backend ChatRole enum. */
export type ChatRole = 'SYSTEM_ASSISTANT' | 'USER' | 'ASSISTANT';

/** Mirrors backend ErrorCode enum. */
export type ErrorCode =
  | 'VALIDATION_ERROR'
  | 'SESSION_NOT_FOUND'
  | 'IMAGE_TOO_LARGE'
  | 'UNSUPPORTED_MEDIA_TYPE'
  | 'LLM_UNAVAILABLE'
  | 'LLM_TIMEOUT';

// ---------------------------------------------------------------------------
// Shared / simple structures
// ---------------------------------------------------------------------------

/** A single select option returned by GET /api/metadata. */
export interface Option {
  id: string;
  /** Polish display label. */
  labelPl: string;
}

// ---------------------------------------------------------------------------
// REST request / response shapes
// ---------------------------------------------------------------------------

/** Response from GET /api/metadata — drives form selectors and file constraints. */
export interface MetadataResponse {
  caseTypes: Option[];
  equipmentCategories: Option[];
  imageConstraints: {
    acceptedTypes: string[];
    maxBytes: number;
  };
}

/** AI decision included in case creation and session responses. */
export interface Decision {
  outcome: DecisionOutcome;
  justification: string;
  nextSteps: string;
  /**
   * Markdown-formatted first message rendered in the chat view.
   * Present only on the POST /api/cases 201 response.
   */
  firstMessageMarkdown?: string;
}

/** Response from POST /api/cases (201 Created). */
export interface CreateCaseResponse {
  sessionId: string;
  decision: Decision;
  imageAnalysisSummary: string;
}

/** Request body for POST /api/cases/{id}/messages. */
export interface ChatRequest {
  content: string;
}

/** A single chat message as returned by the backend. */
export interface ChatMessage {
  role: ChatRole;
  content: string;
  /** ISO 8601 timestamp string as returned by the backend. */
  createdAt: string;
}

/** Form field values sent as multipart/form-data to POST /api/cases. */
export interface CaseFormValues {
  caseType: CaseType;
  equipmentCategory: EquipmentCategory;
  modelName: string;
  /** ISO date string (YYYY-MM-DD). */
  purchaseDate: string;
  /** Required only when caseType === 'COMPLAINT'. */
  reason?: string;
}

/** Response from GET /api/cases/{id} — full session rehydration. */
export interface SessionResponse {
  sessionId: string;
  form: CaseFormValues;
  imageAnalysisSummary: string;
  decision: Decision;
  messages: ChatMessage[];
}

/** Error response body returned on 4xx/5xx responses. */
export interface ErrorResponse {
  code: ErrorCode | string;
  message: string;
  /** Per-field validation errors surfaced beneath form controls. */
  fieldErrors?: Record<string, string>;
}

// ---------------------------------------------------------------------------
// SSE event union
// ---------------------------------------------------------------------------

/**
 * Union of all SSE event payloads consumed from POST /api/cases/{id}/messages.
 * Reference: ADR-002 §4.
 */
export type SseEvent =
  | { type: 'token'; delta: string }
  | { type: 'done'; message: ChatMessage; updatedDecision?: Decision }
  | { type: 'error'; code: ErrorCode | string; message: string };

// ---------------------------------------------------------------------------
// Client-only additions (ADR-002 §4)
// ---------------------------------------------------------------------------

/**
 * Client-only: per-action async state held in the signal store.
 * Drives spinner visibility, button disabled states, and error banners.
 */
export type PendingState = 'IDLE' | 'SUBMITTING' | 'STREAMING' | 'ERROR';

/**
 * Client-only: UI view-model that wraps a ChatMessage with streaming/display flags.
 * `isStreaming` drives the live-growing bubble and typing indicator in ChatComponent.
 * `isDecision` marks the first bubble that renders the formatted decision Markdown.
 * `createdAt` mirrors ChatMessage.createdAt (ISO string from backend).
 */
export interface DisplayMessage {
  role: ChatRole;
  content: string;
  /** ISO 8601 timestamp string — matches ChatMessage.createdAt wire format. */
  createdAt: string;
  /** True for the first bubble that renders the decision Markdown. */
  isDecision: boolean;
  /** True while this bubble is receiving SSE token deltas. */
  isStreaming: boolean;
}
