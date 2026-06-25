package pl.nbp.copilot.domain;

/**
 * Result of a streamed chat turn, accumulated after the stream completes.
 * ADR-003 §4/§5; AC-20/21/22; TAC-003-06.
 *
 * @param replyMarkdown      The full assistant reply text (Polish Markdown).
 * @param updatedDecision    Non-null when the user provided material new information that changes the decision.
 *                           Null for normal Q&amp;A or off-topic redirects.
 * @param offTopic           {@code true} when the user's input was unrelated to the current case and the
 *                           reply is a polite Polish redirect rather than a substantive answer.
 */
public record ChatReply(
        String replyMarkdown,
        Decision updatedDecision,
        boolean offTopic
) {
}
