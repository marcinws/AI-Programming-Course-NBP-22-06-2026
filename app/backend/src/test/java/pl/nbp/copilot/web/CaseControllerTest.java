package pl.nbp.copilot.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for POST /api/cases and GET /api/cases/{id}.
 * Only the OpenRouter endpoint (MockWebServer) is stubbed — all other dependencies are real.
 * ADR-001 §5/§8; TAC-001-01/02/06.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CaseControllerTest {

    private static MockWebServer mockWebServer;

    @BeforeAll
    static void startMockWebServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void stopMockWebServer() throws IOException {
        mockWebServer.shutdown();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String baseUrl = mockWebServer.url("/").toString();
        registry.add("openrouter.base-url", () -> baseUrl);
        registry.add("openrouter.api-key", () -> "test-key");
        registry.add("openrouter.text-model", () -> "test-text-model");
        registry.add("openrouter.vision-model", () -> "test-vision-model");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Creates a valid 1x1 JPEG image for use in tests. */
    private static byte[] minimalJpeg() throws IOException {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0xFF0000); // red pixel
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", baos);
        return baos.toByteArray();
    }

    private void enqueueVisionResponse() {
        String analysisJson = "{\"description\":\"Urządzenie w stanie uszkodzonym.\",\"damageObserved\":true,\"signsOfUse\":true,\"usableForResale\":false,\"confidence\":\"HIGH\"}";
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(buildChatCompletionJson(analysisJson, "test-vision-model")));
    }

    private void enqueueDecisionResponse(String outcome) {
        String decisionJson = "{\"outcome\":\"" + outcome + "\","
                + "\"justification\":\"Warunki gwarancji spełnione.\","
                + "\"citedRules\":[\"pkt 1.1 procedury\"],"
                + "\"nextSteps\":\"Zaakceptuj i wyślij do serwisu.\","
                + "\"confidence\":\"HIGH\"}";
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(buildChatCompletionJson(decisionJson, "test-text-model")));
    }

    private String buildChatCompletionJson(String content, String model) {
        String escaped = escapeJson(content);
        return "{"
                + "\"id\":\"chatcmpl-test\","
                + "\"object\":\"chat.completion\","
                + "\"created\":1700000000,"
                + "\"model\":\"" + model + "\","
                + "\"choices\":[{"
                + "\"index\":0,"
                + "\"message\":{\"role\":\"assistant\",\"content\":\"" + escaped + "\"},"
                + "\"finish_reason\":\"stop\""
                + "}],"
                + "\"usage\":{\"prompt_tokens\":50,\"completion_tokens\":100,\"total_tokens\":150}"
                + "}";
    }

    private String escapeJson(String v) {
        return v.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    // ── POST /api/cases — happy paths ─────────────────────────────────────────

    @Test
    void createCase_validComplaint_returns201WithDecision() throws Exception {
        enqueueVisionResponse();
        enqueueDecisionResponse("APPROVE");

        MockMultipartFile image = new MockMultipartFile(
                "image", "photo.jpg", "image/jpeg", minimalJpeg());

        MvcResult result = mockMvc.perform(multipart("/api/cases")
                        .file(image)
                        .param("caseType", "COMPLAINT")
                        .param("equipmentCategory", "LAPTOP")
                        .param("modelName", "ThinkPad X1")
                        .param("purchaseDate", "2023-01-15")
                        .param("reason", "Uszkodzony ekran"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("sessionId").asText()).isNotBlank();
        assertThat(body.path("decision").path("outcome").asText()).isEqualTo("APPROVE");
        assertThat(body.path("decision").path("firstMessageMarkdown").asText()).isNotBlank();
        assertThat(body.path("imageAnalysisSummary").asText()).isNotBlank();
    }

    @Test
    void createCase_validReturn_returns201() throws Exception {
        enqueueVisionResponse();
        enqueueDecisionResponse("APPROVE");

        MockMultipartFile image = new MockMultipartFile(
                "image", "photo.jpg", "image/jpeg", minimalJpeg());

        MvcResult result = mockMvc.perform(multipart("/api/cases")
                        .file(image)
                        .param("caseType", "RETURN")
                        .param("equipmentCategory", "SMARTPHONE")
                        .param("modelName", "Samsung S24")
                        .param("purchaseDate", "2024-01-01"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("sessionId").asText()).isNotBlank();
        // RETURN without reason is valid — no LLM count assertion needed here
    }

    // ── POST /api/cases — validation failures (zero LLM calls) ───────────────

    @Test
    void createCase_missingReasonForComplaint_returns400WithFieldErrors() throws Exception {
        // TAC-001-01: validation before any LLM call; drain the queue counter before and after
        int requestsBefore = mockWebServer.getRequestCount();

        MockMultipartFile image = new MockMultipartFile(
                "image", "photo.jpg", "image/jpeg", minimalJpeg());

        mockMvc.perform(multipart("/api/cases")
                        .file(image)
                        .param("caseType", "COMPLAINT")
                        .param("equipmentCategory", "LAPTOP")
                        .param("modelName", "ThinkPad X1")
                        .param("purchaseDate", "2023-01-15"))
                // no reason — COMPLAINT requires it
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").exists());

        int requestsAfter = mockWebServer.getRequestCount();
        assertThat(requestsAfter).as("Zero LLM calls on validation failure (TAC-001-01)")
                .isEqualTo(requestsBefore);
    }

    @Test
    void createCase_futurePurchaseDate_returns400() throws Exception {
        int requestsBefore = mockWebServer.getRequestCount();

        MockMultipartFile image = new MockMultipartFile(
                "image", "photo.jpg", "image/jpeg", minimalJpeg());

        mockMvc.perform(multipart("/api/cases")
                        .file(image)
                        .param("caseType", "RETURN")
                        .param("equipmentCategory", "LAPTOP")
                        .param("modelName", "ThinkPad X1")
                        .param("purchaseDate", "2099-12-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(mockWebServer.getRequestCount()).as("Zero LLM calls on future date (TAC-001-01)")
                .isEqualTo(requestsBefore);
    }

    @Test
    void createCase_missingModelName_returns400() throws Exception {
        int requestsBefore = mockWebServer.getRequestCount();

        MockMultipartFile image = new MockMultipartFile(
                "image", "photo.jpg", "image/jpeg", minimalJpeg());

        mockMvc.perform(multipart("/api/cases")
                        .file(image)
                        .param("caseType", "RETURN")
                        .param("equipmentCategory", "LAPTOP")
                        .param("purchaseDate", "2023-01-15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.modelName").exists());

        assertThat(mockWebServer.getRequestCount()).as("Zero LLM calls on missing model name (TAC-001-01)")
                .isEqualTo(requestsBefore);
    }

    @Test
    void createCase_noImage_returns400() throws Exception {
        int requestsBefore = mockWebServer.getRequestCount();

        mockMvc.perform(multipart("/api/cases")
                        .param("caseType", "RETURN")
                        .param("equipmentCategory", "LAPTOP")
                        .param("modelName", "ThinkPad X1")
                        .param("purchaseDate", "2023-01-15"))
                .andExpect(status().isBadRequest());

        assertThat(mockWebServer.getRequestCount()).as("Zero LLM calls when image missing (TAC-001-01)")
                .isEqualTo(requestsBefore);
    }

    @Test
    void createCase_oversizedImage_returns413() throws Exception {
        // TAC-001-02: max-upload-bytes limit → 413 BEFORE any LLM call
        int requestsBefore = mockWebServer.getRequestCount();

        // Build a byte array slightly above the 10 MB default
        byte[] bigImage = new byte[10 * 1024 * 1024 + 1];
        bigImage[0] = (byte) 0xFF;
        bigImage[1] = (byte) 0xD8;

        MockMultipartFile image = new MockMultipartFile(
                "image", "big.jpg", "image/jpeg", bigImage);

        mockMvc.perform(multipart("/api/cases")
                        .file(image)
                        .param("caseType", "RETURN")
                        .param("equipmentCategory", "LAPTOP")
                        .param("modelName", "ThinkPad X1")
                        .param("purchaseDate", "2023-01-15"))
                .andExpect(status().isPayloadTooLarge());

        assertThat(mockWebServer.getRequestCount()).as("Zero LLM calls on oversized image (TAC-001-02)")
                .isEqualTo(requestsBefore);
    }

    @Test
    void createCase_unsupportedImageType_returns415() throws Exception {
        // TAC-001-02: GIF is not an accepted type → 415 BEFORE any LLM call
        int requestsBefore = mockWebServer.getRequestCount();

        MockMultipartFile image = new MockMultipartFile(
                "image", "anim.gif", "image/gif", new byte[]{0x47, 0x49, 0x46});

        mockMvc.perform(multipart("/api/cases")
                        .file(image)
                        .param("caseType", "RETURN")
                        .param("equipmentCategory", "LAPTOP")
                        .param("modelName", "ThinkPad X1")
                        .param("purchaseDate", "2023-01-15"))
                .andExpect(status().isUnsupportedMediaType());

        assertThat(mockWebServer.getRequestCount()).as("Zero LLM calls on unsupported type (TAC-001-02)")
                .isEqualTo(requestsBefore);
    }

    // ── GET /api/cases/{id} ───────────────────────────────────────────────────

    @Test
    void getCase_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/cases/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void getCase_existingSession_returnsSessionResponse() throws Exception {
        enqueueVisionResponse();
        enqueueDecisionResponse("REJECT");

        // Create a case first
        MockMultipartFile image = new MockMultipartFile(
                "image", "photo.jpg", "image/jpeg", minimalJpeg());

        MvcResult createResult = mockMvc.perform(multipart("/api/cases")
                        .file(image)
                        .param("caseType", "COMPLAINT")
                        .param("equipmentCategory", "LAPTOP")
                        .param("modelName", "Dell XPS")
                        .param("purchaseDate", "2022-06-15")
                        .param("reason", "Ekran nie działa"))
                .andExpect(status().isCreated())
                .andReturn();

        String sessionId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("sessionId").asText();

        // Then fetch it
        MvcResult getResult = mockMvc.perform(get("/api/cases/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode body = objectMapper.readTree(getResult.getResponse().getContentAsString());
        assertThat(body.path("sessionId").asText()).isEqualTo(sessionId);
        assertThat(body.path("form").path("caseType").asText()).isEqualTo("COMPLAINT");
        assertThat(body.path("form").path("modelName").asText()).isEqualTo("Dell XPS");
        assertThat(body.path("decision").path("outcome").asText()).isEqualTo("REJECT");
        assertThat(body.path("messages").isArray()).isTrue();
        assertThat(body.path("messages").size()).isGreaterThanOrEqualTo(1);
    }
}
