package pl.nbp.copilot.integration.exception;

/**
 * Thrown when the LLM upstream (OpenRouter) does not respond within the configured timeout
 * and all bounded retries are exhausted.
 * ADR-003 §5/§6; TAC-003-07; mapped to HTTP 504 by the global exception handler.
 */
public class LlmTimeoutException extends RuntimeException {

    public LlmTimeoutException(String message) {
        super(message);
    }

    public LlmTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
