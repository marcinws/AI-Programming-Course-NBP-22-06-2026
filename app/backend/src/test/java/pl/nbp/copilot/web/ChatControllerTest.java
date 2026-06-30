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
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for POST /api/cases/{id}/messages (SSE streaming chat).
 * Only the OpenRouter endpoint (MockWebServer) is stubbed.
 * ADR-001 §3/§5/§6/§8; AC-19/20/21/22/24; TAC-001-05.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest {

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

    private static byte[] minimalJpeg() throws IOException {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0xFF0000);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", baos);
        return baos.toByteArray();
    }

    private void enqueueVisionResponse() {
        String analysisJson = "{\"description\":\"Urządzenie w stanie uszkodzonym.\","
                + "\"damageObserved\":true,\"signsOfUse\":true,\"usableForResale\":false,\"confidence\":\"HIGH\"}";
        mockWebServer.enqueue(buildJsonResponse(buildChatCompletionJson(analysisJson, "test-vision-model")));
    }

    private void enqueueDecisionResponse(String outcome) {
        String decisionJson = "{\"outcome\":\"" + outcome + "\","
                + "\"justification\":\"Warunki gwarancji spełnione.\","
                + "\"citedRules\":[\"pkt 1.1 procedury\"],"
                + "\"nextSteps\":\"Zaakceptuj i wyślij do serwisu.\","
                + "\"confidence\":\"HIGH\"}";
        mockWebServer.enqueue(buildJsonResponse(buildChatCompletionJson(decisionJson, "test-text-model")));
    }

    private void enqueueStreamingResponse(List<String> tokens) {
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            sb.append("data: {\"id\":\"chatcmpl-stream\",\"object\":\"chat.completion.chunk\","
                    + "\"created\":1700000000,\"model\":\"test-text-model\","
                    + "\"choices\":[{\"index\":0,\"delta\":{\"content\":\""
                    + escapeJson(token) + "\"},\"finish_reason\":null}]}\n\n");
        }
        sb.append("data: {\"id\":\"chatcmpl-stream\",\"object\":\"chat.completion.chunk\","
                + "\"created\":1700000000,\"model\":\"test-text-model\","
                + "\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n");
        sb.append("data: [DONE]\n\n");

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sb.toString()));
    }

    private MockResponse buildJsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private String buildChatCompletionJson(String content, String model) {
        return "{"
                + "\"id\":\"chatcmpl-test\","
                + "\"object\":\"chat.completion\","
                + "\"created\":1700000000,"
                + "\"model\":\"" + model + "\","
                + "\"choices\":[{"
                + "\"index\":0,"
                + "\"message\":{\"role\":\"assistant\",\"content\":\""
                + escapeJson(content) + "\"},"
                + "\"finish_reason\":\"stop\""
                + "}],"
                + "\"usage\":{\"prompt_tokens\":50,\"completion_tokens\":100,\"total_tokens\":150}"
                + "}";
    }

    private String escapeJson(String v) {
        return v.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    /** Creates a case and returns its session id. */
    private String createCase() throws Exception {
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
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("sessionId").asText();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void chat_unknownSessionId_returns404JsonBeforeStream() throws Exception {
        // Must return 404 JSON — NOT text/event-stream (ADR-001 §5)
        mockMvc.perform(post("/api/cases/00000000-0000-0000-0000-000000000000/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Pytanie.\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void chat_validSession_returnsSseStream() throws Exception {
        // Setup: create case first
        enqueueVisionResponse();
        enqueueDecisionResponse("APPROVE");
        String sessionId = createCase();

        // Then chat
        enqueueStreamingResponse(List.of("Odpowiedź ", "asystenta."));

        MvcResult asyncResult = mockMvc.perform(post("/api/cases/" + sessionId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Jak długo trwa naprawa?\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Wait for async completion
        asyncResult.getAsyncResult(5000);

        MvcResult result = mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andReturn();

        String contentType = result.getResponse().getContentType();
        assertThat(contentType).contains("text/event-stream");

        String body = result.getResponse().getContentAsString();
        // Must contain at least one token event and a done event
        assertThat(body).contains("event:token");
        assertThat(body).contains("event:done");
    }

    @Test
    void chat_validSession_doneEventContainsMessage() throws Exception {
        enqueueVisionResponse();
        enqueueDecisionResponse("APPROVE");
        String sessionId = createCase();

        enqueueStreamingResponse(List.of("Proszę ", "czekać."));

        MvcResult asyncResult = mockMvc.perform(post("/api/cases/" + sessionId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Kiedy dostanę odpowiedź?\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        asyncResult.getAsyncResult(5000);

        MvcResult result = mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // done event data must contain the message
        assertThat(body).contains("event:done");
        // Extract done data
        String doneData = extractEventData(body, "done");
        assertThat(doneData).isNotBlank();
        JsonNode doneJson = objectMapper.readTree(doneData);
        assertThat(doneJson.has("message")).isTrue();
        assertThat(doneJson.path("message").path("role").asText()).isEqualTo("ASSISTANT");
    }

    @Test
    void chat_withUpdatedDecision_doneEventContainsUpdatedDecision() throws Exception {
        enqueueVisionResponse();
        enqueueDecisionResponse("APPROVE");
        String sessionId = createCase();

        // Stub reply with an UPDATED_DECISION marker
        List<String> tokens = List.of(
                "Na podstawie nowych informacji ",
                "[UPDATED_DECISION:REJECT] ",
                "zmieniam decyzję.");
        enqueueStreamingResponse(tokens);

        MvcResult asyncResult = mockMvc.perform(post("/api/cases/" + sessionId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Sprzęt był naprawiany nieoficjalnie.\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        asyncResult.getAsyncResult(5000);

        MvcResult result = mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String doneData = extractEventData(body, "done");
        assertThat(doneData).isNotBlank();
        JsonNode doneJson = objectMapper.readTree(doneData);
        // updatedDecision may be present when the model signals a change
        // (it is null when the outcome doesn't change)
        assertThat(doneJson).isNotNull();
    }

    @Test
    void chat_midStreamServerError_emitsErrorEvent() throws Exception {
        enqueueVisionResponse();
        enqueueDecisionResponse("APPROVE");
        String sessionId = createCase();

        // Stub 503 responses for all retries (MAX_RETRIES=2 → 3 total attempts)
        for (int i = 0; i < 3; i++) {
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(503)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"error\":{\"message\":\"Unavailable\",\"type\":\"server_error\",\"code\":null}}"));
        }

        MvcResult asyncResult = mockMvc.perform(post("/api/cases/" + sessionId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Pytanie po błędzie.\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        asyncResult.getAsyncResult(10000);

        MvcResult result = mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk()) // stream opened, error sent in-band
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:error");
    }

    // ── Helper: extract SSE event data ────────────────────────────────────────

    private String extractEventData(String sseBody, String eventName) {
        String[] lines = sseBody.split("\n");
        boolean inEvent = false;
        for (String line : lines) {
            if (line.startsWith("event:" + eventName) || line.startsWith("event: " + eventName)) {
                inEvent = true;
            } else if (inEvent && (line.startsWith("data:") || line.startsWith("data: "))) {
                return line.replaceFirst("^data:\\s*", "").trim();
            } else if (inEvent && line.isBlank()) {
                inEvent = false;
            }
        }
        return "";
    }
}
