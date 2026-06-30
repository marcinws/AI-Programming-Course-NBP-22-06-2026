package pl.nbp.copilot.application.port;

import pl.nbp.copilot.domain.CaseSession;
import pl.nbp.copilot.domain.ChatMessage;
import pl.nbp.copilot.domain.Decision;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for the in-memory (and later persistent) session store.
 * ADR-004 §3/§5; TAC-004-01..05.
 *
 * <p>All mutation operations are atomic per session. Missing and expired sessions
 * are indistinguishable to callers — both result in an empty {@link Optional} or a
 * not-found signal (TAC-004-01), avoiding session enumeration.</p>
 */
public interface SessionStore {

    /**
     * Persists a new session and returns its id.
     *
     * @param session the session to store; its {@code id} is used as the key
     * @return the session id
     */
    UUID create(CaseSession session);

    /**
     * Returns the session for the given id, or empty if the id is unknown or the session has expired.
     *
     * @param sessionId the session id
     * @return the session, or {@link Optional#empty()} if not found or expired (TAC-004-01)
     */
    Optional<CaseSession> find(UUID sessionId);

    /**
     * Atomically appends one or more messages to the session's message list.
     * Order is preserved; no messages are lost under concurrent appends (TAC-004-04).
     *
     * @param sessionId the session id
     * @param messages  messages to append (in order)
     * @throws java.util.NoSuchElementException if the session is not found or has expired
     */
    void appendMessages(UUID sessionId, List<ChatMessage> messages);

    /**
     * Atomically replaces the current decision while preserving all prior messages (AC-21; TAC-004-03).
     * The message history (including the original first decision bubble at index 0) is never mutated.
     *
     * @param sessionId   the session id
     * @param newDecision the replacement decision
     * @throws java.util.NoSuchElementException if the session is not found or has expired
     */
    void supersedeDecision(UUID sessionId, Decision newDecision);

    /**
     * Evicts all sessions past their {@code expiresAt} timestamp.
     * Intended to be called by a scheduled background task (TAC-004-02).
     */
    void evictExpired();
}
