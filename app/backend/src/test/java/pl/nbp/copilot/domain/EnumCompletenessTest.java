package pl.nbp.copilot.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TAC-004-06: Enum completeness and Polish label coverage.
 */
class EnumCompletenessTest {

    @Test
    void caseType_hasTwoValues() {
        assertThat(CaseType.values()).hasSize(2);
        assertThat(CaseType.values()).contains(CaseType.COMPLAINT, CaseType.RETURN);
    }

    @Test
    void decisionOutcome_hasThreeValues() {
        assertThat(DecisionOutcome.values()).hasSize(3);
        assertThat(DecisionOutcome.values()).contains(
                DecisionOutcome.APPROVE,
                DecisionOutcome.REJECT,
                DecisionOutcome.ESCALATE);
    }

    @Test
    void equipmentCategory_hasThirteenValues() {
        assertThat(EquipmentCategory.values()).hasSize(13);
    }

    @Test
    void equipmentCategory_containsOther() {
        assertThat(EquipmentCategory.values()).contains(EquipmentCategory.OTHER);
    }

    @Test
    void equipmentCategory_containsAllExpectedValues() {
        assertThat(EquipmentCategory.values()).contains(
                EquipmentCategory.SMARTPHONE,
                EquipmentCategory.TABLET,
                EquipmentCategory.LAPTOP,
                EquipmentCategory.DESKTOP_PC,
                EquipmentCategory.MONITOR,
                EquipmentCategory.TV,
                EquipmentCategory.HEADPHONES_AUDIO,
                EquipmentCategory.CAMERA,
                EquipmentCategory.PRINTER,
                EquipmentCategory.NETWORKING,
                EquipmentCategory.SMARTWATCH_WEARABLE,
                EquipmentCategory.SMALL_APPLIANCE,
                EquipmentCategory.OTHER
        );
    }

    @Test
    void equipmentCategory_everyValueHasNonEmptyPolishLabel() {
        for (EquipmentCategory category : EquipmentCategory.values()) {
            assertThat(category.labelPl())
                    .as("Polish label for %s must not be blank", category)
                    .isNotBlank();
        }
    }
}
