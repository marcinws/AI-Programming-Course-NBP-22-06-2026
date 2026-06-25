package pl.nbp.copilot.domain;

import java.time.LocalDate;

/**
 * Immutable value object representing the intake form data for a service case.
 * ADR-004 §4; TAC-004-05.
 *
 * @param caseType          Type of the service case (COMPLAINT or RETURN).
 * @param equipmentCategory Category of the equipment being serviced.
 * @param modelName         Equipment model name or description (required, ≤200 chars).
 * @param purchaseDate      Date of purchase; must not be in the future.
 * @param reason            Reason for complaint or return; required iff caseType == COMPLAINT (≤4000 chars).
 */
public record CaseForm(
        CaseType caseType,
        EquipmentCategory equipmentCategory,
        String modelName,
        LocalDate purchaseDate,
        String reason
) {
}
