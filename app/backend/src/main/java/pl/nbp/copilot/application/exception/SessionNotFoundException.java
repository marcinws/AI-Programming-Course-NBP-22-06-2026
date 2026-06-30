package pl.nbp.copilot.application.exception;

import java.util.UUID;

/**
 * Thrown when a session is not found by its id (unknown or expired TTL).
 * Mapped to HTTP 404 SESSION_NOT_FOUND by the global exception handler.
 * ADR-001 §3/§6; TAC-001-06; TAC-004-01.
 */
public class SessionNotFoundException extends RuntimeException {

    private final UUID sessionId;

    public SessionNotFoundException(UUID sessionId) {
        super("Sesja nie została znaleziona lub wygasła: " + sessionId);
        this.sessionId = sessionId;
    }

    public UUID getSessionId() {
        return sessionId;
    }
}
