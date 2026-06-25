package pl.nbp.copilot.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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
import pl.nbp.copilot.support.SessionProperties;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates case creation: validate → compress → vision analyze → decide → create session.
 * ADR-001 §3; AC-06/07/08/09/13/18; TAC-001-01.
 */
@Service
public class CaseService {

    private final ImageCompressor imageCompressor;
    private final VisionAnalysisPort visionAnalysisPort;
    private final DecisionPort decisionPort;
    private final SessionStore sessionStore;
    private final PolicyProvider policyProvider;
    private final int ttlMinutes;

    /**
     * Command object passed from the web layer to CaseService.
     *
     * @param form        Validated case form data.
     * @param imageBytes  Raw uploaded image bytes.
     * @param mime        MIME type of the uploaded image.
     */
    public record CreateCaseCommand(
            CaseForm form,
            byte[] imageBytes,
            String mime
    ) {
    }

    /**
     * Result returned from {@link #createCase}.
     *
     * @param sessionId            Created session UUID.
     * @param decision             AI decision (with firstMessageMarkdown).
     * @param imageAnalysisSummary Textual description from the vision model.
     */
    public record CreateCaseResult(
            UUID sessionId,
            Decision decision,
            String imageAnalysisSummary
    ) {
    }

    /**
     * Primary Spring-managed constructor injecting {@link SessionProperties}.
     */
    @Autowired
    public CaseService(
            ImageCompressor imageCompressor,
            VisionAnalysisPort visionAnalysisPort,
            DecisionPort decisionPort,
            SessionStore sessionStore,
            PolicyProvider policyProvider,
            SessionProperties sessionProperties) {
        this.imageCompressor = imageCompressor;
        this.visionAnalysisPort = visionAnalysisPort;
        this.decisionPort = decisionPort;
        this.sessionStore = sessionStore;
        this.policyProvider = policyProvider;
        this.ttlMinutes = sessionProperties.ttlMinutes();
    }

    /**
     * Test-friendly constructor without {@link SessionProperties} — defaults TTL to 60 minutes.
     */
    public CaseService(
            ImageCompressor imageCompressor,
            VisionAnalysisPort visionAnalysisPort,
            DecisionPort decisionPort,
            SessionStore sessionStore,
            PolicyProvider policyProvider) {
        this.imageCompressor = imageCompressor;
        this.visionAnalysisPort = visionAnalysisPort;
        this.decisionPort = decisionPort;
        this.sessionStore = sessionStore;
        this.policyProvider = policyProvider;
        this.ttlMinutes = 60;
    }

    /**
     * Creates a new case session.
     *
     * <p>Pipeline: compress → vision analyze → decide → create session → store.
     * Validation of the command is performed by the web layer before calling this method.
     *
     * @param command validated command with form data and image bytes
     * @return result containing the session id, decision, and image analysis summary
     */
    public CreateCaseResult createCase(CreateCaseCommand command) {
        CaseForm form = command.form();

        // 1. Compress the image
        byte[] compressedBytes;
        try {
            compressedBytes = imageCompressor.compress(command.imageBytes(), command.mime());
        } catch (IOException e) {
            throw new UncheckedIOException("Nie udało się przetworzyć obrazu.", e);
        }

        // 2. Vision analysis
        var imageAnalysis = visionAnalysisPort.analyze(
                form.caseType(), compressedBytes, command.mime(), form);

        // 3. Load procedure and produce structured decision
        String procedureText = policyProvider.procedureText(form.caseType());
        Decision decision = decisionPort.decide(form, imageAnalysis, procedureText);

        // 4. Create session with first message bubble at index 0
        Instant now = Instant.now();
        ChatMessage firstMessage = new ChatMessage(
                ChatMessage.Role.SYSTEM_ASSISTANT,
                decision.firstMessageMarkdown(),
                now);

        CaseSession session = new CaseSession(
                UUID.randomUUID(),
                form.caseType(),
                form,
                imageAnalysis,
                decision,
                List.of(firstMessage),
                now,
                now.plus(ttlMinutes, ChronoUnit.MINUTES));

        UUID sessionId = sessionStore.create(session);

        return new CreateCaseResult(sessionId, decision, imageAnalysis.description());
    }
}
