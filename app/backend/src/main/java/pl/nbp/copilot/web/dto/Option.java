package pl.nbp.copilot.web.dto;

/**
 * Generic select-option used in {@link MetadataResponse} for case types and equipment categories.
 * ADR-001 §4.
 *
 * @param id      Enum constant name used as the form submit value (e.g. "COMPLAINT", "LAPTOP").
 * @param labelPl Human-readable Polish display label.
 */
public record Option(
        String id,
        String labelPl
) {
}
