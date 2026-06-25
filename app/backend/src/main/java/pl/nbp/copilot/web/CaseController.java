package pl.nbp.copilot.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.nbp.copilot.application.CaseService;
import pl.nbp.copilot.application.exception.SessionNotFoundException;
import pl.nbp.copilot.application.port.SessionStore;
import pl.nbp.copilot.domain.CaseForm;
import pl.nbp.copilot.domain.CaseSession;
import pl.nbp.copilot.domain.CaseType;
import pl.nbp.copilot.domain.EquipmentCategory;
import pl.nbp.copilot.support.ImageProperties;
import pl.nbp.copilot.web.dto.CaseFormView;
import pl.nbp.copilot.web.dto.CreateCaseResponse;
import pl.nbp.copilot.web.dto.DecisionView;
import pl.nbp.copilot.web.dto.MessageView;
import pl.nbp.copilot.web.dto.SessionResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles case creation (POST /api/cases) and session rehydration (GET /api/cases/{id}).
 * ADR-001 §3/§5; AC-06/07/08/09/13/18/24; TAC-001-01/02/06.
 */
@RestController
@RequestMapping("/api/cases")
@Validated
public class CaseController {

    private final CaseService caseService;
    private final SessionStore sessionStore;
    private final ImageProperties imageProperties;

    public CaseController(CaseService caseService,
                          SessionStore sessionStore,
                          ImageProperties imageProperties) {
        this.caseService = caseService;
        this.sessionStore = sessionStore;
        this.imageProperties = imageProperties;
    }

    /**
     * Creates a new service case from a multipart form.
     * Validation runs before any LLM call (TAC-001-01).
     * Rejects oversized images (TAC-001-02) via Spring's multipart limits → 413.
     * Rejects unsupported MIME types → 415.
     *
     * @param caseType          COMPLAINT or RETURN
     * @param equipmentCategory equipment category enum
     * @param modelName         device model name
     * @param purchaseDate      ISO date, not future
     * @param reason            required iff COMPLAINT
     * @param image             uploaded image file
     * @return 201 with session id and decision
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CreateCaseResponse> createCase(
            @NotNull(message = "Typ sprawy jest wymagany.") CaseType caseType,
            @NotNull(message = "Kategoria sprzętu jest wymagana.") EquipmentCategory equipmentCategory,
            @NotBlank(message = "Nazwa modelu jest wymagana.") @Size(max = 200, message = "Nazwa modelu nie może przekraczać 200 znaków.") String modelName,
            @NotNull(message = "Data zakupu jest wymagana.") @PastOrPresent(message = "Data zakupu nie może być w przyszłości.") LocalDate purchaseDate,
            String reason,
            MultipartFile image) throws IOException {

        // Cross-field validation: reason required iff COMPLAINT
        if (caseType == CaseType.COMPLAINT && (reason == null || reason.isBlank())) {
            throw new ComplaintReasonRequiredException();
        }
        if (reason != null && reason.length() > 4000) {
            throw new ReasonTooLongException();
        }

        // Image presence check (before type/size — to return 400 not 415/413)
        if (image == null || image.isEmpty()) {
            throw new ImageRequiredException();
        }

        // Content-type validation → 415
        String contentType = image.getContentType();
        if (contentType == null || !imageProperties.acceptedTypes().contains(contentType)) {
            throw new UnsupportedImageTypeException(contentType);
        }

        // Size validation → 413 (before compression — MockMvc doesn't enforce Spring multipart limits)
        if (image.getSize() > imageProperties.maxUploadBytes()) {
            throw new ImageTooLargeException(image.getSize(), imageProperties.maxUploadBytes());
        }

        CaseForm form = new CaseForm(caseType, equipmentCategory, modelName, purchaseDate,
                reason != null && !reason.isBlank() ? reason : null);

        CaseService.CreateCaseCommand command = new CaseService.CreateCaseCommand(
                form, image.getBytes(), contentType);

        CaseService.CreateCaseResult result = caseService.createCase(command);

        CreateCaseResponse response = new CreateCaseResponse(
                result.sessionId().toString(),
                toDecisionView(result.decision()),
                result.imageAnalysisSummary());

        return ResponseEntity.status(201).body(response);
    }

    /**
     * Returns the full session state for session rehydration.
     * Unknown or expired session id → 404.
     * ADR-001 §5; TAC-001-06.
     *
     * @param id session UUID
     * @return 200 SessionResponse
     */
    @GetMapping("/{id}")
    public ResponseEntity<SessionResponse> getCase(@PathVariable UUID id) {
        CaseSession session = sessionStore.find(id)
                .orElseThrow(() -> new SessionNotFoundException(id));

        List<MessageView> messages = session.messages().stream()
                .map(m -> new MessageView(m.role().name(), m.content(), m.createdAt()))
                .collect(Collectors.toList());

        CaseFormView formView = new CaseFormView(
                session.form().caseType(),
                session.form().equipmentCategory(),
                session.form().modelName(),
                session.form().purchaseDate(),
                session.form().reason());

        SessionResponse response = new SessionResponse(
                session.id().toString(),
                formView,
                session.imageAnalysis() != null ? session.imageAnalysis().description() : null,
                toDecisionView(session.decision()),
                messages);

        return ResponseEntity.ok(response);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private DecisionView toDecisionView(pl.nbp.copilot.domain.Decision d) {
        return new DecisionView(
                d.outcome(),
                d.justification(),
                d.nextSteps(),
                d.firstMessageMarkdown());
    }

    // ── Inner exceptions (web-layer validation signals) ───────────────────────

    /**
     * Signals that the reason field is required for COMPLAINT but was missing.
     */
    public static class ComplaintReasonRequiredException extends RuntimeException {
        public ComplaintReasonRequiredException() {
            super("Opis reklamacji jest wymagany dla zgłoszeń typu COMPLAINT.");
        }
    }

    /**
     * Signals that the reason field exceeds 4000 characters.
     */
    public static class ReasonTooLongException extends RuntimeException {
        public ReasonTooLongException() {
            super("Opis reklamacji nie może przekraczać 4000 znaków.");
        }
    }

    /**
     * Signals that the uploaded image exceeds the maximum allowed size.
     */
    public static class ImageTooLargeException extends RuntimeException {
        public ImageTooLargeException(long actualBytes, long maxBytes) {
            super("Plik jest zbyt duży: " + actualBytes + " bajtów. Maksymalny rozmiar to " + maxBytes + " bajtów.");
        }
    }

    /**
     * Signals that no image was provided.
     */
    public static class ImageRequiredException extends RuntimeException {
        public ImageRequiredException() {
            super("Plik zdjęcia jest wymagany.");
        }
    }

    /**
     * Signals that the uploaded image has an unsupported MIME type.
     */
    public static class UnsupportedImageTypeException extends RuntimeException {
        private final String contentType;

        public UnsupportedImageTypeException(String contentType) {
            super("Nieobsługiwany typ pliku: " + contentType
                    + ". Dozwolone typy: image/jpeg, image/png, image/webp.");
            this.contentType = contentType;
        }

        public String getContentType() {
            return contentType;
        }
    }
}
