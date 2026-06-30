package pl.nbp.copilot.web.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1.2 — Error model shape tests.
 * AC-07/08/24; ADR-001 §4.
 */
class ErrorResponseDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── ErrorCode completeness ────────────────────────────────────────────

    @Test
    void errorCode_hasExactlySixValues() {
        assertThat(ErrorCode.values()).hasSize(6);
    }

    @Test
    void errorCode_containsAllExpectedCodes() {
        assertThat(ErrorCode.values()).contains(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.SESSION_NOT_FOUND,
                ErrorCode.IMAGE_TOO_LARGE,
                ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                ErrorCode.LLM_UNAVAILABLE,
                ErrorCode.LLM_TIMEOUT
        );
    }

    // ── ErrorResponse serialization ───────────────────────────────────────

    @Test
    void errorResponse_serializes_withFieldErrors() throws Exception {
        var response = new ErrorResponse(
                ErrorCode.VALIDATION_ERROR.name(),
                "Błąd walidacji.",
                Map.of("modelName", "Nazwa modelu jest wymagana.")
        );

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"code\"");
        assertThat(json).contains("\"message\"");
        assertThat(json).contains("\"fieldErrors\"");
        assertThat(json).contains("VALIDATION_ERROR");
    }

    @Test
    void errorResponse_omitsFieldErrors_whenNull() throws Exception {
        var response = new ErrorResponse(
                ErrorCode.SESSION_NOT_FOUND.name(),
                "Sesja nie istnieje lub wygasła.",
                null
        );

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).doesNotContain("fieldErrors");
        assertThat(json).contains("SESSION_NOT_FOUND");
        assertThat(json).contains("\"message\"");
    }

    @Test
    void errorResponse_codeAndMessagePresent() {
        var response = new ErrorResponse(
                ErrorCode.LLM_UNAVAILABLE.name(),
                "Usługa AI jest niedostępna.",
                null
        );

        assertThat(response.code()).isEqualTo("LLM_UNAVAILABLE");
        assertThat(response.message()).isEqualTo("Usługa AI jest niedostępna.");
        assertThat(response.fieldErrors()).isNull();
    }
}
