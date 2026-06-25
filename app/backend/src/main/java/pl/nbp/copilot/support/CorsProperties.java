package pl.nbp.copilot.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS configuration bound from {@code app.cors.*} in application.yaml.
 * ADR-001 §6; ADR-000 §8; TAC-010.
 *
 * @param allowedOrigin The SPA origin allowed to make cross-origin requests
 *                      (default {@code http://localhost:4200}; env override: {@code APP_CORS_ALLOWED_ORIGIN}).
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(String allowedOrigin) {
}
