package pl.nbp.copilot.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/cases/{id}/messages}.
 * ADR-001 §4.
 *
 * @param content The employee's follow-up message. Must be non-empty and at most 4000 characters.
 */
public record ChatRequest(
        @NotBlank(message = "Treść wiadomości nie może być pusta.")
        @Size(max = 4000, message = "Treść wiadomości nie może przekraczać 4000 znaków.")
        String content
) {
}
