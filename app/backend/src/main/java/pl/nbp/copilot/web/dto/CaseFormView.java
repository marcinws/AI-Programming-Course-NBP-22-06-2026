package pl.nbp.copilot.web.dto;

import pl.nbp.copilot.domain.CaseType;
import pl.nbp.copilot.domain.EquipmentCategory;

import java.time.LocalDate;

/**
 * Read-only view of the submitted case form, returned inside {@link SessionResponse}.
 * Field names mirror the submitted {@code CreateCaseRequest} multipart fields.
 * ADR-001 §4; ADR-004 §4.
 *
 * @param caseType          COMPLAINT or RETURN.
 * @param equipmentCategory Enum from the predefined list.
 * @param modelName         Device model name as submitted.
 * @param purchaseDate      Date of original purchase.
 * @param reason            Optional; required for COMPLAINT, null for RETURN.
 */
public record CaseFormView(
        CaseType caseType,
        EquipmentCategory equipmentCategory,
        String modelName,
        LocalDate purchaseDate,
        String reason
) {
}
