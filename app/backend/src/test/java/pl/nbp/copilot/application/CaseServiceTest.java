package pl.nbp.copilot.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.nbp.copilot.application.port.DecisionPort;
import pl.nbp.copilot.application.port.ImageCompressor;
import pl.nbp.copilot.application.port.PolicyProvider;
import pl.nbp.copilot.application.port.SessionStore;
import pl.nbp.copilot.application.port.VisionAnalysisPort;
import pl.nbp.copilot.domain.CaseForm;
import pl.nbp.copilot.domain.CaseSession;
import pl.nbp.copilot.domain.CaseType;
import pl.nbp.copilot.domain.ChatMessage;
import pl.nbp.copilot.domain.Decision;
import pl.nbp.copilot.domain.DecisionConfidence;
import pl.nbp.copilot.domain.DecisionOutcome;
import pl.nbp.copilot.domain.EquipmentCategory;
import pl.nbp.copilot.domain.ImageAnalysis;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CaseService}.
 * All dependencies are mocked. Verifies orchestration logic: compression before vision,
 * vision before decision, session creation, first message composition.
 * ADR-001 §3/§8; AC-06/07/08/09/13/18; TAC-001-01/02.
 */
class CaseServiceTest {

    private ImageCompressor imageCompressor;
    private VisionAnalysisPort visionAnalysisPort;
    private DecisionPort decisionPort;
    private SessionStore sessionStore;
    private PolicyProvider policyProvider;
    private CaseService caseService;

    private static final String PROCEDURE_TEXT = "Procedura reklamacji...";
    private static final UUID SESSION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        imageCompressor = mock(ImageCompressor.class);
        visionAnalysisPort = mock(VisionAnalysisPort.class);
        decisionPort = mock(DecisionPort.class);
        sessionStore = mock(SessionStore.class);
        policyProvider = mock(PolicyProvider.class);

        caseService = new CaseService(
                imageCompressor, visionAnalysisPort, decisionPort, sessionStore, policyProvider);

        when(policyProvider.procedureText(any())).thenReturn(PROCEDURE_TEXT);
        when(imageCompressor.compress(any(), anyString())).thenReturn(new byte[]{1, 2, 3});
        when(sessionStore.create(any())).thenReturn(SESSION_ID);
    }

    private ImageAnalysis defaultAnalysis() {
        return new ImageAnalysis("Urządzenie uszkodzone.", true, true, false, DecisionConfidence.HIGH);
    }

    private Decision defaultDecision(DecisionOutcome outcome) {
        return new Decision(outcome, "Uzasadnienie.", List.of("pkt 1.1"), "Kolejne kroki.",
                DecisionConfidence.HIGH, "Dzień dobry,\n\n✅ **Decyzja**\n\nZastrzeżenie");
    }

    private CaseService.CreateCaseCommand complaintCommand() {
        CaseForm form = new CaseForm(
                CaseType.COMPLAINT, EquipmentCategory.LAPTOP,
                "ThinkPad X1", LocalDate.of(2023, 1, 15), "Uszkodzony ekran");
        return new CaseService.CreateCaseCommand(form, new byte[]{(byte) 0xFF, (byte) 0xD8}, "image/jpeg");
    }

    private CaseService.CreateCaseCommand returnCommand() {
        CaseForm form = new CaseForm(
                CaseType.RETURN, EquipmentCategory.SMARTPHONE,
                "Samsung S24", LocalDate.of(2024, 6, 1), null);
        return new CaseService.CreateCaseCommand(form, new byte[]{(byte) 0xFF, (byte) 0xD8}, "image/jpeg");
    }

    // ── Orchestration order ────────────────────────────────────────────────────

    @Test
    void createCase_compressesImageBeforeVisionCall() throws Exception {
        when(visionAnalysisPort.analyze(any(), any(), anyString(), any())).thenReturn(defaultAnalysis());
        when(decisionPort.decide(any(), any(), anyString())).thenReturn(defaultDecision(DecisionOutcome.APPROVE));

        caseService.createCase(complaintCommand());

        // compress must be called with the original bytes + mime
        verify(imageCompressor).compress(new byte[]{(byte) 0xFF, (byte) 0xD8}, "image/jpeg");
        // vision receives the compressed bytes
        verify(visionAnalysisPort).analyze(eq(CaseType.COMPLAINT), eq(new byte[]{1, 2, 3}), eq("image/jpeg"), any());
    }

    @Test
    void createCase_callsDecisionWithProcedureText() throws Exception {
        when(visionAnalysisPort.analyze(any(), any(), anyString(), any())).thenReturn(defaultAnalysis());
        when(decisionPort.decide(any(), any(), anyString())).thenReturn(defaultDecision(DecisionOutcome.APPROVE));

        caseService.createCase(complaintCommand());

        verify(decisionPort).decide(any(), any(), eq(PROCEDURE_TEXT));
    }

    @Test
    void createCase_storesSessionAndReturnsSessionId() throws Exception {
        when(visionAnalysisPort.analyze(any(), any(), anyString(), any())).thenReturn(defaultAnalysis());
        when(decisionPort.decide(any(), any(), anyString())).thenReturn(defaultDecision(DecisionOutcome.APPROVE));

        CaseService.CreateCaseResult result = caseService.createCase(complaintCommand());

        verify(sessionStore).create(any(CaseSession.class));
        assertThat(result.sessionId()).isEqualTo(SESSION_ID);
    }

    @Test
    void createCase_resultContainsDecision() throws Exception {
        Decision decision = defaultDecision(DecisionOutcome.REJECT);
        when(visionAnalysisPort.analyze(any(), any(), anyString(), any())).thenReturn(defaultAnalysis());
        when(decisionPort.decide(any(), any(), anyString())).thenReturn(decision);

        CaseService.CreateCaseResult result = caseService.createCase(complaintCommand());

        assertThat(result.decision().outcome()).isEqualTo(DecisionOutcome.REJECT);
        assertThat(result.decision().firstMessageMarkdown()).contains("Zastrzeżenie");
    }

    @Test
    void createCase_sessionContainsFirstMessageBubble() throws Exception {
        when(visionAnalysisPort.analyze(any(), any(), anyString(), any())).thenReturn(defaultAnalysis());
        when(decisionPort.decide(any(), any(), anyString())).thenReturn(defaultDecision(DecisionOutcome.APPROVE));

        caseService.createCase(complaintCommand());

        // Capture the session passed to create()
        org.mockito.ArgumentCaptor<CaseSession> captor =
                org.mockito.ArgumentCaptor.forClass(CaseSession.class);
        verify(sessionStore).create(captor.capture());

        CaseSession session = captor.getValue();
        assertThat(session.messages()).isNotEmpty();
        assertThat(session.messages().get(0).role())
                .isEqualTo(ChatMessage.Role.SYSTEM_ASSISTANT);
        assertThat(session.messages().get(0).content()).isNotBlank();
    }

    @Test
    void createCase_returnType_passesProcedureForReturn() throws Exception {
        when(visionAnalysisPort.analyze(any(), any(), anyString(), any())).thenReturn(defaultAnalysis());
        when(decisionPort.decide(any(), any(), anyString())).thenReturn(defaultDecision(DecisionOutcome.APPROVE));

        caseService.createCase(returnCommand());

        verify(policyProvider).procedureText(CaseType.RETURN);
    }

    @Test
    void createCase_imageAnalysisSummaryPresentInResult() throws Exception {
        ImageAnalysis analysis = new ImageAnalysis("Opis widocznego stanu.", true, false, null, DecisionConfidence.MEDIUM);
        when(visionAnalysisPort.analyze(any(), any(), anyString(), any())).thenReturn(analysis);
        when(decisionPort.decide(any(), any(), anyString())).thenReturn(defaultDecision(DecisionOutcome.APPROVE));

        CaseService.CreateCaseResult result = caseService.createCase(complaintCommand());

        assertThat(result.imageAnalysisSummary()).isEqualTo("Opis widocznego stanu.");
    }
}
