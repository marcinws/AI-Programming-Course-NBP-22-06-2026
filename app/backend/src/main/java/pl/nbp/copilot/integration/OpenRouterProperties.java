package pl.nbp.copilot.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the OpenRouter / OpenAI Java SDK client.
 * Bound from {@code openrouter.*} in {@code application.yaml}.
 * ADR-003 §3; ADR-000 §7; TAC-003-01.
 *
 * <p>Key resolution: {@code OPENAI_API_KEY} takes precedence over {@code OPENROUTER_API_KEY}
 * (expressed as a Spring property placeholder chain in application.yaml).</p>
 *
 * @param apiKey      Resolved API key (see application.yaml for resolution chain).
 * @param baseUrl     Base URL for the OpenRouter API, e.g. {@code https://openrouter.ai/api/v1}.
 * @param textModel   Model ID for decision and chat calls ({@code OPENROUTER_TEXT_MODEL}).
 * @param visionModel Model ID for image analysis calls ({@code OPENROUTER_VISION_MODEL}).
 * @param httpReferer Optional OpenRouter ranking header value ({@code HTTP-Referer}).
 * @param appTitle    Optional OpenRouter ranking header value ({@code X-Title}).
 */
@ConfigurationProperties(prefix = "openrouter")
public record OpenRouterProperties(
        String apiKey,
        String baseUrl,
        String textModel,
        String visionModel,
        String httpReferer,
        String appTitle
) {
}
