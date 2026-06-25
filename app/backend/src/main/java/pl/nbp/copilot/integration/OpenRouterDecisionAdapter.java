package pl.nbp.copilot.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.core.http.StreamResponse;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.helpers.ChatCompletionAccumulator;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pl.nbp.copilot.application.port.DecisionPort;
import pl.nbp.copilot.application.port.PolicyProvider;
import pl.nbp.copilot.application.port.PromptProvider;
import pl.nbp.copilot.application.port.TokenSink;
import pl.nbp.copilot.domain.CaseForm;
import pl.nbp.copilot.domain.CaseSession;
import pl.nbp.copilot.domain.ChatMessage;
import pl.nbp.copilot.domain.ChatReply;
import pl.nbp.copilot.domain.Decision;
import pl.nbp.copilot.domain.DecisionConfidence;
import pl.nbp.copilot.domain.DecisionOutcome;
import pl.nbp.copilot.domain.ImageAnalysis;
import pl.nbp.copilot.integration.exception.LlmTimeoutException;
import pl.nbp.copilot.integration.exception.LlmUnavailableException;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Decision adapter that calls the OpenRouter text/reasoning model via the OpenAI Java SDK.
 * Implements:
 * <ul>
 *   <li>Structured decision via Chat Completions JSON mode (ESCALATE fail-safe on any parse error)</li>
 *   <li>Streaming chat replies with delta accumulation</li>
 * </ul>
 *
 * <p>ADR-003 §3/§5; AC-13/14/15/16/17/20/21/22/26; TAC-003-04/05/06/07.</p>
 */
@Component
public class OpenRouterDecisionAdapter implements DecisionPort {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterDecisionAdapter.class);

    /**
     * Mandatory advisory disclaimer that must appear in every composed first-message bubble (AC-26).
     */
    static final String DISCLAIMER_PL =
            "\n\n---\n> ⚠️ **Zastrzeżenie:** Niniejsza rekomendacja ma charakter wyłącznie doradczy " +
            "i nie stanowi wiążącej decyzji firmy. Ostateczną decyzję podejmuje uprawniony pracownik " +
            "na własną odpowiedzialność.";

    /**
     * Marker that signals the model considers the user's input off-topic.
     * The model is instructed to include this token in the reply when redirecting.
     */
    private static final String OFF_TOPIC_MARKER = "[OFFTOPIC]";

    /**
     * Marker prefix that signals the model proposes an updated decision.
     */
    private static final String UPDATED_DECISION_MARKER = "[UPDATED_DECISION:";

    private final OpenAIClient client;
    private final OpenRouterProperties properties;
    private final PromptProvider promptProvider;
    private final PolicyProvider policyProvider;
    private final ObjectMapper objectMapper;

    public OpenRouterDecisionAdapter(
            OpenAIClient client,
            OpenRouterProperties properties,
            PromptProvider promptProvider,
            PolicyProvider policyProvider) {
        this.client = client;
        this.properties = properties;
        this.promptProvider = promptProvider;
        this.policyProvider = policyProvider;
        this.objectMapper = new ObjectMapper();
    }

    // ── DecisionPort.decide ───────────────────────────────────────────────────

    @Override
    public Decision decide(CaseForm form, ImageAnalysis imageAnalysis, String procedureText) {
        String systemPrompt = promptProvider.systemPrompt();
        String decisionPrompt = buildDecisionPrompt(
                promptProvider.decisionPrompt(form.caseType()), form, imageAnalysis, procedureText);

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(properties.textModel())
                .addSystemMessage(systemPrompt)
                .addUserMessage(decisionPrompt)
                .build();

        String responseText = callWithExceptionMapping(() -> {
            var completion = client.chat().completions().create(params);
            return completion.choices().stream()
                    .findFirst()
                    .flatMap(c -> c.message().content())
                    .orElse("");
        });

        Decision parsed = parseDecision(responseText);
        return composeFirstMessage(parsed);
    }

    // ── DecisionPort.streamReply ──────────────────────────────────────────────

    @Override
    public ChatReply streamReply(CaseSession session, String userContent, TokenSink sink) {
        String systemPrompt = promptProvider.systemPrompt();
        String chatSystemPrompt = promptProvider.chatPrompt();

        // Build the full message list: system + context history + new user message
        List<ChatCompletionMessageParam> allMessages = new ArrayList<>();
        allMessages.add(ChatCompletionMessageParam.ofSystem(
                com.openai.models.chat.completions.ChatCompletionSystemMessageParam.builder()
                        .content(systemPrompt + "\n\n" + chatSystemPrompt)
                        .build()));
        allMessages.addAll(buildContextMessages(session));
        allMessages.add(ChatCompletionMessageParam.ofUser(
                com.openai.models.chat.completions.ChatCompletionUserMessageParam.builder()
                        .content(userContent)
                        .build()));

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(properties.textModel())
                .messages(allMessages)
                .build();

        ChatCompletionAccumulator accumulator = ChatCompletionAccumulator.create();

        callWithExceptionMapping(() -> {
            try (StreamResponse<ChatCompletionChunk> stream =
                         client.chat().completions().createStreaming(params)) {
                stream.stream()
                        .peek(accumulator::accumulate)
                        .forEach(chunk -> chunk.choices().forEach(choice -> {
                            String delta = choice.delta().content().orElse("");
                            if (!delta.isEmpty()) {
                                sink.accept(delta);
                            }
                        }));
            }
            return null;
        });

        String fullReply = accumulator.chatCompletion().choices().stream()
                .findFirst()
                .flatMap(c -> c.message().content())
                .orElse("");

        boolean offTopic = fullReply.contains(OFF_TOPIC_MARKER);
        Decision updatedDecision = extractUpdatedDecision(fullReply, session.decision());

        return new ChatReply(fullReply, updatedDecision, offTopic);
    }

    // ── Private helpers — decision building ───────────────────────────────────

    /**
     * Builds the decision prompt by substituting placeholders in the template
     * with actual form/analysis/procedure data.
     */
    private String buildDecisionPrompt(String template, CaseForm form,
                                       ImageAnalysis imageAnalysis, String procedureText) {
        return template
                .replace("{{equipmentCategory}}", nvl(form.equipmentCategory() != null ? form.equipmentCategory().name() : null))
                .replace("{{modelName}}", nvl(form.modelName()))
                .replace("{{purchaseDate}}", form.purchaseDate() != null ? form.purchaseDate().toString() : "—")
                .replace("{{reason}}", nvl(form.reason()))
                .replace("{{imageDescription}}", nvl(imageAnalysis.description()))
                .replace("{{procedureText}}", nvl(procedureText));
    }

    /**
     * Parses the model's JSON response into a {@link Decision}.
     * Any parse error or invalid/unknown outcome → coerced to ESCALATE (TAC-003-04).
     */
    private Decision parseDecision(String responseText) {
        try {
            // Strip markdown code fences if present
            String json = stripCodeFences(responseText);
            JsonNode root = objectMapper.readTree(json);

            DecisionOutcome outcome = parseOutcome(root);
            String justification = root.path("justification").asText("");
            List<String> citedRules = parseCitedRules(root);
            String nextSteps = root.path("nextSteps").asText("");
            DecisionConfidence confidence = parseConfidence(root);

            return new Decision(outcome, justification, citedRules, nextSteps, confidence, null);
        } catch (Exception e) {
            log.warn("Failed to parse decision JSON; coercing to ESCALATE. responseLength={} error={}",
                    responseText.length(), e.getMessage());
            return escalateFallback("Nie udało się przetworzyć odpowiedzi modelu decyzyjnego. " +
                    "Sprawa wymaga ręcznej weryfikacji.");
        }
    }

    private DecisionOutcome parseOutcome(JsonNode root) {
        String raw = root.path("outcome").asText("").toUpperCase().trim();
        // Map Polish labels to enum values that the prompt may return
        raw = switch (raw) {
            case "ZATWIERDŹ", "ZATWIERDZ", "APPROVE" -> "APPROVE";
            case "ODRZUĆ", "ODRZUC", "REJECT" -> "REJECT";
            case "ESKALUJ", "ESCALATE" -> "ESCALATE";
            default -> raw;
        };
        try {
            return DecisionOutcome.valueOf(raw);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown decision outcome '{}'; coercing to ESCALATE", raw);
            return DecisionOutcome.ESCALATE;
        }
    }

    private List<String> parseCitedRules(JsonNode root) {
        List<String> rules = new ArrayList<>();
        JsonNode arr = root.path("citedRules");
        if (arr.isArray()) {
            arr.forEach(n -> rules.add(n.asText()));
        }
        return rules;
    }

    private DecisionConfidence parseConfidence(JsonNode root) {
        String raw = root.path("confidence").asText("").toUpperCase().trim();
        try {
            return DecisionConfidence.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return DecisionConfidence.MEDIUM;
        }
    }

    private Decision escalateFallback(String justification) {
        return new Decision(
                DecisionOutcome.ESCALATE,
                justification,
                List.of(),
                "Przekaż sprawę do przełożonego w celu ręcznej weryfikacji.",
                DecisionConfidence.LOW,
                null);
    }

    /**
     * Composes the {@code firstMessageMarkdown} field from the structured decision parts.
     * The disclaimer is always appended (AC-26; TAC-003-05).
     */
    private Decision composeFirstMessage(Decision d) {
        String outcomeLabel = switch (d.outcome()) {
            case APPROVE -> "✅ **Decyzja: ZATWIERDŹ**";
            case REJECT -> "❌ **Decyzja: ODRZUĆ**";
            case ESCALATE -> "⚠️ **Decyzja: ESKALUJ**";
        };

        StringBuilder sb = new StringBuilder();
        sb.append("Dzień dobry,\n\n");
        sb.append(outcomeLabel).append("\n\n");
        if (d.justification() != null && !d.justification().isBlank()) {
            sb.append("**Uzasadnienie:** ").append(d.justification()).append("\n\n");
        }
        if (d.citedRules() != null && !d.citedRules().isEmpty()) {
            sb.append("**Cytowane punkty procedury:**\n");
            d.citedRules().forEach(rule -> sb.append("- ").append(rule).append("\n"));
            sb.append("\n");
        }
        if (d.nextSteps() != null && !d.nextSteps().isBlank()) {
            sb.append("**Kolejne kroki:** ").append(d.nextSteps()).append("\n");
        }
        sb.append(DISCLAIMER_PL);

        return new Decision(
                d.outcome(),
                d.justification(),
                d.citedRules(),
                d.nextSteps(),
                d.confidence(),
                sb.toString());
    }

    // ── Private helpers — streaming / chat ────────────────────────────────────

    /**
     * Builds the message list for the streaming chat call from the session context.
     * Includes: the decision bubble as ASSISTANT, then the interleaved USER/ASSISTANT history.
     */
    private List<ChatCompletionMessageParam> buildContextMessages(CaseSession session) {
        List<ChatCompletionMessageParam> messages = new ArrayList<>();

        // Decision bubble is index 0 in the session message list (SYSTEM_ASSISTANT role)
        for (ChatMessage msg : session.messages()) {
            switch (msg.role()) {
                case SYSTEM_ASSISTANT, ASSISTANT ->
                        messages.add(ChatCompletionMessageParam.ofAssistant(
                                com.openai.models.chat.completions.ChatCompletionAssistantMessageParam.builder()
                                        .content(msg.content())
                                        .build()));
                case USER ->
                        messages.add(ChatCompletionMessageParam.ofUser(
                                com.openai.models.chat.completions.ChatCompletionUserMessageParam.builder()
                                        .content(msg.content())
                                        .build()));
            }
        }
        return messages;
    }

    /**
     * Extracts an {@link Decision} from a chat reply that contains an UPDATED_DECISION marker.
     * Returns null when no material update is signaled.
     */
    private Decision extractUpdatedDecision(String fullReply, Decision currentDecision) {
        int markerIdx = fullReply.indexOf(UPDATED_DECISION_MARKER);
        if (markerIdx < 0) return null;

        int start = markerIdx + UPDATED_DECISION_MARKER.length();
        int end = fullReply.indexOf(']', start);
        if (end < 0) return null;

        String outcomeStr = fullReply.substring(start, end).trim().toUpperCase();
        try {
            DecisionOutcome newOutcome = DecisionOutcome.valueOf(outcomeStr);
            if (newOutcome == currentDecision.outcome()) return null; // no material change
            String justification = "Zaktualizowana decyzja na podstawie nowych informacji od pracownika.";
            Decision raw = new Decision(newOutcome, justification,
                    currentDecision.citedRules(), currentDecision.nextSteps(),
                    currentDecision.confidence(), null);
            return composeFirstMessage(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ── Private helpers — exception mapping ───────────────────────────────────

    private <T> T callWithExceptionMapping(Supplier<T> call) {
        try {
            return call.get();
        } catch (OpenAIIoException e) {
            Throwable cause = e.getCause();
            if (isTimeoutCause(cause)) {
                throw new LlmTimeoutException("Upłynął limit czasu żądania do modelu decyzyjnego.", e);
            }
            throw new LlmUnavailableException("Błąd komunikacji z modelem decyzyjnym.", e);
        } catch (OpenAIServiceException e) {
            int status = e.statusCode();
            if (status == 408 || status == 504) {
                throw new LlmTimeoutException("Limit czasu żądania do modelu decyzyjnego (HTTP " + status + ").", e);
            }
            throw new LlmUnavailableException("Model decyzyjny zwrócił błąd serwera (HTTP " + status + ").", e);
        } catch (LlmUnavailableException | LlmTimeoutException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmUnavailableException("Nieoczekiwany błąd komunikacji z modelem decyzyjnym.", e);
        }
    }

    private boolean isTimeoutCause(Throwable cause) {
        if (cause == null) return false;
        if (cause instanceof SocketTimeoutException) return true;
        String msg = cause.getMessage();
        return msg != null && msg.toLowerCase().contains("timeout");
    }

    private String stripCodeFences(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                return trimmed.substring(start + 1, end).trim();
            }
        }
        return trimmed;
    }

    private String nvl(String value) {
        return value != null ? value : "—";
    }
}
