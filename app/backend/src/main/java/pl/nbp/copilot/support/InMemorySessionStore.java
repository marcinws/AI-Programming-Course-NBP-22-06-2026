package pl.nbp.copilot.support;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.nbp.copilot.application.port.SessionStore;
import pl.nbp.copilot.domain.CaseSession;
import pl.nbp.copilot.domain.ChatMessage;
import pl.nbp.copilot.domain.Decision;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link SessionStore} backed by a {@link ConcurrentHashMap}.
 * TTL expiry is enforced both lazily on each access and eagerly by a scheduled sweep.
 * ADR-004 §3/§6; TAC-004-01..05.
 *
 * <p>Mutation operations (appendMessages, supersedeDecision) use {@code synchronized} on
 * per-session lock objects to ensure atomicity while allowing different sessions to proceed
 * concurrently (TAC-004-04).</p>
 */
@Component
public class InMemorySessionStore implements SessionStore {

    private final ConcurrentHashMap<UUID, CaseSession> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Object> locks = new ConcurrentHashMap<>();
    private final SessionProperties properties;

    public InMemorySessionStore(SessionProperties properties) {
        this.properties = properties;
    }

    @Override
    public UUID create(CaseSession session) {
        UUID id = session.id();
        locks.put(id, new Object());
        store.put(id, session);
        return id;
    }

    @Override
    public Optional<CaseSession> find(UUID sessionId) {
        CaseSession session = store.get(sessionId);
        if (session == null) {
            return Optional.empty();
        }
        if (isExpired(session)) {
            evictSingle(sessionId);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    @Override
    public void appendMessages(UUID sessionId, List<ChatMessage> messages) {
        Object lock = locks.get(sessionId);
        if (lock == null) {
            throw new NoSuchElementException("Session not found: " + sessionId);
        }
        synchronized (lock) {
            CaseSession current = store.get(sessionId);
            if (current == null || isExpired(current)) {
                throw new NoSuchElementException("Session not found or expired: " + sessionId);
            }
            List<ChatMessage> updated = new ArrayList<>(current.messages());
            updated.addAll(messages);
            store.put(sessionId, new CaseSession(
                    current.id(),
                    current.caseType(),
                    current.form(),
                    current.imageAnalysis(),
                    current.decision(),
                    List.copyOf(updated),
                    current.createdAt(),
                    current.expiresAt()
            ));
        }
    }

    @Override
    public void supersedeDecision(UUID sessionId, Decision newDecision) {
        Object lock = locks.get(sessionId);
        if (lock == null) {
            throw new NoSuchElementException("Session not found: " + sessionId);
        }
        synchronized (lock) {
            CaseSession current = store.get(sessionId);
            if (current == null || isExpired(current)) {
                throw new NoSuchElementException("Session not found or expired: " + sessionId);
            }
            // Replace decision; keep messages intact (AC-21, TAC-004-03)
            store.put(sessionId, new CaseSession(
                    current.id(),
                    current.caseType(),
                    current.form(),
                    current.imageAnalysis(),
                    newDecision,
                    current.messages(),
                    current.createdAt(),
                    current.expiresAt()
            ));
        }
    }

    /**
     * Scheduled sweep: evicts all sessions whose TTL has elapsed.
     * Runs every minute. TAC-004-02.
     */
    @Override
    @Scheduled(fixedDelayString = "PT1M")
    public void evictExpired() {
        Instant now = Instant.now();
        store.forEach((id, session) -> {
            if (session.expiresAt().isBefore(now) || session.expiresAt().equals(now)) {
                evictSingle(id);
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private boolean isExpired(CaseSession session) {
        Instant now = Instant.now();
        return !session.expiresAt().isAfter(now);
    }

    private void evictSingle(UUID id) {
        store.remove(id);
        locks.remove(id);
    }
}
