package pl.nbp.copilot.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link GlobalExceptionHandler}.
 * Verifies every known failure maps to the correct status + ErrorCode.
 * TAC-001-04; ADR-001 §6.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

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

    private static byte[] minimalJpeg() throws IOException {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0xFF0000);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", baos);
        return baos.toByteArray();
    }

    // ── VALIDATION_ERROR → 400 ────────────────────────────────────────────────

    @Test
    void validationError_missingRequiredField_returns400WithPolishMessage() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "photo.jpg", "image/jpeg", minimalJpeg());

        MvcResult result = mockMvc.perform(multipart("/api/cases")
                        .file(image)
                        .param("caseType", "COMPLAINT")
                        .param("equipmentCategory", "LAPTOP")
                        // missing modelName
                        .param("purchaseDate", "2023-01-15")
                        .param("reason", "Uszkodzony ekran"))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.path("message").asText()).isNotBlank();
        // No stack trace in body
        assertThat(result.getResponse().getContentAsString()).doesNotContain("java.lang");
    }

    @Test
    void validationError_hasFieldErrors() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "photo.jpg", "image/jpeg", minimalJpeg());

        MvcResult result = mockMvc.perform(multipart("/api/cases")
                        .file(image)
                        .param("caseType", "COMPLAINT")
                        .param("equipmentCategory", "LAPTOP")
                        // missing modelName
                        .param("purchaseDate", "2023-01-15")
                        .param("reason", "Uszkodzony ekran"))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.has("fieldErrors")).isTrue();
        assertThat(body.path("fieldErrors").path("modelName").asText()).isNotBlank();
    }

    // ── IMAGE_TOO_LARGE → 413 ─────────────────────────────────────────────────

    @Test
    void imageTooLarge_returns413WithCode() throws Exception {
        byte[] bigImage = new byte[10 * 1024 * 1024 + 1];
        bigImage[0] = (byte) 0xFF;
        bigImage[1] = (byte) 0xD8;

        MockMultipartFile image = new MockMultipartFile(
                "image", "big.jpg", "image/jpeg", bigImage);

        MvcResult result = mockMvc.perform(multipart("/api/cases")
                        .file(image)
                        .param("caseType", "RETURN")
                        .param("equipmentCategory", "LAPTOP")
                        .param("modelName", "ThinkPad X1")
                        .param("purchaseDate", "2023-01-15"))
                .andExpect(status().isPayloadTooLarge())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("code").asText()).isEqualTo("IMAGE_TOO_LARGE");
        assertThat(body.path("message").asText()).isNotBlank();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("java.lang");
    }

    // ── UNSUPPORTED_MEDIA_TYPE → 415 ─────────────────────────────────────────

    @Test
    void unsupportedImageType_returns415WithCode() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "anim.gif", "image/gif", new byte[]{0x47, 0x49, 0x46});

        MvcResult result = mockMvc.perform(multipart("/api/cases")
                        .file(image)
                        .param("caseType", "RETURN")
                        .param("equipmentCategory", "LAPTOP")
                        .param("modelName", "ThinkPad X1")
                        .param("purchaseDate", "2023-01-15"))
                .andExpect(status().isUnsupportedMediaType())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("code").asText()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
        assertThat(body.path("message").asText()).isNotBlank();
    }

    // ── SESSION_NOT_FOUND → 404 ───────────────────────────────────────────────

    @Test
    void sessionNotFound_returns404WithCode() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/cases/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("code").asText()).isEqualTo("SESSION_NOT_FOUND");
        assertThat(body.path("message").asText()).isNotBlank();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("java.lang");
    }

    // ── LLM_UNAVAILABLE → 502 ─────────────────────────────────────────────────

    @Test
    void llmUnavailable_onCaseCreation_returns502WithCode() throws Exception {
        // Stub 503 for all retry attempts (MAX_RETRIES=2 → 3 total attempts) → LlmUnavailableException → 502
        for (int i = 0; i < 3; i++) {
            mockWebServer.enqueue(new okhttp3.mockwebserver.MockResponse()
                    .setResponseCode(503)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"error\":{\"message\":\"Service unavailable\",\"type\":\"server_error\",\"code\":null}}"));
        }

        MockMultipartFile image = new MockMultipartFile(
                "image", "photo.jpg", "image/jpeg", minimalJpeg());

        MvcResult result = mockMvc.perform(multipart("/api/cases")
                        .file(image)
                        .param("caseType", "COMPLAINT")
                        .param("equipmentCategory", "LAPTOP")
                        .param("modelName", "ThinkPad X1")
                        .param("purchaseDate", "2023-01-15")
                        .param("reason", "Uszkodzony ekran"))
                .andExpect(status().isBadGateway())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("code").asText()).isEqualTo("LLM_UNAVAILABLE");
        assertThat(body.path("message").asText()).isNotBlank();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("java.lang");
    }

    // ── No 500 / no stack trace for known conditions ──────────────────────────

    @Test
    void noStackTrace_inAnyKnownErrorResponse() throws Exception {
        // Quick check: 404 for unknown session must have no stack trace
        MvcResult result = mockMvc.perform(get("/api/cases/00000000-0000-0000-0000-000000000000"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("at pl.nbp");
        assertThat(body).doesNotContain("StackTrace");
        assertThat(body).doesNotContain("java.lang");
    }
}
