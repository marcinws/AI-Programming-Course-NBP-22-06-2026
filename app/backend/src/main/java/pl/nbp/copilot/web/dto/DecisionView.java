package pl.nbp.copilot.web.dto;

import pl.nbp.copilot.domain.DecisionOutcome;

/**
 * Decision sub-object returned inside {@link CreateCaseResponse}.
 * ADR-001 §4.
 *
 * @param outcome              APPROVE | REJECT | ESCALATE
 * @param justification        Explanation rendered in Polish Markdown.
 * @param nextSteps            Recommended next steps in Polish.
 * @param firstMessageMarkdown Full first chat bubble: greeting + decision + justification +
 *                             next steps + mandatory disclaimer. Polish Markdown.
 */
public record DecisionView(
        DecisionOutcome outcome,
        String justification,
        String nextSteps,
        String firstMessageMarkdown
) {
}
