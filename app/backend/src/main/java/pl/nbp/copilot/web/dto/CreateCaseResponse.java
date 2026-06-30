package pl.nbp.copilot.web.dto;

/**
 * Response body for {@code POST /api/cases} (HTTP 201).
 * ADR-001 §4.
 *
 * @param sessionId            Opaque UUID identifying the created session.
 * @param decision             Structured decision with outcome + composed first message.
 * @param imageAnalysisSummary Short textual summary produced by the vision model.
 */
public record CreateCaseResponse(
        String sessionId,
        DecisionView decision,
        String imageAnalysisSummary
) {
}
