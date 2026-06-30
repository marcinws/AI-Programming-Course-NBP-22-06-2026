package pl.nbp.copilot.web.dto;

/**
 * Machine-readable error codes used in {@link ErrorResponse}.
 * The frontend mirrors these values — never rename without updating both sides.
 * ADR-001 §4.
 */
public enum ErrorCode {
    /** One or more request fields failed Bean Validation. */
    VALIDATION_ERROR,
    /** The requested session id is unknown or has expired (TTL). */
    SESSION_NOT_FOUND,
    /** Uploaded image exceeds the configured size limit. */
    IMAGE_TOO_LARGE,
    /** Uploaded image has a content type not in the accepted set. */
    UNSUPPORTED_MEDIA_TYPE,
    /** The LLM upstream returned an error or exhausted retries. */
    LLM_UNAVAILABLE,
    /** The LLM upstream did not respond within the configured timeout. */
    LLM_TIMEOUT
}
