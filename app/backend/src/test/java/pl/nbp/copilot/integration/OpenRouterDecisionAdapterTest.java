package pl.nbp.copilot.integration;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.nbp.copilot.application.port.PolicyProvider;
import pl.nbp.copilot.application.port.PromptProvider;
import pl.nbp.copilot.application.port.TokenSink;
import pl.nbp.copilot.domain.CaseForm;
import pl.nbp.copilot.domain.CaseSession;
import pl.nbp.copilot.domain.CaseType;
import pl.nbp.copilot.domain.ChatMessage;
import pl.nbp.copilot.domain.ChatReply;
import pl.nbp.copilot.domain.Decision;
import pl.nbp.copilot.domain.DecisionConfidence;
import pl.nbp.copilot.domain.DecisionOutcome;
import pl.nbp.copilot.domain.EquipmentCategory;
import pl.nbp.copilot.domain.ImageAnalysis;
import pl.nbp.copilot.integration.exception.LlmUnavailableException;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link OpenRouterDecisionAdapter} against MockWebServer.
 * Verifies: structured decision parsing, ESCALATE fail-safe, disclaimer composition,
 * procedure injection, streaming accumulation, off-topic detection, updated decision,
 * and typed exceptions on failure.
 * ADR-003 §3/§5/§8; TAC-003-04/05/06/07.
 */
class OpenRouterDecisionAdapterTest {

    private MockWebServer mockWebServer;
    private OpenRouterDecisionAdapter adapter;
    private PromptProvider promptProvider;
    private PolicyProvider policyProvider;

    private static final String TEXT_MODEL = "test-text-model";
    private static final String COMPLAINT_PROCEDURE = "Procedura reklamacji: Punkt 1.1 ...";
    private static final String RETURN_PROCEDURE = "Procedura zwrotu: Punkt 2.1 ...";

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/").toString();

        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl)
                .apiKey("test-key")
                .timeout(Duration.ofSeconds(10))
                .maxRetries(0)
                .build();

        promptProvider = mock(PromptProvider.class);
        when(promptProvider.systemPrompt()).thenReturn("System prompt PL");
        when(promptProvider.decisionPrompt(CaseType.COMPLAINT)).thenReturn(
                "Decyzja reklamacji {{equipmentCategory}} {{modelName}} {{purchaseDate}} {{reason}} {{imageDescription}} {{procedureText}}");
        when(promptProvider.decisionPrompt(CaseType.RETURN)).thenReturn(
                "Decyzja zwrotu {{equipmentCategory}} {{modelName}} {{purchaseDate}} {{reason}} {{imageDescription}} {{procedureText}}");
        when(promptProvider.chatPrompt()).thenReturn("Chat system prompt");

        policyProvider = mock(PolicyProvider.class);
        when(policyProvider.procedureText(CaseType.COMPLAINT)).thenReturn(COMPLAINT_PROCEDURE);
        when(policyProvider.procedureText(CaseType.RETURN)).thenReturn(RETURN_PROCEDURE);

        OpenRouterProperties properties = new OpenRouterProperties(
                "test-key", baseUrl, TEXT_MODEL, "test-vision-model",
                "http://localhost:4200", "HW Service Copilot");

        adapter = new OpenRouterDecisionAdapter(client, properties, promptProvider, policyProvider);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    // ── Helper methods ─────────────────────────────────────────────────────────

    private CaseForm complaintForm() {
        return new CaseForm(CaseType.COMPLAINT, EquipmentCategory.LAPTOP,
                "ThinkPad X1", LocalDate.of(2023, 1, 15), "Uszkodzony ekran");
    }

    private CaseForm returnForm() {
        return new CaseForm(CaseType.RETURN, EquipmentCategory.SMARTPHONE,
                "Samsung S24", LocalDate.of(2024, 6, 1), null);
    }

    private ImageAnalysis defaultImageAnalysis() {
        return new ImageAnalysis("Urządzenie w złym stanie.", true, true, false, DecisionConfidence.HIGH);
    }

    private String buildDecisionJson(String outcome, String justification,
                                     String citedRule, String nextSteps, String confidence) {
        return "{"
                + "\"outcome\":\"" + outcome + "\","
                + "\"justification\":\"" + escapeJson(justification) + "\","
                + "\"citedRules\":[\"" + escapeJson(citedRule) + "\"],"
                + "\"nextSteps\":\"" + escapeJson(nextSteps) + "\","
                + "\"confidence\":\"" + confidence + "\""
                + "}";
    }

    private String buildChatCompletionJson(String content) {
        return "{"
                + "\"id\":\"chatcmpl-test\","
                + "\"object\":\"chat.completion\","
                + "\"created\":1700000000,"
                + "\"model\":\"" + TEXT_MODEL + "\","
                + "\"choices\":[{"
                + "\"index\":0,"
                + "\"message\":{\"role\":\"assistant\",\"content\":\"" + escapeJson(content) + "\"},"
                + "\"finish_reason\":\"stop\""
                + "}],"
                + "\"usage\":{\"prompt_tokens\":50,\"completion_tokens\":100,\"total_tokens\":150}"
                + "}";
    }

    /**
     * Builds a chunked SSE streaming response for MockWebServer.
     * Each token is sent as a separate Server-Sent Event chunk.
     */
    private String buildStreamingResponse(List<String> tokens) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            String escapedToken = escapeJson(token);
            sb.append("data: {\"id\":\"chatcmpl-stream\",\"object\":\"chat.completion.chunk\",\"created\":1700000000,\"model\":\"")
                    .append(TEXT_MODEL)
                    .append("\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"")
                    .append(escapedToken)
                    .append("\"},\"finish_reason\":null}]}\n\n");
        }
        // Final done chunk with finish_reason
        sb.append("data: {\"id\":\"chatcmpl-stream\",\"object\":\"chat.completion.chunk\",\"created\":1700000000,\"model\":\"")
                .append(TEXT_MODEL)
                .append("\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n");
        sb.append("data: [DONE]\n\n");
        return sb.toString();
    }

    private CaseSession buildSession(Decision decision) {
        ChatMessage decisionBubble = new ChatMessage(
                ChatMessage.Role.SYSTEM_ASSISTANT,
                decision.firstMessageMarkdown() != null ? decision.firstMessageMarkdown() : "Decyzja",
                Instant.now());
        return new CaseSession(
                UUID.randomUUID(),
                CaseType.COMPLAINT,
                complaintForm(),
                defaultImageAnalysis(),
                decision,
                List.of(decisionBubble),
                Instant.now(),
                Instant.now().plusSeconds(3600));
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    // ── DECIDE tests ───────────────────────────────────────────────────────────

    @Test
    void decide_validApproveResponse_returnsApproveDecision() {
        String decisionJson = buildDecisionJson(
                "APPROVE", "Warunki gwarancji spełnione.", "pkt 1.1 procedury", "Zaakceptuj i wyślij do serwisu.", "HIGH");
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(buildChatCompletionJson(decisionJson)));

        Decision result = adapter.decide(complaintForm(), defaultImageAnalysis(), COMPLAINT_PROCEDURE);

        assertThat(result.outcome()).isEqualTo(DecisionOutcome.APPROVE);
        assertThat(result.justification()).isEqualTo("Warunki gwarancji spełnione.");
        assertThat(result.citedRules()).containsExactly("pkt 1.1 procedury");
        assertThat(result.nextSteps()).isEqualTo("Zaakceptuj i wyślij do serwisu.");
        assertThat(result.confidence()).isEqualTo(DecisionConfidence.HIGH);
    }

    @Test
    void decide_validRejectResponse_returnsRejectDecision() {
        String decisionJson = buildDecisionJson(
                "REJECT", "Sprzęt uszkodzony mechanicznie.", "pkt 2.3 procedury", "Poinformuj klienta.", "MEDIUM");
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(buildChatCompletionJson(decisionJson)));

        Decision result = adapter.decide(complaintForm(), defaultImageAnalysis(), COMPLAINT_PROCEDURE);

        assertThat(result.outcome()).isEqualTo(DecisionOutcome.REJECT);
    }

    @Test
    void decide_validEscalateResponse_returnsEscalateDecision() {
        String decisionJson = buildDecisionJson(
                "ESCALATE", "Brak wystarczających danych.", "pkt 3.1 procedury", "Przekaż do przełożonego.", "LOW");
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(buildChatCompletionJson(decisionJson)));

        Decision result = adapter.decide(complaintForm(), defaultImageAnalysis(), COMPLAINT_PROCEDURE);

        assertThat(result.outcome()).isEqualTo(DecisionOutcome.ESCALATE);
    }

    @Test
    void decide_garbageResponse_coercedToEscalate() {
        // Non-JSON garbage — must not throw, must coerce to ESCALATE (TAC-003-04)
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(buildChatCompletionJson("To nie jest JSON, nie wiem co zrobić.")));

        Decision result = adapter.decide(complaintForm(), defaultImageAnalysis(), COMPLAINT_PROCEDURE);

        assertThat(result.outcome())
                .as("Garbage response must be coerced to ESCALATE — never throws")
                .isEqualTo(DecisionOutcome.ESCALATE);
    }

    @Test
    void decide_unknownOutcome_coercedToEscalate() {
        // JSON with unknown outcome value
        String decisionJson = "{\"outcome\":\"MAYBE\",\"justification\":\"Nieznana decyzja.\",\"citedRules\":[],\"nextSteps\":\"—\",\"confidence\":\"LOW\"}";
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(buildChatCompletionJson(decisionJson)));

        Decision result = adapter.decide(complaintForm(), defaultImageAnalysis(), COMPLAINT_PROCEDURE);

        assertThat(result.outcome())
                .as("Unknown outcome must be coerced to ESCALATE (TAC-003-04)")
                .isEqualTo(DecisionOutcome.ESCALATE);
    }

    @Test
    void decide_nonJsonEmptyContent_coercedToEscalate() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(buildChatCompletionJson("")));

        Decision result = adapter.decide(complaintForm(), defaultImageAnalysis(), COMPLAINT_PROCEDURE);

        assertThat(result.outcome())
                .as("Empty content must coerce to ESCALATE, not throw")
                .isEqualTo(DecisionOutcome.ESCALATE);
    }

    @Test
    void decide_firstMessageMarkdown_alwaysContainsDisclaimer() {
        // TAC-003-05: every decision's firstMessageMarkdown must include the Polish advisory disclaimer
        String decisionJson = buildDecisionJson("APPROVE", "Uzasadnienie OK.", "pkt 1.1", "Kolejne kroki.", "HIGH");
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(buildChatCompletionJson(decisionJson)));

        Decision result = adapter.decide(complaintForm(), defaultImageAnalysis(), COMPLAINT_PROCEDURE);

        assertThat(result.firstMessageMarkdown())
                .as("firstMessageMarkdown must always contain the advisory disclaimer (AC-26; TAC-003-05)")
                .contains("Zastrzeżenie");
        assertThat(result.firstMessageMarkdown())
                .as("Disclaimer must note advisory-only nature")
                .contains("doradczy");
    }

    @Test
    void decide_escalateFallback_firstMessageMarkdown_alsoHasDisclaimer() {
        // Even the ESCALATE fail-safe must include the disclaimer
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(buildChatCompletionJson("invalid json garbage")));

        Decision result = adapter.decide(complaintForm(), defaultImageAnalysis(), COMPLAINT_PROCEDURE);

        assertThat(result.outcome()).isEqualTo(DecisionOutcome.ESCALATE);
        assertThat(result.firstMessageMarkdown())
                .as("Coerced ESCALATE must still include the mandatory disclaimer")
                .contains("Zastrzeżenie");
    }

    @Test
    void decide_complaint_injectsComplaintProcedureText() throws InterruptedException {
        // TAC-003-02: COMPLAINT → complaint procedure text in the request body
        String decisionJson = buildDecisionJson("APPROVE", "OK.", "pkt 1.1", "Zatwierdź.", "HIGH");
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(buildChatCompletionJson(decisionJson)));

        adapter.decide(complaintForm(), defaultImageAnalysis(), COMPLAINT_PROCEDURE);

        RecordedRequest request = mockWebServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body)
                .as("COMPLAINT decision must inject the complaint procedure text (TAC-003-02)")
                .contains(escapeJson(COMPLAINT_PROCEDURE));
    }

    @Test
    void decide_return_injectsReturnProcedureText() throws InterruptedException {
        // TAC-003-02: RETURN → return procedure text in the request body
        String decisionJson = buildDecisionJson("APPROVE", "OK.", "pkt 2.1", "Zatwierdź zwrot.", "HIGH");
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(buildChatCompletionJson(decisionJson)));

        adapter.decide(returnForm(), defaultImageAnalysis(), RETURN_PROCEDURE);

        RecordedRequest request = mockWebServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body)
                .as("RETURN decision must inject the return procedure text (TAC-003-02)")
                .contains(escapeJson(RETURN_PROCEDURE));
    }

    @Test
    void decide_usesTextModelId() throws InterruptedException {
        String decisionJson = buildDecisionJson("APPROVE", "OK.", "pkt 1.1", "Zatwierdź.", "HIGH");
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(buildChatCompletionJson(decisionJson)));

        adapter.decide(complaintForm(), defaultImageAnalysis(), COMPLAINT_PROCEDURE);

        RecordedRequest request = mockWebServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body)
                .as("Decision call must use the text model ID (TAC-003-01)")
                .contains(TEXT_MODEL);
    }

    @Test
    void decide_serverError_throwsLlmUnavailableException() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"message\":\"Unavailable\",\"type\":\"server_error\",\"code\":null}}"));

        assertThatThrownBy(() -> adapter.decide(complaintForm(), defaultImageAnalysis(), COMPLAINT_PROCEDURE))
                .as("5xx response must raise LlmUnavailableException (TAC-003-07)")
                .isInstanceOf(LlmUnavailableException.class);
    }

    // ── STREAM REPLY tests ─────────────────────────────────────────────────────

    @Test
    void streamReply_pushesTokenDeltasInOrder() {
        List<String> tokens = List.of("To ", "jest ", "odpowiedź.");
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(buildStreamingResponse(tokens)));

        Decision decision = new Decision(DecisionOutcome.APPROVE, "OK", List.of(), "Kroki.", DecisionConfidence.HIGH,
                "Dzień dobry,\n\n✅ **Decyzja: ZATWIERDŹ**\n\n---\n> ⚠️ **Zastrzeżenie:** test");
        CaseSession session = buildSession(decision);

        List<String> receivedDeltas = new ArrayList<>();
        TokenSink sink = receivedDeltas::add;

        adapter.streamReply(session, "Mam pytanie.", sink);

        assertThat(receivedDeltas)
                .as("Deltas must arrive in the same order as streamed chunks (TAC-003-06)")
                .containsExactly("To ", "jest ", "odpowiedź.");
    }

    @Test
    void streamReply_accumulatesFullReply() {
        List<String> tokens = List.of("Pełna ", "odpowiedź ", "asystenta.");
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(buildStreamingResponse(tokens)));

        Decision decision = new Decision(DecisionOutcome.APPROVE, "OK", List.of(), "Kroki.", DecisionConfidence.HIGH,
                "Dzień dobry,\n\n✅ **Decyzja: ZATWIERDŹ**\n\n---\n> ⚠️ **Zastrzeżenie:** test");
        CaseSession session = buildSession(decision);

        ChatReply reply = adapter.streamReply(session, "Pytanie.", delta -> {});

        assertThat(reply.replyMarkdown())
                .as("Full accumulated reply must equal the concatenated deltas (TAC-003-06)")
                .isEqualTo("Pełna odpowiedź asystenta.");
    }

    @Test
    void streamReply_offTopicInput_offTopicFlagSet() {
        // Model includes [OFFTOPIC] marker when redirecting off-topic input
        List<String> tokens = List.of("[OFFTOPIC] Przepraszam, ", "to pytanie ", "nie dotyczy sprawy.");
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(buildStreamingResponse(tokens)));

        Decision decision = new Decision(DecisionOutcome.APPROVE, "OK", List.of(), "Kroki.", DecisionConfidence.HIGH,
                "Dzień dobry,\n\n✅ **Decyzja: ZATWIERDŹ**\n\n---\n> ⚠️ **Zastrzeżenie:** test");
        CaseSession session = buildSession(decision);

        ChatReply reply = adapter.streamReply(session, "Co to jest Python?", delta -> {});

        assertThat(reply.offTopic())
                .as("Off-topic input must set offTopic=true in the ChatReply")
                .isTrue();
        assertThat(reply.updatedDecision())
                .as("Off-topic reply must not produce an updatedDecision")
                .isNull();
    }

    @Test
    void streamReply_noOffTopicMarker_offTopicFalse() {
        List<String> tokens = List.of("Normalna odpowiedź na pytanie.");
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(buildStreamingResponse(tokens)));

        Decision decision = new Decision(DecisionOutcome.APPROVE, "OK", List.of(), "Kroki.", DecisionConfidence.HIGH,
                "Dzień dobry,\n\n✅ **Decyzja: ZATWIERDŹ**\n\n---\n> ⚠️ **Zastrzeżenie:** test");
        CaseSession session = buildSession(decision);

        ChatReply reply = adapter.streamReply(session, "Jak długo trwa naprawa?", delta -> {});

        assertThat(reply.offTopic()).isFalse();
    }

    @Test
    void streamReply_materialNewInfo_updatedDecisionPresent() {
        // Model signals a changed outcome via [UPDATED_DECISION:REJECT]
        List<String> tokens = List.of(
                "Na podstawie nowych informacji zmieniam decyzję. ",
                "[UPDATED_DECISION:REJECT] ",
                "Sprzęt był używany przez nieautoryzowany serwis.");
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(buildStreamingResponse(tokens)));

        Decision currentDecision = new Decision(DecisionOutcome.APPROVE, "OK", List.of(), "Kroki.", DecisionConfidence.HIGH,
                "Dzień dobry,\n\n✅ **Decyzja: ZATWIERDŹ**\n\n---\n> ⚠️ **Zastrzeżenie:** test");
        CaseSession session = buildSession(currentDecision);

        ChatReply reply = adapter.streamReply(session, "Sprzęt był serwisowany nieoficjalnie.", delta -> {});

        assertThat(reply.updatedDecision())
                .as("Material new info with decision marker must produce updatedDecision (TAC-003-06)")
                .isNotNull();
        assertThat(reply.updatedDecision().outcome())
                .isEqualTo(DecisionOutcome.REJECT);
        assertThat(reply.updatedDecision().firstMessageMarkdown())
                .as("Updated decision's firstMessageMarkdown must contain disclaimer")
                .contains("Zastrzeżenie");
    }

    @Test
    void streamReply_noUpdatedDecisionMarker_updatedDecisionNull() {
        List<String> tokens = List.of("Proszę czekać na informację z serwisu.");
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(buildStreamingResponse(tokens)));

        Decision decision = new Decision(DecisionOutcome.APPROVE, "OK", List.of(), "Kroki.", DecisionConfidence.HIGH,
                "Dzień dobry,\n\n✅ **Decyzja: ZATWIERDŹ**\n\n---\n> ⚠️ **Zastrzeżenie:** test");
        CaseSession session = buildSession(decision);

        ChatReply reply = adapter.streamReply(session, "Kiedy dostanę odpowiedź?", delta -> {});

        assertThat(reply.updatedDecision())
                .as("Normal Q&A without UPDATED_DECISION marker must not produce updatedDecision")
                .isNull();
    }

    @Test
    void streamReply_serverError_throwsLlmUnavailableException() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"message\":\"Unavailable\",\"type\":\"server_error\",\"code\":null}}"));

        Decision decision = new Decision(DecisionOutcome.APPROVE, "OK", List.of(), "Kroki.", DecisionConfidence.HIGH,
                "Dzień dobry,\n\n✅ **Decyzja: ZATWIERDŹ**\n\n---\n> ⚠️ **Zastrzeżenie:** test");
        CaseSession session = buildSession(decision);

        assertThatThrownBy(() -> adapter.streamReply(session, "Pytanie.", delta -> {}))
                .as("Pre-stream 5xx must raise LlmUnavailableException (TAC-003-07)")
                .isInstanceOf(LlmUnavailableException.class);
    }
}
