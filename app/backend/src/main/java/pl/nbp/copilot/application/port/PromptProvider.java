package pl.nbp.copilot.application.port;

import pl.nbp.copilot.domain.CaseType;

/**
 * Port for loading versioned LLM prompt templates.
 * Templates are stored as Markdown files under {@code resources/prompts/}.
 * ADR-003 §3/§6; AC-10/11/15/16.
 */
public interface PromptProvider {

    /**
     * Returns the system prompt (guardrails, persona, disclaimer).
     * ADR-003 §6; AC-26.
     */
    String systemPrompt();

    /**
     * Returns the image analysis prompt for the given case type.
     * The vision model uses this prompt to describe the equipment condition.
     * ADR-003 §3; AC-10/11.
     *
     * @param caseType determines which analysis prompt variant to load
     */
    String analysisPrompt(CaseType caseType);

    /**
     * Returns the decision prompt for the given case type.
     * The text model uses this prompt (with injected procedure and form data) to decide.
     * ADR-003 §3/§6; AC-15/16.
     *
     * @param caseType determines which decision prompt variant to load
     */
    String decisionPrompt(CaseType caseType);

    /**
     * Returns the chat prompt template used for streaming replies.
     * ADR-003 §3; AC-20/22.
     */
    String chatPrompt();
}
