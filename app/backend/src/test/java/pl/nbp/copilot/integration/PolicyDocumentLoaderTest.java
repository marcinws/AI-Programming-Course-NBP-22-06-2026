package pl.nbp.copilot.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pl.nbp.copilot.application.port.PolicyProvider;
import pl.nbp.copilot.domain.CaseType;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link PolicyDocumentLoader}.
 * Verifies that procedure documents load correctly for each case type.
 * ADR-003 §6; AC-16; TAC-003-02.
 */
@SpringBootTest
@ActiveProfiles("test")
class PolicyDocumentLoaderTest {

    @Autowired
    private PolicyProvider policyProvider;

    @Test
    void procedureText_complaint_returnsComplaintProcedure() {
        String text = policyProvider.procedureText(CaseType.COMPLAINT);

        assertThat(text)
                .as("Complaint procedure text must be non-empty")
                .isNotBlank();
        // Key phrases from the complaint procedure document
        assertThat(text)
                .as("Should contain complaint-specific content")
                .containsIgnoringCase("reklamacj");
        assertThat(text)
                .as("Should reference warranty/liability period")
                .containsIgnoringCase("miesięcy");
    }

    @Test
    void procedureText_return_returnsReturnProcedure() {
        String text = policyProvider.procedureText(CaseType.RETURN);

        assertThat(text)
                .as("Return procedure text must be non-empty")
                .isNotBlank();
        // Key phrases from the return procedure document
        assertThat(text)
                .as("Should contain return-specific content")
                .containsIgnoringCase("zwrot");
        assertThat(text)
                .as("Should reference 14-day return window")
                .containsIgnoringCase("14 dni");
    }

    @Test
    void procedureText_complaintAndReturnAreDistinct() {
        String complaint = policyProvider.procedureText(CaseType.COMPLAINT);
        String returnProc = policyProvider.procedureText(CaseType.RETURN);

        assertThat(complaint)
                .as("Complaint and return procedures must be different documents")
                .isNotEqualTo(returnProc);
    }

    @Test
    void procedureText_cachedOnSecondCall_returnsSameContent() {
        String first = policyProvider.procedureText(CaseType.COMPLAINT);
        String second = policyProvider.procedureText(CaseType.COMPLAINT);

        assertThat(first).isEqualTo(second);
    }
}
