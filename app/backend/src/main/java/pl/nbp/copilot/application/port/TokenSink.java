package pl.nbp.copilot.application.port;

/**
 * Functional interface for consuming streamed token deltas from the LLM.
 * Each delta is a partial chunk of the assistant's reply (may be one or more tokens).
 * ADR-003 §5; AC-20/21.
 */
@FunctionalInterface
public interface TokenSink {

    /**
     * Accepts a single token delta from the streaming response.
     *
     * @param delta partial content from a streaming chunk; may be empty but not null
     */
    void accept(String delta);
}
