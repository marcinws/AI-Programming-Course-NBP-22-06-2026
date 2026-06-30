package pl.nbp.copilot.web.dto;

import java.util.List;

/**
 * Response body for {@code GET /api/metadata} (HTTP 200).
 * Drives form selector options and image upload validation on the frontend.
 * ADR-001 §4; ADR-002 §6.
 *
 * @param caseTypes            Available case types with Polish labels.
 * @param equipmentCategories  Available equipment categories with Polish labels.
 * @param imageConstraints     Accepted image types and maximum upload size.
 */
public record MetadataResponse(
        List<Option> caseTypes,
        List<Option> equipmentCategories,
        ImageConstraints imageConstraints
) {
}
