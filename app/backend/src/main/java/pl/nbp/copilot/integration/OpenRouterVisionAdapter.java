package pl.nbp.copilot.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartImage;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pl.nbp.copilot.application.port.PromptProvider;
import pl.nbp.copilot.application.port.VisionAnalysisPort;
import pl.nbp.copilot.domain.CaseForm;
import pl.nbp.copilot.domain.CaseType;
import pl.nbp.copilot.domain.DecisionConfidence;
import pl.nbp.copilot.domain.ImageAnalysis;
import pl.nbp.copilot.integration.exception.LlmTimeoutException;
import pl.nbp.copilot.integration.exception.LlmUnavailableException;

import java.net.SocketTimeoutException;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;

/**
 * Vision analysis adapter that calls the OpenRouter multimodal vision model via the OpenAI Java SDK.
 * Implements the describe-only contract: the vision model characterises the equipment's condition
 * and never produces an approve/reject/escalate verdict (ADR-003 §3; TAC-003-03).
 *
 * <p>ADR-003 §3/§5; AC-10/11/12; TAC-003-01/02/03.</p>
 */
@Component
public class OpenRouterVisionAdapter implements VisionAnalysisPort {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterVisionAdapter.class);

    private final OpenAIClient client;
    private final OpenRouterProperties properties;
    private final PromptProvider promptProvider;
    private final ObjectMapper objectMapper;

    public OpenRouterVisionAdapter(
            OpenAIClient client,
            OpenRouterProperties properties,
            PromptProvider promptProvider) {
        this.client = client;
        this.properties = properties;
        this.promptProvider = promptProvider;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ImageAnalysis analyze(CaseType caseType, byte[] imageBytes, String mime, CaseForm form) {
        String analysisPrompt = promptProvider.analysisPrompt(caseType);
        String systemPrompt = promptProvider.systemPrompt();
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        String dataUrl = "data:" + mime + ";base64," + base64Image;

        ChatCompletionContentPartText textPart = ChatCompletionContentPartText.builder()
                .text(buildAnalysisText(analysisPrompt, form))
                .build();

        ChatCompletionContentPartImage imagePart = ChatCompletionContentPartImage.builder()
                .imageUrl(ChatCompletionContentPartImage.ImageUrl.builder()
                        .url(dataUrl)
                        .build())
                .build();

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(properties.visionModel())
                .addSystemMessage(systemPrompt)
                .addUserMessageOfArrayOfContentParts(List.of(
                        ChatCompletionContentPart.ofText(textPart),
                        ChatCompletionContentPart.ofImageUrl(imagePart)))
                .build();

        ChatCompletion completion = callWithExceptionMapping(() ->
                client.chat().completions().create(params));

        String responseText = extractContent(completion);
        return parseAnalysisResponse(responseText);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Builds the text portion of the user message for the analysis call.
     * Combines the prompt template with the non-image form context.
     */
    private String buildAnalysisText(String analysisPrompt, CaseForm form) {
        return analysisPrompt
                + "\n\n## Kontekst zgłoszenia\n"
                + "- Kategoria sprzętu: " + (form.equipmentCategory() != null ? form.equipmentCategory().name() : "—") + "\n"
                + "- Model: " + nvl(form.modelName()) + "\n"
                + "- Data zakupu: " + (form.purchaseDate() != null ? form.purchaseDate().toString() : "—") + "\n"
                + (form.reason() != null ? "- Opis problemu: " + form.reason() + "\n" : "");
    }

    /**
     * Extracts the plain text content from the first choice of a chat completion response.
     */
    private String extractContent(ChatCompletion completion) {
        return completion.choices().stream()
                .findFirst()
                .flatMap(c -> c.message().content())
                .orElse("");
    }

    /**
     * Parses the model's JSON response into an {@link ImageAnalysis}.
     * If the response is not JSON or is missing the description field, falls back to using
     * the raw text as the description with all flags set to null (unknown).
     */
    private ImageAnalysis parseAnalysisResponse(String responseText) {
        try {
            JsonNode root = objectMapper.readTree(responseText);
            if (!root.isObject()) {
                return new ImageAnalysis(responseText, null, null, null, null);
            }
            String description = root.path("description").asText(responseText);
            Boolean damageObserved = parseBooleanFlag(root, "damageObserved");
            Boolean signsOfUse = parseBooleanFlag(root, "signsOfUse");
            Boolean usableForResale = parseBooleanFlag(root, "usableForResale");
            DecisionConfidence confidence = parseConfidence(root);
            return new ImageAnalysis(description, damageObserved, signsOfUse, usableForResale, confidence);
        } catch (Exception e) {
            // Model returned non-JSON (plain text description) — treat as description-only
            log.debug("Vision model returned non-JSON; using as description. length={}", responseText.length());
            return new ImageAnalysis(responseText, null, null, null, null);
        }
    }

    private Boolean parseBooleanFlag(JsonNode root, String fieldName) {
        JsonNode node = root.path(fieldName);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asBoolean();
    }

    private DecisionConfidence parseConfidence(JsonNode root) {
        JsonNode node = root.path("confidence");
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            return DecisionConfidence.valueOf(node.asText().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Executes the supplier, mapping SDK exceptions to the typed domain exceptions.
     * The SDK's built-in maxRetries handles transient failures before this point.
     */
    private <T> T callWithExceptionMapping(Supplier<T> call) {
        try {
            return call.get();
        } catch (OpenAIIoException e) {
            Throwable cause = e.getCause();
            if (isTimeoutCause(cause)) {
                throw new LlmTimeoutException("Upłynął limit czasu żądania do modelu wizji.", e);
            }
            throw new LlmUnavailableException("Błąd komunikacji z modelem wizji.", e);
        } catch (OpenAIServiceException e) {
            int status = e.statusCode();
            if (status == 408 || status == 504) {
                throw new LlmTimeoutException("Limit czasu żądania do modelu wizji (HTTP " + status + ").", e);
            }
            throw new LlmUnavailableException("Model wizji zwrócił błąd serwera (HTTP " + status + ").", e);
        } catch (LlmUnavailableException | LlmTimeoutException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmUnavailableException("Nieoczekiwany błąd komunikacji z modelem wizji.", e);
        }
    }

    private boolean isTimeoutCause(Throwable cause) {
        if (cause == null) return false;
        if (cause instanceof SocketTimeoutException) return true;
        String msg = cause.getMessage();
        return msg != null && msg.toLowerCase().contains("timeout");
    }

    private String nvl(String value) {
        return value != null ? value : "—";
    }
}
