package pl.nbp.copilot.web.dto;

import java.time.Instant;

/**
 * Single chat message as returned in {@link SessionResponse}.
 * ADR-001 §4.
 *
 * @param role      Logical role: SYSTEM_ASSISTANT | USER | ASSISTANT.
 * @param content   Message body in Polish Markdown.
 * @param createdAt Creation timestamp (UTC).
 */
public record MessageView(
        String role,
        String content,
        Instant createdAt
) {
}
