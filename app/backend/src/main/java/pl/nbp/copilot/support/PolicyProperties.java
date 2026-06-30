package pl.nbp.copilot.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Policy document configuration bound from {@code app.policy.*} in application.yaml.
 * ADR-003 §6; ADR-000 §7.
 *
 * @param complaintPath Spring resource path to the complaint procedure document (default classpath:/policies/complaint-procedure.md).
 * @param returnPath    Spring resource path to the return procedure document (default classpath:/policies/return-procedure.md).
 */
@ConfigurationProperties(prefix = "app.policy")
public record PolicyProperties(
        String complaintPath,
        String returnPath
) {
}
