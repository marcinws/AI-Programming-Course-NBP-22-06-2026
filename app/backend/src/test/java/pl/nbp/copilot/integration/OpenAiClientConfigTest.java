package pl.nbp.copilot.integration;

import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the {@link OpenAIClient} bean is correctly built from the resolved
 * {@link OpenRouterProperties} — base URL and API key from env/yaml (TAC-003-01).
 *
 * <p>The test profile provides {@code openrouter.base-url=http://localhost:0} and
 * {@code openrouter.api-key=test-api-key} so no real network call is made.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class OpenAiClientConfigTest {

    @Autowired
    private OpenAIClient openAIClient;

    @Autowired
    private OpenRouterProperties openRouterProperties;

    @Test
    void clientBean_isPresent() {
        assertThat(openAIClient)
                .as("OpenAIClient bean must be present in the application context")
                .isNotNull();
    }

    @Test
    void properties_baseUrl_resolvedFromYaml() {
        // The test profile sets openrouter.base-url=http://localhost:0
        assertThat(openRouterProperties.baseUrl())
                .as("Base URL must be resolved from openrouter.base-url property")
                .isEqualTo("http://localhost:0");
    }

    @Test
    void properties_apiKey_resolvedFromYaml() {
        assertThat(openRouterProperties.apiKey())
                .as("API key must be non-blank (resolved from OPENAI_API_KEY or OPENROUTER_API_KEY)")
                .isNotBlank();
    }

    @Test
    void properties_visionModelId_resolvedFromYaml() {
        assertThat(openRouterProperties.visionModel())
                .as("Vision model ID must be configured (OPENROUTER_VISION_MODEL)")
                .isEqualTo("test-vision-model");
    }

    @Test
    void properties_textModelId_resolvedFromYaml() {
        assertThat(openRouterProperties.textModel())
                .as("Text model ID must be configured (OPENROUTER_TEXT_MODEL)")
                .isEqualTo("test-text-model");
    }
}
