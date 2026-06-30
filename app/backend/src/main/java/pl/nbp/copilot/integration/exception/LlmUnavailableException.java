package pl.nbp.copilot.integration.exception;

/**
 * Thrown when the LLM upstream (OpenRouter) returns a 5xx error or a connection-level failure
 * and all bounded retries are exhausted.
 * ADR-003 §5/§6; TAC-003-07; mapped to HTTP 502/503 by the global exception handler.
 */
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(String message) {
        super(message);
    }

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
