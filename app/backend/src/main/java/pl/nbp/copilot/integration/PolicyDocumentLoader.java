package pl.nbp.copilot.integration;

import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import pl.nbp.copilot.application.port.PolicyProvider;
import pl.nbp.copilot.domain.CaseType;
import pl.nbp.copilot.support.PolicyProperties;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches company procedure documents from the classpath.
 * ADR-003 §6; AC-16; TAC-003-02.
 *
 * <p>Each document is loaded once on first access and cached for the lifetime of the bean.
 * Spring's {@link ResourceLoader} resolves {@code classpath:} and {@code file:} prefixes.</p>
 */
@Component
public class PolicyDocumentLoader implements PolicyProvider {

    private final PolicyProperties properties;
    private final ResourceLoader resourceLoader;
    private final ConcurrentHashMap<CaseType, String> cache = new ConcurrentHashMap<>();

    public PolicyDocumentLoader(PolicyProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public String procedureText(CaseType caseType) {
        return cache.computeIfAbsent(caseType, this::load);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String load(CaseType caseType) {
        String path = switch (caseType) {
            case COMPLAINT -> properties.complaintPath();
            case RETURN -> properties.returnPath();
        };
        try {
            var resource = resourceLoader.getResource(path);
            try (InputStream in = resource.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Nie można załadować dokumentu procedury dla typu sprawy " + caseType + " z lokalizacji: " + path, e);
        }
    }
}
