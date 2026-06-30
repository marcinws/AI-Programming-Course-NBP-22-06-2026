package pl.nbp.copilot.application.port;

import pl.nbp.copilot.domain.CaseForm;
import pl.nbp.copilot.domain.CaseSession;
import pl.nbp.copilot.domain.ChatReply;
import pl.nbp.copilot.domain.Decision;
import pl.nbp.copilot.domain.ImageAnalysis;

/**
 * Port for AI-driven decision making and streaming chat replies.
 * ADR-003 §3/§5; AC-13/14/15/16/17/20/21/22/26; TAC-003-04/05/06/07.
 */
public interface DecisionPort {

    /**
     * Produces a structured service decision from the case form, image analysis, and procedure text.
     *
     * <p>Uses the decision/text model ({@code OPENROUTER_TEXT_MODEL}) with structured output
     * ({@code responseFormat(Decision)}). Any unparseable or invalid outcome is coerced to ESCALATE
     * (TAC-003-04). The {@code firstMessageMarkdown} field is composed server-side and always contains
     * the mandatory advisory disclaimer (AC-26; TAC-003-05).</p>
     *
     * @param form          intake case form data
     * @param imageAnalysis result of the vision analysis step
     * @param procedureText the matching procedure document text injected into the prompt (TAC-003-02)
     * @return a validated {@link Decision} — never null, never an invalid outcome
     * @throws pl.nbp.copilot.integration.exception.LlmUnavailableException after bounded retries on 5xx / connection error
     * @throws pl.nbp.copilot.integration.exception.LlmTimeoutException     after bounded retries on timeout
     */
    Decision decide(CaseForm form, ImageAnalysis imageAnalysis, String procedureText);

    /**
     * Streams a chat reply over the existing case session.
     *
     * <p>Calls the decision/text model via {@code createStreaming} with the full session context
     * (system prompt + decision bubble + message history + new user message). Each token delta is
     * pushed to the {@link TokenSink}. After the stream completes, the accumulated reply is returned
     * as a {@link ChatReply} which may contain an {@code updatedDecision} if the user supplied
     * material new information.</p>
     *
     * @param session     the current case session (provides context: form, image analysis, decision, history)
     * @param userContent the user's latest message content
     * @param sink        callback receiving each token delta in order (ADR-003 §5; TAC-003-06)
     * @return the accumulated {@link ChatReply}
     * @throws pl.nbp.copilot.integration.exception.LlmUnavailableException before stream start on 5xx / connection error
     * @throws pl.nbp.copilot.integration.exception.LlmTimeoutException     before stream start on timeout
     */
    ChatReply streamReply(CaseSession session, String userContent, TokenSink sink);
}
