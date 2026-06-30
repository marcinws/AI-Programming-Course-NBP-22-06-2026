package pl.nbp.copilot.web.dto;

import pl.nbp.copilot.domain.CaseType;
import pl.nbp.copilot.domain.EquipmentCategory;

import java.time.LocalDate;

/**
 * Multipart form-binding type for {@code POST /api/cases}.
 * The {@code image} part is bound separately as {@code MultipartFile} in the controller.
 * Full field-level validation (cross-field reason constraint, date not-future) is added in a
 * later step; this record defines the shape and field names only.
 * ADR-001 §4.
 *
 * @param caseType          COMPLAINT or RETURN (required).
 * @param equipmentCategory Equipment category from the predefined list (required).
 * @param modelName         Device model name; trimmed, non-empty, max 200 chars (required).
 * @param purchaseDate      Date of purchase in ISO-8601 format; must not be in the future (required).
 * @param reason            Description of the complaint; required iff {@code caseType == COMPLAINT}.
 */
public record CreateCaseRequest(
        CaseType caseType,
        EquipmentCategory equipmentCategory,
        String modelName,
        LocalDate purchaseDate,
        String reason
) {
}
