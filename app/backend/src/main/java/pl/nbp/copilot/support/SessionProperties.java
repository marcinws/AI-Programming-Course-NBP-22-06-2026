package pl.nbp.copilot.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Session configuration bound from {@code app.session.*} in application.yaml.
 * ADR-004 §3; ADR-000 §7.
 *
 * @param ttlMinutes Session time-to-live in minutes (default 60).
 */
@ConfigurationProperties(prefix = "app.session")
public record SessionProperties(
        int ttlMinutes
) {
    public SessionProperties {
        if (ttlMinutes <= 0) {
            throw new IllegalArgumentException("app.session.ttl-minutes must be positive, got: " + ttlMinutes);
        }
    }
}
