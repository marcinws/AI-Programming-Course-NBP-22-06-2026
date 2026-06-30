package pl.nbp.copilot.application;

import org.springframework.stereotype.Service;
import pl.nbp.copilot.application.exception.SessionNotFoundException;
import pl.nbp.copilot.application.port.DecisionPort;
import pl.nbp.copilot.application.port.SessionStore;
import pl.nbp.copilot.application.port.TokenSink;
import pl.nbp.copilot.domain.CaseSession;
import pl.nbp.copilot.domain.ChatMessage;
import pl.nbp.copilot.domain.ChatReply;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates a streaming chat turn.
 * Pipeline: load session → append user message → stream reply → append assistant message → optionally supersede decision.
 * ADR-001 §3/§5; AC-19/20/21/22/24; TAC-001-05.
 */
@Service
public class ChatService {

    private final SessionStore sessionStore;
    private final DecisionPort decisionPort;

    public ChatService(SessionStore sessionStore, DecisionPort decisionPort) {
        this.sessionStore = sessionStore;
        this.decisionPort = decisionPort;
    }

    /**
     * Streams a chat reply for the given session.
     *
     * <p>Steps:
     * <ol>
     *   <li>Load session — throws {@link SessionNotFoundException} if not found/expired.</li>
     *   <li>Append the user message to the session.</li>
     *   <li>Call {@link DecisionPort#streamReply}, pushing token deltas to the sink.</li>
     *   <li>Append the full assistant message.</li>
     *   <li>If the reply contains an updated decision, call {@link SessionStore#supersedeDecision}.</li>
     * </ol>
     *
     * @param sessionId  the session to send the message to
     * @param content    the user's message content
     * @param sink       receives token deltas as they arrive
     * @return the accumulated {@link ChatReply}
     * @throws SessionNotFoundException if the session is not found or has expired
     */
    public ChatReply streamReply(UUID sessionId, String content, TokenSink sink) {
        CaseSession session = sessionStore.find(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        // Append user message before the LLM call
        ChatMessage userMessage = new ChatMessage(ChatMessage.Role.USER, content, Instant.now());
        sessionStore.appendMessages(sessionId, List.of(userMessage));

        // Stream reply via the decision port
        ChatReply reply = decisionPort.streamReply(session, content, sink);

        // Append full assistant message
        ChatMessage assistantMessage = new ChatMessage(
                ChatMessage.Role.ASSISTANT, reply.replyMarkdown(), Instant.now());
        sessionStore.appendMessages(sessionId, List.of(assistantMessage));

        // Supersede decision if model signaled a material change (AC-21)
        if (reply.updatedDecision() != null) {
            sessionStore.supersedeDecision(sessionId, reply.updatedDecision());
        }

        return reply;
    }
}
