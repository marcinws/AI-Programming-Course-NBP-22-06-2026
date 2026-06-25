package pl.nbp.copilot.integration;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Spring configuration that constructs the shared {@link OpenAIClient} bean.
 * The client is pointed at OpenRouter via a custom base URL; the API key and
 * model ids come from {@link OpenRouterProperties} (env-resolved by Spring).
 *
 * <p>ADR-003 §3; ADR-000 §7; TAC-003-01.</p>
 *
 * <p>Retry strategy: the SDK's built-in retry mechanism is used with a bounded
 * {@code maxRetries} count. Timeouts are set at the client level via
 * {@code OPENAI_REQUEST_TIMEOUT_MS} (resolved through the {@code openai.request-timeout-ms}
 * property bound separately in application.yaml).</p>
 */
@Configuration
public class OpenAiClientConfig {

    /** Maximum number of retries on transient failures before throwing a typed exception. */
    static final int MAX_RETRIES = 2;

    private final OpenRouterProperties openRouterProperties;
    private final long requestTimeoutMs;

    public OpenAiClientConfig(
            OpenRouterProperties openRouterProperties,
            @org.springframework.beans.factory.annotation.Value("${openai.request-timeout-ms:60000}") long requestTimeoutMs) {
        this.openRouterProperties = openRouterProperties;
        this.requestTimeoutMs = requestTimeoutMs;
    }

    /**
     * Builds the SDK client with:
     * <ul>
     *   <li>{@code baseUrl} = {@code openrouter.base-url} (OpenRouter endpoint)</li>
     *   <li>{@code apiKey} = {@code openrouter.api-key} (resolved from OPENAI_API_KEY or OPENROUTER_API_KEY)</li>
     *   <li>{@code timeout} = {@code openai.request-timeout-ms}</li>
     *   <li>{@code maxRetries} = {@link #MAX_RETRIES} (bounded)</li>
     * </ul>
     *
     * @return configured {@link OpenAIClient} singleton
     */
    @Bean
    public OpenAIClient openAIClient() {
        return OpenAIOkHttpClient.builder()
                .baseUrl(openRouterProperties.baseUrl())
                .apiKey(openRouterProperties.apiKey())
                .timeout(Duration.ofMillis(requestTimeoutMs))
                .maxRetries(MAX_RETRIES)
                .build();
    }
}
