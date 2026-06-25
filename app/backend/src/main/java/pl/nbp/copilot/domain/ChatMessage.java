package pl.nbp.copilot.domain;

import java.time.Instant;

/**
 * Immutable value object representing a single message in the chat conversation.
 * ADR-004 §4; ADR-000 §5.
 *
 * @param role      The role of the message sender.
 * @param content   Message content in Markdown, in Polish (AC-23).
 * @param createdAt Timestamp when the message was created.
 */
public record ChatMessage(
        Role role,
        String content,
        Instant createdAt
) {

    /**
     * Role of the message author in the conversation.
     */
    public enum Role {
        /** Initial decision bubble composed by the system (index 0). */
        SYSTEM_ASSISTANT,
        /** Message sent by the employee. */
        USER,
        /** Subsequent replies from the AI assistant. */
        ASSISTANT
    }
}
