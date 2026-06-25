package pl.nbp.copilot.domain;

import java.util.List;

/**
 * Immutable value object representing the AI-generated service decision.
 * ADR-003 §4; ADR-004 §4; ADR-000 §5.
 *
 * @param outcome              The decision outcome: APPROVE, REJECT, or ESCALATE.
 * @param justification        Written justification referencing the applicable procedure and case facts (Polish).
 * @param citedRules           List of rule references from the procedure document that support the decision.
 * @param nextSteps            Actionable next steps for the employee (Polish).
 * @param confidence           Confidence level of the decision.
 * @param firstMessageMarkdown Server-composed first chat message: greeting + decision + justification + next steps + disclaimer (Polish Markdown).
 */
public record Decision(
        DecisionOutcome outcome,
        String justification,
        List<String> citedRules,
        String nextSteps,
        DecisionConfidence confidence,
        String firstMessageMarkdown
) {
}
