package pl.nbp.copilot.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.nbp.copilot.application.exception.SessionNotFoundException;
import pl.nbp.copilot.application.port.DecisionPort;
import pl.nbp.copilot.application.port.SessionStore;
import pl.nbp.copilot.application.port.TokenSink;
import pl.nbp.copilot.domain.CaseForm;
import pl.nbp.copilot.domain.CaseSession;
import pl.nbp.copilot.domain.CaseType;
import pl.nbp.copilot.domain.ChatMessage;
import pl.nbp.copilot.domain.ChatReply;
import pl.nbp.copilot.domain.Decision;
import pl.nbp.copilot.domain.DecisionConfidence;
import pl.nbp.copilot.domain.DecisionOutcome;
import pl.nbp.copilot.domain.EquipmentCategory;
import pl.nbp.copilot.domain.ImageAnalysis;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ChatService}.
 * All dependencies are mocked.
 * ADR-001 §3/§5/§8; AC-19/20/21/22/24; TAC-001-05.
 */
class ChatServiceTest {

    private SessionStore sessionStore;
    private DecisionPort decisionPort;
    private ChatService chatService;

    private static final UUID SESSION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sessionStore = mock(SessionStore.class);
        decisionPort = mock(DecisionPort.class);
        chatService = new ChatService(sessionStore, decisionPort);
    }

    private CaseSession buildSession() {
        Decision decision = new Decision(
                DecisionOutcome.APPROVE, "Uzasadnienie.", List.of("pkt 1.1"),
                "Kolejne kroki.", DecisionConfidence.HIGH,
                "Dzień dobry,\n\n✅ **Decyzja: ZATWIERDŹ**\n\nZastrzeżenie");
        ChatMessage firstMessage = new ChatMessage(
                ChatMessage.Role.SYSTEM_ASSISTANT, decision.firstMessageMarkdown(), Instant.now());
        CaseForm form = new CaseForm(
                CaseType.COMPLAINT, EquipmentCategory.LAPTOP, "ThinkPad X1",
                LocalDate.of(2023, 1, 15), "Uszkodzony ekran");
        ImageAnalysis analysis = new ImageAnalysis("Opis urządzenia.", true, true, false, DecisionConfidence.HIGH);
        return new CaseSession(
                SESSION_ID, CaseType.COMPLAINT, form, analysis, decision,
                new ArrayList<>(List.of(firstMessage)),
                Instant.now(), Instant.now().plusSeconds(3600));
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void streamReply_appendsUserMessageAndAssistantMessage() {
        CaseSession session = buildSession();
        when(sessionStore.find(SESSION_ID)).thenReturn(Optional.of(session));
        when(decisionPort.streamReply(any(), anyString(), any()))
                .thenReturn(new ChatReply("Odpowiedź asystenta.", null, false));

        List<String> collected = new ArrayList<>();
        chatService.streamReply(SESSION_ID, "Pytanie.", collected::add);

        // user message appended
        verify(sessionStore).appendMessages(eq(SESSION_ID), argThatContainsRole(ChatMessage.Role.USER));
        // assistant message appended
        verify(sessionStore).appendMessages(eq(SESSION_ID), argThatContainsRole(ChatMessage.Role.ASSISTANT));
    }

    @Test
    void streamReply_returnsFullReplyAndNoUpdatedDecision_whenNormalReply() {
        CaseSession session = buildSession();
        when(sessionStore.find(SESSION_ID)).thenReturn(Optional.of(session));
        when(decisionPort.streamReply(any(), anyString(), any()))
                .thenReturn(new ChatReply("Pełna odpowiedź.", null, false));

        var result = chatService.streamReply(SESSION_ID, "Pytanie.", delta -> {});

        assertThat(result.replyMarkdown()).isEqualTo("Pełna odpowiedź.");
        assertThat(result.updatedDecision()).isNull();
    }

    @Test
    void streamReply_withUpdatedDecision_callsSupersedeDecision() {
        CaseSession session = buildSession();
        Decision newDecision = new Decision(
                DecisionOutcome.REJECT, "Nowe uzasadnienie.", List.of(),
                "Nowe kroki.", DecisionConfidence.HIGH,
                "Zaktualizowana decyzja - Zastrzeżenie");
        when(sessionStore.find(SESSION_ID)).thenReturn(Optional.of(session));
        when(decisionPort.streamReply(any(), anyString(), any()))
                .thenReturn(new ChatReply("Zmieniam decyzję.", newDecision, false));

        var result = chatService.streamReply(SESSION_ID, "Nowa info.", delta -> {});

        verify(sessionStore).supersedeDecision(eq(SESSION_ID), eq(newDecision));
        assertThat(result.updatedDecision()).isNotNull();
        assertThat(result.updatedDecision().outcome()).isEqualTo(DecisionOutcome.REJECT);
    }

    @Test
    void streamReply_withUpdatedDecision_doesNotMutateMessageHistory() {
        CaseSession session = buildSession();
        Decision newDecision = new Decision(
                DecisionOutcome.REJECT, "Nowe uzasadnienie.", List.of(),
                "Nowe kroki.", DecisionConfidence.HIGH, "Zaktualizowana decyzja");
        when(sessionStore.find(SESSION_ID)).thenReturn(Optional.of(session));
        when(decisionPort.streamReply(any(), anyString(), any()))
                .thenReturn(new ChatReply("Zmieniam.", newDecision, false));

        chatService.streamReply(SESSION_ID, "Info.", delta -> {});

        // supersedeDecision preserves messages — appendMessages called twice (user + assistant)
        // Messages are appended; only the decision reference is replaced, history intact
        verify(sessionStore, org.mockito.Mockito.times(2)).appendMessages(eq(SESSION_ID), any());
        verify(sessionStore).supersedeDecision(eq(SESSION_ID), eq(newDecision));
    }

    // ── Unknown session → 404 ─────────────────────────────────────────────────

    @Test
    void streamReply_unknownSessionId_throwsSessionNotFoundException() {
        when(sessionStore.find(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.streamReply(SESSION_ID, "Pytanie.", delta -> {}))
                .isInstanceOf(SessionNotFoundException.class);

        verify(decisionPort, never()).streamReply(any(), anyString(), any());
    }

    // ── Helper: argument matcher for message role ─────────────────────────────

    private static List<ChatMessage> argThatContainsRole(ChatMessage.Role role) {
        return org.mockito.ArgumentMatchers.argThat(list ->
                list != null && list.stream().anyMatch(m -> m.role() == role));
    }
}
