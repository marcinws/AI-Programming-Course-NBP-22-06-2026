package pl.nbp.copilot.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.nbp.copilot.application.ChatService;
import pl.nbp.copilot.application.exception.SessionNotFoundException;
import pl.nbp.copilot.application.port.SessionStore;
import pl.nbp.copilot.domain.ChatReply;
import pl.nbp.copilot.domain.Decision;
import pl.nbp.copilot.domain.DecisionOutcome;
import pl.nbp.copilot.web.dto.ChatRequest;
import pl.nbp.copilot.web.dto.ErrorCode;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Handles POST /api/cases/{id}/messages — SSE streaming chat endpoint.
 * ADR-001 §3/§5/§6; AC-19/20/21/22/24; TAC-001-05.
 *
 * <p>The 404 check for an unknown session is performed synchronously BEFORE opening any SSE
 * emitter, so unknown session ids always return a JSON 404 body, not a text/event-stream.</p>
 */
@RestController
@RequestMapping("/api/cases")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final long EMITTER_TIMEOUT_MS = 120_000L; // 2 minutes

    private final ChatService chatService;
    private final SessionStore sessionStore;
    private final Executor asyncExecutor;
    private final ObjectMapper objectMapper;

    public ChatController(ChatService chatService,
                          SessionStore sessionStore,
                          Executor asyncExecutor,
                          ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.sessionStore = sessionStore;
        this.asyncExecutor = asyncExecutor;
        this.objectMapper = objectMapper;
    }

    /**
     * Streams a chat reply as Server-Sent Events.
     *
     * <p>Events:
     * <ul>
     *   <li>{@code token} — {@code {"delta":"..."}} for each streamed token delta</li>
     *   <li>{@code done} — {@code {"message":{...},"updatedDecision":{...}?}} after the stream completes</li>
     *   <li>{@code error} — {@code {"code":"...","message":"..."}} on mid-stream failure; stream then closes</li>
     * </ul>
     *
     * @param id      session UUID
     * @param request chat request body
     * @return SSE emitter (or 404 JSON for unknown session)
     */
    @PostMapping(value = "/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Object streamReply(@PathVariable UUID id,
                              @Valid @RequestBody ChatRequest request) {

        // Decide 404 BEFORE opening the emitter — prevents any text/event-stream response for unknown sessions
        if (sessionStore.find(id).isEmpty()) {
            throw new SessionNotFoundException(id);
        }

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitter.onCompletion(() -> log.debug("SSE completed for session {}", id));
        emitter.onTimeout(() -> {
            log.warn("SSE timeout for session {}", id);
            emitter.complete();
        });

        asyncExecutor.execute(() -> {
            try {
                ChatReply reply = chatService.streamReply(id, request.content(), delta -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("token")
                                .data(new TokenEvent(delta)));
                    } catch (IOException e) {
                        log.warn("Failed to send SSE token for session {}: {}", id, e.getMessage());
                    }
                });

                // Build done payload
                DoneMessage msg = new DoneMessage(
                        "ASSISTANT", reply.replyMarkdown(), Instant.now());
                DoneDecisionView updatedDecision = reply.updatedDecision() != null
                        ? toUpdatedDecisionView(reply.updatedDecision())
                        : null;
                DoneEvent doneEvent = new DoneEvent(msg, updatedDecision);

                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(objectMapper.writeValueAsString(doneEvent)));
                emitter.complete();

            } catch (SessionNotFoundException e) {
                // Session expired between the check and the actual streaming call
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(objectMapper.writeValueAsString(
                                    new ErrorEvent(ErrorCode.SESSION_NOT_FOUND.name(),
                                            "Sesja wygasła lub nie istnieje."))));
                } catch (IOException ignored) {
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("Error during SSE streaming for session {}", id, e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(objectMapper.writeValueAsString(
                                    new ErrorEvent(ErrorCode.LLM_UNAVAILABLE.name(),
                                            "Błąd komunikacji z modelem AI. Spróbuj ponownie."))));
                } catch (IOException ignored) {
                }
                emitter.complete();
            }
        });

        return emitter;
    }

    // ── SSE event payload records ─────────────────────────────────────────────

    private record TokenEvent(String delta) {
    }

    private record DoneMessage(String role, String content, Instant createdAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record DoneEvent(DoneMessage message, DoneDecisionView updatedDecision) {
    }

    private record DoneDecisionView(DecisionOutcome outcome, String justification, String nextSteps) {
    }

    private record ErrorEvent(String code, String message) {
    }

    private DoneDecisionView toUpdatedDecisionView(Decision d) {
        return new DoneDecisionView(d.outcome(), d.justification(), d.nextSteps());
    }
}
