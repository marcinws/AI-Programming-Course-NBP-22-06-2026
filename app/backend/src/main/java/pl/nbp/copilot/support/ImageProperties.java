package pl.nbp.copilot.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Image upload configuration bound from {@code app.image.*} in application.yaml.
 * ADR-000 §7; ADR-001 §5.
 *
 * @param maxUploadBytes Maximum allowed upload size in bytes (default 10 MB).
 * @param maxDimensionPx Long-side resize cap before the LLM vision call (default 2048 px).
 * @param targetFormat   Re-encode target format, e.g. "jpeg" (default "jpeg").
 * @param acceptedTypes  Accepted MIME types (default image/jpeg, image/png, image/webp).
 */
@ConfigurationProperties(prefix = "app.image")
public record ImageProperties(
        long maxUploadBytes,
        int maxDimensionPx,
        String targetFormat,
        List<String> acceptedTypes
) {
}
