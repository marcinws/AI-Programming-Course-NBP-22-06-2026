package pl.nbp.copilot.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full backend integration test suite: controllers → services → adapters with MockWebServer
 * as the ONLY mock (stubbing OpenRouter). Covers all ADR-001 §8 test scenarios and
 * TAC-001-01..07, TAC-009, TAC-010.
 *
 * <p>Design notes:
 * <ul>
 *   <li>MockWebServer is started statically before the Spring context so that
 *       {@link DynamicPropertySource} can inject its URL as {@code openrouter.base-url}.</li>
 *   <li>Tests are ordered to ensure predictable queue consumption.</li>
 *   <li>{@code @BeforeEach} captures the cumulative request count so each test can
 *       assert a per-test delta rather than a global total.</li>
 *   <li>The SDK client is configured with {@code maxRetries=2}; tests that inject 5xx
 *       responses must enqueue {@code 1 + maxRetries = 3} responses to saturate the retry loop.</li>
 * </ul>
 *
 * <p>ADR-001 §8; ADR-000 §10; TAC-001-01..TAC-001-07; TAC-009; TAC-010.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BackendIntegrationTest {

    // ── Static MockWebServer — started before Spring context ─────────────────

    static MockWebServer mockWebServer;

    @BeforeAll
    static void startMockWebServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void stopMockWebServer() throws IOException {
        mockWebServer.shutdown();
    }

    /**
     * Injects the MockWebServer base URL so the OpenAI SDK client points at it.
     * TAC-009: this ensures all upstream SDK calls go through MockWebServer.
     */
    @DynamicPropertySource
    static void overrideOpenRouterProperties(DynamicPropertyRegistry registry) {
        registry.add("openrouter.base-url", () -> mockWebServer.url("/").toString());
        registry.add("openrouter.api-key", () -> "test-integration-key");
        registry.add("openrouter.text-model", () -> "test-text-model");
        registry.add("openrouter.vision-model", () -> "test-vision-model");
        // Large timeout — tests must not fail due to retry-induced timeouts
        registry.add("openai.request-timeout-ms", () -> "30000");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    /** Captures the cumulative MockWebServer request count before each test. */
    int requestCountBefore;

    @BeforeEach
    void captureRequestCount() {
        requestCountBefore = mockWebServer.getRequestCount();
    }

    @AfterEach
    void drainQueue() throws InterruptedException {
        // Drain any responses the test enqueued but didn't consume, to prevent bleed-over.
        // MockWebServer keeps unrecorded requests as a peek queue; we drain by taking from
        // the incoming queue with a short timeout until empty.
        RecordedRequest req;
        do {
            req = mockWebServer.takeRequest(50, TimeUnit.MILLISECONDS);
        } while (req != null);
    }

    // ── Test helpers ──────────────────────────────────────────────────────────

    /** Returns the number of upstream OpenRouter calls made during the current test. */
    private int upstreamCallsDelta() {
        return mockWebServer.getRequestCount() - requestCountBefore;
    }

    /** Builds a synthetic JPEG image. */
    private byte[] createJpeg(int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, w / 2, h);
        g.setColor(Color.RED);
        g.fillRect(w / 2, 0, w / 2, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    /** Builds a standard chat completion JSON response. */
    private String chatCompletionJson(String content) {
        String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
        return "{"
                + "\"id\":\"chatcmpl-test\","
                + "\"object\":\"chat.completion\","
                + "\"created\":1700000000,"
                + "\"model\":\"test-text-model\","
                + "\"choices\":[{"
                + "\"index\":0,"
                + "\"message\":{\"role\":\"assistant\",\"content\":\"" + escaped + "\"},"
                + "\"finish_reason\":\"stop\""
                + "}],"
                + "\"usage\":{\"prompt_tokens\":50,\"completion_tokens\":100,\"total_tokens\":150}"
                + "}";
    }

    /** Builds an OpenRouter decision JSON response. */
    private String decisionJson(String outcome, String justification, String nextSteps) {
        return "{"
                + "\"outcome\":\"" + outcome + "\","
                + "\"justification\":\"" + justification + "\","
                + "\"citedRules\":[\"pkt 1.1 procedury\"],"
                + "\"nextSteps\":\"" + nextSteps + "\","
                + "\"confidence\":\"HIGH\""
                + "}";
    }

    /**
     * Enqueues two MockWebServer responses: vision call then decision call.
     * These are the standard 2 upstream calls for a successful case creation.
     */
    private void enqueueVisionThenDecision(String imageDescription, String outcome,
                                           String justification, String nextSteps) {
        // Vision response (call 1)
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(chatCompletionJson(imageDescription)));
        // Decision response (call 2)
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(chatCompletionJson(decisionJson(outcome, justification, nextSteps))));
    }

    /**
     * Enqueues {@code count} 503 Service Unavailable error responses.
     * Use {@code count = 1 + MAX_RETRIES = 3} to saturate the SDK retry loop.
     */
    private void enqueue503Responses(int count) {
        for (int i = 0; i < count; i++) {
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(503)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"error\":{\"message\":\"Service Unavailable\","
                            + "\"type\":\"server_error\",\"code\":null}}"));
        }
    }

    /** Builds a streaming SSE response. */
    private String streamingResponse(String... tokens) {
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            String escaped = token.replace("\\", "\\\\").replace("\"", "\\\"");
            sb.append("data: {\"id\":\"chatcmpl-stream\",\"object\":\"chat.completion.chunk\","
                    + "\"created\":1700000000,\"model\":\"test-text-model\","
                    + "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"")
                    .append(escaped)
                    .append("\"},\"finish_reason\":null}]}\n\n");
        }
        sb.append("data: {\"id\":\"chatcmpl-stream\",\"object\":\"chat.completion.chunk\","
                + "\"created\":1700000000,\"model\":\"test-text-model\","
                + "\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n");
        sb.append("data: [DONE]\n\n");
        return sb.toString();
    }

    /** Creates a case and returns the sessionId. Drains the 2 upstream requests from MockWebServer. */
    private String createCaseAndGetSessionId(String caseType, String reason) throws Exception {
        enqueueVisionThenDecision(
                "Urządzenie w złym stanie.",
                "REJECT",
                "Uszkodzenie mechaniczne.",
                "Poinformuj klienta.");

        byte[] jpeg = createJpeg(100, 100);
        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "test.jpg", "image/jpeg", jpeg);

        var request = multipart("/api/cases")
                .file(imageFile)
                .param("caseType", caseType)
                .param("equipmentCategory", "LAPTOP")
                .param("modelName", "TestModel X1")
                .param("purchaseDate", "2024-01-15");

        if (reason != null) {
            request = multipart("/api/cases")
                    .file(imageFile)
                    .param("caseType", caseType)
                    .param("equipmentCategory", "LAPTOP")
                    .param("modelName", "TestModel X1")
                    .param("purchaseDate", "2024-01-15")
                    .param("reason", reason);
        }

        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andReturn();

        // Drain the 2 upstream requests consumed by case creation
        mockWebServer.takeRequest(5, TimeUnit.SECONDS);
        mockWebServer.takeRequest(5, TimeUnit.SECONDS);

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("sessionId").asText();
    }

    // ── Scenario 1: Valid COMPLAINT (JPEG) → 201, decision + firstMessageMarkdown ──

    @Test
    @Order(1)
    void scenario1_validComplaint_jpeg_returns201WithDecisionAndDisclaimer() throws Exception {
        enqueueVisionThenDecision(
                "Urządzenie w złym stanie, widoczne uszkodzenia.",
                "APPROVE",
                "Warunki gwarancji spełnione.",
                "Zaakceptuj i wyślij do serwisu.");

        byte[] jpeg = createJpeg(200, 150);
        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "test.jpg", "image/jpeg", jpeg);

        MvcResult result = mockMvc.perform(multipart("/api/cases")
                        .file(imageFile)
                        .param("caseType", "COMPLAINT")
                        .param("equipmentCategory", "LAPTOP")
                        .param("modelName", "ThinkPad X1")
                        .param("purchaseDate", "2024-01-15")
                        .param("reason", "Uszkodzony ekran, nie uruchamia się."))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.decision.outcome").value("APPROVE"))
                .andExpect(jsonPath("$.decision.justification").isNotEmpty())
                .andExpect(jsonPath("$.decision.firstMessageMarkdown").isNotEmpty())
                .andReturn();

        // Exactly 2 upstream calls: vision + text (TAC-009)
        assertThat(upstreamCallsDelta())
                .as("Scenario 1: exactly 2 OpenRouter calls (vision + decision)")
                .isEqualTo(2);

        // firstMessageMarkdown must contain the Polish advisory disclaimer (AC-26)
        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        String firstMsg = json.path("decision").path("firstMessageMarkdown").asText();
        assertThat(firstMsg)
                .as("firstMessageMarkdown must contain Polish disclaimer")
                .contains("Zastrzeżenie");
    }

    // ── Scenario 2: Valid RETURN (no reason) → 201 ───────────────────────────

    @Test
    @Order(2)
    void scenario2_validReturn_noReason_returns201() throws Exception {
        enqueueVisionThenDecision(
                "Urządzenie w dobrym stanie, brak uszkodzeń.",
                "APPROVE",
                "Zwrot w terminie, produkt nienaruszony.",
                "Przyjmij zwrot i zainicjuj procedurę zwrotu środków.");

        byte[] jpeg = createJpeg(150, 100);
        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "product.jpg", "image/jpeg", jpeg);

        mockMvc.perform(multipart("/api/cases")
                        .file(imageFile)
                        .param("caseType", "RETURN")
                        .param("equipmentCategory", "SMARTPHONE")
                        .param("modelName", "Samsung Galaxy S24")
                        .param("purchaseDate", "2025-03-10"))
                // No 'reason' param — RETURN does not require reason
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.decision.outcome").value("APPROVE"));

        // Exactly 2 upstream calls: vision + text
        assertThat(upstreamCallsDelta())
                .as("Scenario 2: exactly 2 OpenRouter calls for RETURN case")
                .isEqualTo(2);
    }

    // ── Scenario 3: Missing required fields → 400 + fieldErrors, zero LLM calls ──

    @Test
    @Order(3)
    void scenario3a_complaint_missingReason_returns400_zeroLlmCalls() throws Exception {
        // TAC-001-01: COMPLAINT without reason → 400, zero LLM calls
        byte[] jpeg = createJpeg(100, 100);
        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "test.jpg", "image/jpeg", jpeg);

        mockMvc.perform(multipart("/api/cases")
                        .file(imageFile)
                        .param("caseType", "COMPLAINT")
                        .param("equipmentCategory", "LAPTOP")
                        .param("modelName", "ThinkPad X1")
                        .param("purchaseDate", "2024-01-15"))
                // No reason provided for COMPLAINT
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.reason").isNotEmpty());

        assertThat(upstreamCallsDelta())
                .as("TAC-001-01: zero OpenRouter calls on validation failure (missing reason)")
                .isEqualTo(0);
    }

    @Test
    @Order(4)
    void scenario3b_missingModelName_returns400_zeroLlmCalls() throws Exception {
        // TAC-001-01: missing modelName → 400
        byte[] jpeg = createJpeg(100, 100);
        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "test.jpg", "image/jpeg", jpeg);

        mockMvc.perform(multipart("/api/cases")
                        .file(imageFile)
                        .param("caseType", "RETURN")
                        .param("equipmentCategory", "LAPTOP")
                        // modelName omitted
                        .param("purchaseDate", "2024-01-15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.modelName").isNotEmpty());

        assertThat(upstreamCallsDelta())
                .as("TAC-001-01: zero OpenRouter calls on validation failure (missing modelName)")
                .isEqualTo(0);
    }

    @Test
    @Order(5)
    void scenario3c_futureDate_returns400_zeroLlmCalls() throws Exception {
        // TAC-001-01: future purchaseDate → 400
        byte[] jpeg = createJpeg(100, 100);
        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "test.jpg", "image/jpeg", jpeg);

        mockMvc.perform(multipart("/api/cases")
                        .file(imageFile)
                        .param("caseType", "RETURN")
                        .param("equipmentCategory", "LAPTOP")
                        .param("modelName", "MacBook Pro")
                        .param("purchaseDate", "2099-12-31"))  // future date
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.purchaseDate").isNotEmpty());

        assertThat(upstreamCallsDelta())
                .as("TAC-001-01: zero OpenRouter calls on validation failure (future date)")
                .isEqualTo(0);
    }

    // ── Scenario 4a: No image → 400 ──────────────────────────────────────────

    @Test
    @Order(6)
    void scenario4a_noImage_returns400_zeroLlmCalls() throws Exception {
        // TAC-001-02: missing image → 400
        mockMvc.perform(multipart("/api/cases")
                        .param("caseType", "RETURN")
                        .param("equipmentCategory", "LAPTOP")
                        .param("modelName", "MacBook Pro")
                        .param("purchaseDate", "2024-01-15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.image").isNotEmpty());

        assertThat(upstreamCallsDelta())
                .as("TAC-001-02: zero OpenRouter calls when no image provided")
                .isEqualTo(0);
    }

    // ── Scenario 4b: Oversized image → 413 ───────────────────────────────────

    @Test
    @Order(7)
    void scenario4b_oversizedImage_returns413_zeroLlmCalls() throws Exception {
        // TAC-001-02: image > 10MB → 413
        byte[] oversized = new byte[10_485_761]; // 1 byte over 10MB limit
        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "big.jpg", "image/jpeg", oversized);

        mockMvc.perform(multipart("/api/cases")
                        .file(imageFile)
                        .param("caseType", "RETURN")
                        .param("equipmentCategory", "LAPTOP")
                        .param("modelName", "MacBook Pro")
                        .param("purchaseDate", "2024-01-15"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"));

        assertThat(upstreamCallsDelta())
                .as("TAC-001-02: zero OpenRouter calls when image oversized → 413")
                .isEqualTo(0);
    }

    // ── Scenario 4c: Wrong MIME type → 415 ───────────────────────────────────

    @Test
    @Order(8)
    void scenario4c_wrongMimeType_returns415_zeroLlmCalls() throws Exception {
        // TAC-001-02: GIF type → 415
        MockMultipartFile gifFile = new MockMultipartFile(
                "image", "anim.gif", "image/gif", new byte[]{0x47, 0x49, 0x46, 0x38});

        mockMvc.perform(multipart("/api/cases")
                        .file(gifFile)
                        .param("caseType", "RETURN")
                        .param("equipmentCategory", "LAPTOP")
                        .param("modelName", "MacBook Pro")
                        .param("purchaseDate", "2024-01-15"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));

        assertThat(upstreamCallsDelta())
                .as("TAC-001-02: zero OpenRouter calls when MIME type unsupported → 415")
                .isEqualTo(0);
    }

    // ── Scenario 5a: LLM unavailable (vision 5xx) → 502 ─────────────────────

    @Test
    @Order(9)
    void scenario5a_llmUnavailable_visionFails_returns502() throws Exception {
        // Vision call returns 503 after retries — must surface as 502 LLM_UNAVAILABLE.
        // SDK maxRetries=2 → 3 total attempts. Enqueue 3 error responses.
        enqueue503Responses(3);

        byte[] jpeg = createJpeg(100, 100);
        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "test.jpg", "image/jpeg", jpeg);

        mockMvc.perform(multipart("/api/cases")
                        .file(imageFile)
                        .param("caseType", "RETURN")
                        .param("equipmentCategory", "LAPTOP")
                        .param("modelName", "MacBook Pro")
                        .param("purchaseDate", "2024-01-15"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("LLM_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        // At least 1 upstream attempt was made (retries bring it to 3)
        assertThat(upstreamCallsDelta())
                .as("At least 1 upstream attempt must have been made for vision failure")
                .isGreaterThanOrEqualTo(1);
    }

    // ── Scenario 5b: Vision OK, text model fails → 502 ───────────────────────

    @Test
    @Order(10)
    void scenario5b_visionOk_textFails_returns502() throws Exception {
        // Vision succeeds
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(chatCompletionJson("Urządzenie w dobrym stanie.")));
        // Text/decision call fails — enqueue 3 for retries
        enqueue503Responses(3);

        byte[] jpeg = createJpeg(100, 100);
        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "test.jpg", "image/jpeg", jpeg);

        mockMvc.perform(multipart("/api/cases")
                        .file(imageFile)
                        .param("caseType", "RETURN")
                        .param("equipmentCategory", "LAPTOP")
                        .param("modelName", "MacBook Pro")
                        .param("purchaseDate", "2024-01-15"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("LLM_UNAVAILABLE"));

        // At least 2 upstream attempts: 1 vision success + ≥1 text failure
        assertThat(upstreamCallsDelta())
                .as("Vision OK + text fail: at least 2 upstream calls")
                .isGreaterThanOrEqualTo(2);
    }

    // ── Scenario 6: Chat SSE happy path ──────────────────────────────────────

    @Test
    @Order(11)
    void scenario6_chatSse_happyPath_streamsTokensAndDone_andHistoryAppended() throws Exception {
        // Create a session — this drains 2 upstream requests internally
        String sessionId = createCaseAndGetSessionId("COMPLAINT", "Ekran pęknięty po upadku.");

        // Reset counter after session creation (already drained in helper)
        requestCountBefore = mockWebServer.getRequestCount();

        // Enqueue streaming chat response
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(streamingResponse("Rozumiem ", "Pana pytanie.", " Proszę czekać.")));

        // Perform chat request — SSE via MockMvc async dispatch
        MvcResult asyncResult = mockMvc.perform(post("/api/cases/" + sessionId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Kiedy mogę oczekiwać decyzji?\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Dispatch the async response and collect the SSE body
        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM_VALUE));

        String sseBody = asyncResult.getResponse().getContentAsString();

        // TAC-001-05: at least one token event
        assertThat(sseBody)
                .as("SSE stream must contain at least one token event")
                .contains("event:token");
        // TAC-001-05: terminal done event
        assertThat(sseBody)
                .as("SSE stream must contain a terminal done event")
                .contains("event:done");
        // TAC-001-05: no error event in happy path
        assertThat(sseBody)
                .as("SSE stream must not contain an error event in happy path")
                .doesNotContain("event:error");

        // Exactly 1 upstream streaming call for the chat turn
        assertThat(upstreamCallsDelta())
                .as("Chat SSE happy path: exactly 1 upstream LLM call")
                .isEqualTo(1);

        // History must be appended — GET /api/cases/{id} shows ≥2 messages
        mockMvc.perform(get("/api/cases/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages").isArray())
                .andExpect(jsonPath("$.messages.length()").value(greaterThanOrEqualTo(2)));
    }

    // ── Scenario 7: Chat mid-stream error → error event, session not corrupted ──

    @Test
    @Order(12)
    void scenario7_chatMidStreamError_sendsErrorEventAndSessionNotCorrupted() throws Exception {
        // Create a session
        String sessionId = createCaseAndGetSessionId("COMPLAINT", "Aparat nie działa.");
        requestCountBefore = mockWebServer.getRequestCount();

        // Enqueue 3 503 responses for the chat call (retries)
        enqueue503Responses(3);

        MvcResult asyncResult = mockMvc.perform(post("/api/cases/" + sessionId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Mam dodatkowe pytanie.\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk());

        String sseBody = asyncResult.getResponse().getContentAsString();

        // TAC-001-05: error event must be emitted mid-stream
        assertThat(sseBody)
                .as("Mid-stream error must produce an 'error' SSE event")
                .contains("event:error");

        // TAC-001-05: session must not be corrupted — GET still returns the session
        mockMvc.perform(get("/api/cases/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.decision").isNotEmpty());
    }

    // ── Scenario 8: Unknown/expired session → 404 JSON ───────────────────────

    @Test
    @Order(13)
    void scenario8a_unknownSession_chat_returns404Json_noStream() throws Exception {
        // TAC-001-07: unknown sessionId on chat → JSON 404, never opens SSE stream
        String fakeId = "00000000-0000-0000-0000-000000000000";

        mockMvc.perform(post("/api/cases/" + fakeId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Test\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));

        assertThat(upstreamCallsDelta())
                .as("TAC-001-07: zero OpenRouter calls for chat on missing session")
                .isEqualTo(0);
    }

    @Test
    @Order(14)
    void scenario8b_unknownSession_get_returns404Json() throws Exception {
        // TAC-001-07: unknown sessionId on GET → 404
        String fakeId = "00000000-0000-0000-0000-000000000001";

        mockMvc.perform(get("/api/cases/" + fakeId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));

        assertThat(upstreamCallsDelta())
                .as("TAC-001-07: zero OpenRouter calls for GET on missing session")
                .isEqualTo(0);
    }

    // ── Scenario 9: GET /api/metadata → 200, Polish labels ───────────────────

    @Test
    @Order(15)
    void scenario9_metadata_returns200WithPolishLabels() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/metadata"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.caseTypes").isArray())
                .andExpect(jsonPath("$.equipmentCategories").isArray())
                .andExpect(jsonPath("$.imageConstraints.maxBytes").isNumber())
                .andExpect(jsonPath("$.imageConstraints.acceptedTypes").isArray())
                .andReturn();

        // Verify Polish labels are present on caseTypes
        String body = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);

        JsonNode caseTypes = json.path("caseTypes");
        assertThat(caseTypes.isArray()).isTrue();
        assertThat(caseTypes.size()).isGreaterThan(0);
        for (JsonNode ct : caseTypes) {
            assertThat(ct.path("labelPl").asText())
                    .as("caseType.labelPl must be present and non-empty (Polish label)")
                    .isNotBlank();
        }

        // Verify Polish labels on equipmentCategories
        JsonNode categories = json.path("equipmentCategories");
        assertThat(categories.isArray()).isTrue();
        assertThat(categories.size()).isGreaterThan(0);
        for (JsonNode cat : categories) {
            assertThat(cat.path("labelPl").asText())
                    .as("equipmentCategory.labelPl must be present and non-empty (Polish label)")
                    .isNotBlank();
        }

        // No OpenRouter calls for metadata
        assertThat(upstreamCallsDelta())
                .as("Metadata endpoint must make zero upstream LLM calls")
                .isEqualTo(0);
    }

    // ── Scenario 10: CORS preflight ───────────────────────────────────────────

    @Test
    @Order(16)
    void scenario10_cors_allowedOrigin_isPermitted() throws Exception {
        // TAC-010: the configured SPA origin must receive Access-Control-Allow-Origin
        mockMvc.perform(options("/api/metadata")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    @Test
    @Order(17)
    void scenario10_cors_otherOrigin_isRejected() throws Exception {
        // TAC-010: an unapproved origin must NOT receive Access-Control-Allow-Origin
        mockMvc.perform(options("/api/metadata")
                        .header("Origin", "http://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // ── TAC-009: SDK wired to correct base-URL, API key and model IDs ─────────

    @Test
    @Order(18)
    void tac09_sdkUsesConfiguredBaseUrl_ApiKey_andModelIds() throws Exception {
        // TAC-009: Verify the OpenAI SDK client is constructed with the configured
        // base-URL (pointed at MockWebServer via DynamicPropertySource), API key,
        // and correct model IDs in the request body.

        enqueueVisionThenDecision(
                "Tablet wyglądający na uszkodzony.",
                "APPROVE",
                "Gwarancja obejmuje wadę fabryczną.",
                "Wyślij do serwisu gwarancyjnego.");

        byte[] jpeg = createJpeg(100, 100);
        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "tablet.jpg", "image/jpeg", jpeg);

        mockMvc.perform(multipart("/api/cases")
                        .file(imageFile)
                        .param("caseType", "RETURN")
                        .param("equipmentCategory", "TABLET")
                        .param("modelName", "iPad Air")
                        .param("purchaseDate", "2024-05-01"))
                .andExpect(status().isCreated());

        // Inspect the vision request (call 1)
        RecordedRequest req1 = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
        assertThat(req1)
                .as("TAC-009: Vision request must have reached the MockWebServer base URL")
                .isNotNull();

        // TAC-009: Authorization header must carry the configured API key
        assertThat(req1.getHeader("Authorization"))
                .as("TAC-009: SDK must send 'Bearer test-integration-key' in Authorization header")
                .isEqualTo("Bearer test-integration-key");

        // TAC-009: Request must target the Chat Completions endpoint
        assertThat(req1.getPath())
                .as("TAC-009: SDK must call /v1/chat/completions (or compatible path)")
                .contains("chat/completions");

        // TAC-009: Vision request must use the configured vision model ID
        String body1 = req1.getBody().readUtf8();
        assertThat(body1)
                .as("TAC-009: Vision request body must contain configured vision model ID 'test-vision-model'")
                .contains("test-vision-model");

        // Inspect the decision request (call 2)
        RecordedRequest req2 = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
        assertThat(req2)
                .as("TAC-009: Decision request must have reached MockWebServer")
                .isNotNull();

        String body2 = req2.getBody().readUtf8();
        assertThat(body2)
                .as("TAC-009: Decision request body must contain configured text model ID 'test-text-model'")
                .contains("test-text-model");
    }
}
