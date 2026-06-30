package pl.nbp.copilot.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.nbp.copilot.domain.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link InMemorySessionStore}.
 * ADR-004 §8; TAC-004-01..05.
 */
class InMemorySessionStoreTest {

    private InMemorySessionStore store;

    @BeforeEach
    void setUp() {
        store = new InMemorySessionStore(new SessionProperties(60));
    }

    // ── TAC-004-01: create + find ─────────────────────────────────────────────

    @Test
    void createAndFind_returnsSession() {
        CaseSession session = buildSession(Instant.now().plusSeconds(3600));

        UUID id = store.create(session);

        Optional<CaseSession> found = store.find(id);
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(session.id());
    }

    @Test
    void find_unknownId_returnsEmpty() {
        Optional<CaseSession> result = store.find(UUID.randomUUID());
        assertThat(result).isEmpty();
    }

    // ── TAC-004-01/02: TTL expiry ─────────────────────────────────────────────

    @Test
    void find_expiredSession_returnsEmpty() {
        // expiresAt in the past → expired
        CaseSession session = buildSession(Instant.now().minusSeconds(1));
        store.create(session);

        Optional<CaseSession> result = store.find(session.id());
        assertThat(result).isEmpty();
    }

    @Test
    void find_sessionExactlyAtTtlBoundary_returnsEmpty() {
        // expiresAt == now → expired (not strictly after now)
        Instant expiry = Instant.now();
        CaseSession session = buildSession(expiry);
        store.create(session);

        // The session's expiresAt is at or before now — must be treated as expired
        Optional<CaseSession> result = store.find(session.id());
        assertThat(result).isEmpty();
    }

    @Test
    void find_sessionJustBeforeExpiry_returnsSession() {
        // expiresAt far in the future → active
        CaseSession session = buildSession(Instant.now().plusSeconds(3600));
        store.create(session);

        Optional<CaseSession> result = store.find(session.id());
        assertThat(result).isPresent();
    }

    // ── TAC-004-02: eviction sweep ────────────────────────────────────────────

    @Test
    void evictExpired_removesExpiredOnly_leavesActiveUntouched() {
        CaseSession expired = buildSession(Instant.now().minusSeconds(10));
        CaseSession active = buildSession(Instant.now().plusSeconds(3600));

        store.create(expired);
        store.create(active);

        store.evictExpired();

        assertThat(store.find(expired.id())).isEmpty();
        assertThat(store.find(active.id())).isPresent();
    }

    @Test
    void evictExpired_onlyExpiredRemoved_multipleSessions() {
        List<UUID> expiredIds = new ArrayList<>();
        List<UUID> activeIds = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            CaseSession s = buildSession(Instant.now().minusSeconds(i + 1));
            store.create(s);
            expiredIds.add(s.id());
        }
        for (int i = 0; i < 3; i++) {
            CaseSession s = buildSession(Instant.now().plusSeconds(3600));
            store.create(s);
            activeIds.add(s.id());
        }

        store.evictExpired();

        for (UUID id : expiredIds) {
            assertThat(store.find(id)).as("expired session %s should be gone", id).isEmpty();
        }
        for (UUID id : activeIds) {
            assertThat(store.find(id)).as("active session %s should survive", id).isPresent();
        }
    }

    // ── TAC-004-04: concurrent appendMessages ─────────────────────────────────

    @Test
    void appendMessages_concurrent_allMessagesRetainedAndOrdered() throws InterruptedException {
        CaseSession session = buildSession(Instant.now().plusSeconds(3600));
        store.create(session);

        int threadCount = 10;
        int messagesPerThread = 20;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        for (int t = 0; t < threadCount; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    List<ChatMessage> msgs = List.of(
                            new ChatMessage(ChatMessage.Role.USER, "message", Instant.now())
                    );
                    for (int m = 0; m < messagesPerThread; m++) {
                        store.appendMessages(session.id(), msgs);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        Optional<CaseSession> found = store.find(session.id());
        assertThat(found).isPresent();
        // Initial messages (1 decision bubble) + threadCount * messagesPerThread
        int expectedTotal = session.messages().size() + threadCount * messagesPerThread;
        assertThat(found.get().messages()).hasSize(expectedTotal);
    }

    @Test
    void appendMessages_notFoundSession_throwsNoSuchElement() {
        assertThatThrownBy(() ->
                store.appendMessages(UUID.randomUUID(), List.of(
                        new ChatMessage(ChatMessage.Role.USER, "hi", Instant.now())
                ))
        ).isInstanceOf(NoSuchElementException.class);
    }

    // ── TAC-004-03: supersedeDecision keeps messages (AC-21) ─────────────────

    @Test
    void supersedeDecision_replacesDecision_keepsAllPriorMessages() {
        Decision original = buildDecision(DecisionOutcome.ESCALATE, "original");
        CaseSession session = buildSessionWithDecision(Instant.now().plusSeconds(3600), original);
        store.create(session);

        // Append some chat messages
        store.appendMessages(session.id(), List.of(
                new ChatMessage(ChatMessage.Role.USER, "Dodatkowa informacja", Instant.now()),
                new ChatMessage(ChatMessage.Role.ASSISTANT, "Zaktualizowana odpowiedź", Instant.now())
        ));

        int messagesBeforeSupersede = store.find(session.id()).get().messages().size();

        Decision updated = buildDecision(DecisionOutcome.APPROVE, "updated");
        store.supersedeDecision(session.id(), updated);

        Optional<CaseSession> found = store.find(session.id());
        assertThat(found).isPresent();

        // Decision replaced
        assertThat(found.get().decision().outcome()).isEqualTo(DecisionOutcome.APPROVE);
        assertThat(found.get().decision().justification()).isEqualTo("updated");

        // All messages preserved (AC-21)
        assertThat(found.get().messages()).hasSize(messagesBeforeSupersede);
        // First message (index 0) is still the original decision bubble
        assertThat(found.get().messages().get(0).role()).isEqualTo(ChatMessage.Role.SYSTEM_ASSISTANT);
    }

    @Test
    void supersedeDecision_notFoundSession_throwsNoSuchElement() {
        Decision decision = buildDecision(DecisionOutcome.APPROVE, "irrelevant");
        assertThatThrownBy(() -> store.supersedeDecision(UUID.randomUUID(), decision))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── TAC-004-05: no raw image bytes retained ───────────────────────────────

    @Test
    void storedSession_containsOnlyImageAnalysisTextFlags_noRawBytes() {
        ImageAnalysis analysis = new ImageAnalysis(
                "Widoczne rysy na obudowie",
                true,
                null,
                false,
                DecisionConfidence.MEDIUM
        );
        CaseSession session = buildSessionWithAnalysis(Instant.now().plusSeconds(3600), analysis);
        store.create(session);

        Optional<CaseSession> found = store.find(session.id());
        assertThat(found).isPresent();

        // CaseSession holds ImageAnalysis (text + flags) — not raw bytes
        // This is a structural assertion: CaseSession has no byte[] field
        ImageAnalysis stored = found.get().imageAnalysis();
        assertThat(stored.description()).isEqualTo("Widoczne rysy na obudowie");
        assertThat(stored.damageObserved()).isTrue();
        assertThat(stored.signsOfUse()).isNull();   // null = unknown
        assertThat(stored.usableForResale()).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CaseSession buildSession(Instant expiresAt) {
        return buildSessionWithDecision(expiresAt, buildDecision(DecisionOutcome.ESCALATE, "justification"));
    }

    private CaseSession buildSessionWithDecision(Instant expiresAt, Decision decision) {
        return buildSessionFull(expiresAt, decision, null);
    }

    private CaseSession buildSessionWithAnalysis(Instant expiresAt, ImageAnalysis analysis) {
        return buildSessionFull(expiresAt, buildDecision(DecisionOutcome.ESCALATE, "justification"), analysis);
    }

    private CaseSession buildSessionFull(Instant expiresAt, Decision decision, ImageAnalysis analysis) {
        UUID id = UUID.randomUUID();
        CaseForm form = new CaseForm(
                CaseType.COMPLAINT,
                EquipmentCategory.SMARTPHONE,
                "TestModel X1",
                java.time.LocalDate.of(2025, 1, 1),
                "Ekran przestał działać"
        );
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(
                ChatMessage.Role.SYSTEM_ASSISTANT,
                decision != null ? decision.firstMessageMarkdown() : "# Decyzja",
                Instant.now()
        ));
        return new CaseSession(
                id,
                CaseType.COMPLAINT,
                form,
                analysis,
                decision,
                List.copyOf(messages),
                Instant.now(),
                expiresAt
        );
    }

    private Decision buildDecision(DecisionOutcome outcome, String justification) {
        return new Decision(
                outcome,
                justification,
                List.of("pkt 3.1"),
                "Następne kroki testowe",
                DecisionConfidence.MEDIUM,
                "# " + outcome.name() + "\n\n" + justification
        );
    }
}
