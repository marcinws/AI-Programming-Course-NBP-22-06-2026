package pl.nbp.copilot.integration;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.nbp.copilot.application.port.PromptProvider;
import pl.nbp.copilot.domain.CaseForm;
import pl.nbp.copilot.domain.CaseType;
import pl.nbp.copilot.domain.DecisionConfidence;
import pl.nbp.copilot.domain.EquipmentCategory;
import pl.nbp.copilot.domain.ImageAnalysis;
import pl.nbp.copilot.integration.exception.LlmUnavailableException;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link OpenRouterVisionAdapter} against MockWebServer.
 * Verifies: correct model used, correct prompt per case type, image part present as base64,
 * response parsed to ImageAnalysis, null flags on missing fields, typed exceptions on failure.
 * ADR-003 §3/§5/§8; TAC-003-01/02/03.
 */
class OpenRouterVisionAdapterTest {

    private MockWebServer mockWebServer;
    private OpenRouterVisionAdapter adapter;
    private PromptProvider promptProvider;

    private static final String VISION_MODEL = "test-vision-model";
    private static final byte[] TEST_IMAGE_BYTES = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final String TEST_MIME = "image/jpeg";

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/").toString();

        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl)
                .apiKey("test-key")
                .timeout(Duration.ofSeconds(5))
                .maxRetries(0)
                .build();

        promptProvider = mock(PromptProvider.class);
        when(promptProvider.systemPrompt()).thenReturn("System prompt PL");
        when(promptProvider.analysisPrompt(CaseType.COMPLAINT)).thenReturn("Analiza reklamacji");
        when(promptProvider.analysisPrompt(CaseType.RETURN)).thenReturn("Analiza zwrotu");

        OpenRouterProperties properties = new OpenRouterProperties(
                "test-key", baseUrl, "test-text-model", VISION_MODEL,
                "http://localhost:4200", "HW Service Copilot");

        adapter = new OpenRouterVisionAdapter(client, properties, promptProvider);
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

    private void enqueueAnalysisResponse(String description, Boolean damageObserved,
                                         Boolean signsOfUse, Boolean usableForResale,
                                         String confidence) {
        String jsonContent = buildAnalysisJson(description, damageObserved, signsOfUse, usableForResale, confidence);
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(buildChatCompletionJson(jsonContent)));
    }

    private String buildAnalysisJson(String description, Boolean damageObserved,
                                     Boolean signsOfUse, Boolean usableForResale,
                                     String confidence) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"description\":\"").append(escapeJson(description)).append("\"");
        if (damageObserved != null) sb.append(",\"damageObserved\":").append(damageObserved);
        if (signsOfUse != null) sb.append(",\"signsOfUse\":").append(signsOfUse);
        if (usableForResale != null) sb.append(",\"usableForResale\":").append(usableForResale);
        if (confidence != null) sb.append(",\"confidence\":\"").append(confidence).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private String buildChatCompletionJson(String content) {
        String escaped = escapeJson(content);
        return "{"
                + "\"id\":\"chatcmpl-test\","
                + "\"object\":\"chat.completion\","
                + "\"created\":1700000000,"
                + "\"model\":\"" + VISION_MODEL + "\","
                + "\"choices\":[{"
                + "\"index\":0,"
                + "\"message\":{\"role\":\"assistant\",\"content\":\"" + escaped + "\"},"
                + "\"finish_reason\":\"stop\""
                + "}],"
                + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":20,\"total_tokens\":30}"
                + "}";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    void analyze_complaint_usesVisionModelId() throws InterruptedException {
        enqueueAnalysisResponse("Opis stanu urządzenia", true, true, false, "HIGH");

        adapter.analyze(CaseType.COMPLAINT, TEST_IMAGE_BYTES, TEST_MIME, complaintForm());

        RecordedRequest request = mockWebServer.takeRequest();
        String requestBody = request.getBody().readUtf8();
        assertThat(requestBody)
                .as("Request must reference the vision model ID")
                .contains(VISION_MODEL);
    }

    @Test
    void analyze_complaint_usesComplaintAnalysisPrompt() throws InterruptedException {
        enqueueAnalysisResponse("Opis", null, null, null, null);

        adapter.analyze(CaseType.COMPLAINT, TEST_IMAGE_BYTES, TEST_MIME, complaintForm());

        RecordedRequest request = mockWebServer.takeRequest();
        String requestBody = request.getBody().readUtf8();
        assertThat(requestBody)
                .as("Complaint analysis prompt must be sent for COMPLAINT case type")
                .contains("Analiza reklamacji");
    }

    @Test
    void analyze_return_usesReturnAnalysisPrompt() throws InterruptedException {
        enqueueAnalysisResponse("Opis zwrotu", null, null, null, null);

        adapter.analyze(CaseType.RETURN, TEST_IMAGE_BYTES, TEST_MIME, returnForm());

        RecordedRequest request = mockWebServer.takeRequest();
        String requestBody = request.getBody().readUtf8();
        assertThat(requestBody)
                .as("Return analysis prompt must be sent for RETURN case type")
                .contains("Analiza zwrotu");
    }

    @Test
    void analyze_requestContainsBase64ImagePart() throws InterruptedException {
        enqueueAnalysisResponse("Opis", null, null, null, null);

        adapter.analyze(CaseType.COMPLAINT, TEST_IMAGE_BYTES, TEST_MIME, complaintForm());

        RecordedRequest request = mockWebServer.takeRequest();
        String requestBody = request.getBody().readUtf8();
        assertThat(requestBody)
                .as("Request must contain base64 data URL for the image")
                .contains("data:image/jpeg;base64,");
    }

    @Test
    void analyze_returnsImageAnalysisWithAllFields() {
        enqueueAnalysisResponse("Laptop z widocznym pęknięciem ekranu.", true, true, false, "HIGH");

        ImageAnalysis result = adapter.analyze(CaseType.COMPLAINT, TEST_IMAGE_BYTES, TEST_MIME, complaintForm());

        assertThat(result.description()).isEqualTo("Laptop z widocznym pęknięciem ekranu.");
        assertThat(result.damageObserved()).isTrue();
        assertThat(result.signsOfUse()).isTrue();
        assertThat(result.usableForResale()).isFalse();
        assertThat(result.confidence()).isEqualTo(DecisionConfidence.HIGH);
    }

    @Test
    void analyze_noOutcomeField_returnDescribeOnly() {
        // Vision model must never return approve/reject — it only describes (TAC-003-03)
        enqueueAnalysisResponse("Telefon w dobrym stanie.", false, false, true, "MEDIUM");

        ImageAnalysis result = adapter.analyze(CaseType.RETURN, TEST_IMAGE_BYTES, TEST_MIME, returnForm());

        // ImageAnalysis has no outcome field — verified by compiler (describe-only structure)
        assertThat(result.description()).isNotBlank();
        assertThat(result.confidence()).isEqualTo(DecisionConfidence.MEDIUM);
    }

    @Test
    void analyze_missingFlagsInResponse_nullFlagsInResult() {
        enqueueAnalysisResponse("Opis urządzenia bez flag.", null, null, null, null);

        ImageAnalysis result = adapter.analyze(CaseType.COMPLAINT, TEST_IMAGE_BYTES, TEST_MIME, complaintForm());

        assertThat(result.description()).isEqualTo("Opis urządzenia bez flag.");
        assertThat(result.damageObserved())
                .as("Missing damageObserved in response must result in null (unknown)")
                .isNull();
        assertThat(result.signsOfUse())
                .as("Missing signsOfUse in response must result in null (unknown)")
                .isNull();
        assertThat(result.usableForResale())
                .as("Missing usableForResale in response must result in null (unknown)")
                .isNull();
        assertThat(result.confidence())
                .as("Missing confidence in response must result in null")
                .isNull();
    }

    @Test
    void analyze_plainTextResponse_usedAsDescription() {
        String plainDescription = "Urządzenie ma rysy na ekranie i obudowie.";
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(buildChatCompletionJson(plainDescription)));

        ImageAnalysis result = adapter.analyze(CaseType.COMPLAINT, TEST_IMAGE_BYTES, TEST_MIME, complaintForm());

        assertThat(result.description()).isEqualTo(plainDescription);
        assertThat(result.damageObserved()).isNull();
        assertThat(result.signsOfUse()).isNull();
        assertThat(result.usableForResale()).isNull();
        assertThat(result.confidence()).isNull();
    }

    @Test
    void analyze_serverError_throwsLlmUnavailableException() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"message\":\"Service unavailable\",\"type\":\"server_error\",\"code\":null}}"));

        assertThatThrownBy(() -> adapter.analyze(CaseType.COMPLAINT, TEST_IMAGE_BYTES, TEST_MIME, complaintForm()))
                .as("5xx response must raise LlmUnavailableException")
                .isInstanceOf(LlmUnavailableException.class);
    }

    @Test
    void analyze_requestContainsSystemPrompt() throws InterruptedException {
        enqueueAnalysisResponse("Opis", null, null, null, null);

        adapter.analyze(CaseType.COMPLAINT, TEST_IMAGE_BYTES, TEST_MIME, complaintForm());

        RecordedRequest request = mockWebServer.takeRequest();
        String requestBody = request.getBody().readUtf8();
        assertThat(requestBody)
                .as("System prompt must be included in the request")
                .contains("System prompt PL");
    }
}
