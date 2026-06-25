package pl.nbp.copilot.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Uniform error body returned for every non-2xx response.
 * {@code fieldErrors} is omitted from JSON when null (not present on all error types).
 * ADR-001 §4; AC-24.
 *
 * @param code        Machine-readable error code matching {@link ErrorCode} name.
 * @param message     User-safe Polish message — never a stack trace.
 * @param fieldErrors Per-field validation messages; null/omitted for non-validation errors.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        Map<String, String> fieldErrors
) {
}
