package pl.nbp.copilot.integration;

import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import pl.nbp.copilot.application.port.PromptProvider;
import pl.nbp.copilot.domain.CaseType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches LLM prompt templates from {@code resources/prompts/}.
 * ADR-003 §3/§6; AC-10/11/15/16/26.
 *
 * <p>Template files (Markdown) are loaded once on first access. Spring's {@link ResourceLoader}
 * resolves {@code classpath:} paths.</p>
 */
@Component
public class PromptTemplateProvider implements PromptProvider {

    private static final String PROMPTS_BASE = "classpath:/prompts/";

    private final ResourceLoader resourceLoader;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public PromptTemplateProvider(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public String systemPrompt() {
        return loadTemplate("system.md");
    }

    @Override
    public String analysisPrompt(CaseType caseType) {
        String filename = switch (caseType) {
            case COMPLAINT -> "analysis-complaint.md";
            case RETURN -> "analysis-return.md";
        };
        return loadTemplate(filename);
    }

    @Override
    public String decisionPrompt(CaseType caseType) {
        String filename = switch (caseType) {
            case COMPLAINT -> "decision-complaint.md";
            case RETURN -> "decision-return.md";
        };
        return loadTemplate(filename);
    }

    @Override
    public String chatPrompt() {
        return loadTemplate("chat.md");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String loadTemplate(String filename) {
        return cache.computeIfAbsent(filename, name -> {
            String path = PROMPTS_BASE + name;
            try {
                var resource = resourceLoader.getResource(path);
                try (InputStream in = resource.getInputStream()) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Nie można załadować szablonu promptu: " + path, e);
            }
        });
    }
}
