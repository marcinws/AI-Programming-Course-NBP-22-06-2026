package pl.nbp.copilot.application.port;

import pl.nbp.copilot.domain.CaseForm;
import pl.nbp.copilot.domain.CaseType;
import pl.nbp.copilot.domain.ImageAnalysis;

/**
 * Port for multimodal image analysis via the vision LLM.
 * The vision model describes the equipment's visible condition only — it never decides approve/reject.
 * ADR-003 §3/§5; AC-10/11/12; TAC-003-01/02/03.
 */
public interface VisionAnalysisPort {

    /**
     * Analyzes the supplied image bytes and returns a neutral condition description plus optional flags.
     *
     * <p>The call targets the vision model ({@code OPENROUTER_VISION_MODEL}).
     * The prompt is selected by {@code caseType} (analysis-complaint / analysis-return).
     * Image bytes are sent as a base64 data URL. The model is instructed to describe only,
     * never to decide APPROVE/REJECT/ESCALATE (ADR-003 §3; TAC-003-03).</p>
     *
     * @param caseType  determines the analysis prompt variant; must not be null
     * @param imageBytes compressed image bytes; must not be null or empty
     * @param mime       MIME type of the image (e.g. "image/jpeg"); used to build the data URL
     * @param form       case form data providing non-image context hints to the model
     * @return {@link ImageAnalysis} with description and optional flags (null flags = unknown)
     * @throws pl.nbp.copilot.integration.exception.LlmUnavailableException  after bounded retries on 5xx / connection error
     * @throws pl.nbp.copilot.integration.exception.LlmTimeoutException      after bounded retries on timeout
     */
    ImageAnalysis analyze(CaseType caseType, byte[] imageBytes, String mime, CaseForm form);
}
