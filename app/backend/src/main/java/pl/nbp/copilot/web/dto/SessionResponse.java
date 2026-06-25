package pl.nbp.copilot.web.dto;

import java.util.List;

/**
 * Response body for {@code GET /api/cases/{id}} (HTTP 200).
 * ADR-001 §4.
 *
 * @param sessionId            Opaque session UUID.
 * @param form                 Submitted case form fields (read-only view).
 * @param imageAnalysisSummary Vision model's textual description of the uploaded image.
 * @param decision             Current decision (may have been superseded by chat).
 * @param messages             Full ordered chat history; index 0 is the decision bubble.
 */
public record SessionResponse(
        String sessionId,
        CaseFormView form,
        String imageAnalysisSummary,
        DecisionView decision,
        List<MessageView> messages
) {
}
