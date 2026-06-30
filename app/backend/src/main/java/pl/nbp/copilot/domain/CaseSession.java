package pl.nbp.copilot.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable snapshot of a service-case session held in-memory.
 * The in-memory store mutates by replacing session instances (copy-on-write semantics).
 * ADR-004 §3/§4; AC-21; TAC-004-05.
 *
 * <p>No raw image bytes are present — only the textual {@link ImageAnalysis} derived from
 * the vision model call (ADR-004 §3, TAC-004-05).</p>
 *
 * <p>The {@code messages} list is ordered; index 0 is always the decision bubble
 * ({@link ChatMessage.Role#SYSTEM_ASSISTANT}).</p>
 *
 * @param id            Opaque session identifier (UUID v4).
 * @param caseType      Case type (COMPLAINT or RETURN).
 * @param form          Validated intake form data.
 * @param imageAnalysis Analysis produced by the vision model; may be null before the first analysis.
 * @param decision      Current decision (may be superseded, but history is preserved — AC-21).
 * @param messages      Ordered message history; index 0 = decision bubble.
 * @param createdAt     Session creation timestamp.
 * @param expiresAt     TTL expiry timestamp; sessions past this are treated as not-found (TAC-004-01/02).
 */
public record CaseSession(
        UUID id,
        CaseType caseType,
        CaseForm form,
        ImageAnalysis imageAnalysis,
        Decision decision,
        List<ChatMessage> messages,
        Instant createdAt,
        Instant expiresAt
) {
}
