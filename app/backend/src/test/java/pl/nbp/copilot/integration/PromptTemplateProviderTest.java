package pl.nbp.copilot.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pl.nbp.copilot.application.port.PromptProvider;
import pl.nbp.copilot.domain.CaseType;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link PromptTemplateProvider}.
 * Verifies that all prompt templates load correctly and contain key guardrail phrases.
 * ADR-003 §3/§6; AC-10/11/15/16/26; TAC-006.
 */
@SpringBootTest
@ActiveProfiles("test")
class PromptTemplateProviderTest {

    @Autowired
    private PromptProvider promptProvider;

    // ── System prompt ─────────────────────────────────────────────────────────

    @Test
    void systemPrompt_isNonEmpty() {
        String prompt = promptProvider.systemPrompt();
        assertThat(prompt).isNotBlank();
    }

    @Test
    void systemPrompt_containsAdvisoryGuardrail() {
        String prompt = promptProvider.systemPrompt();
        // Must contain advisory-only language (PRD §11; AC-26)
        assertThat(prompt.toLowerCase())
                .as("System prompt must state advisory-only nature")
                .containsAnyOf("doradczy", "doradcz", "niewiążący", "niewiążąc");
    }

    @Test
    void systemPrompt_containsEscalateWhenUnsure_guardrail() {
        String prompt = promptProvider.systemPrompt();
        // Must instruct to ESCALATE when unsure (PRD §11; AC-17)
        assertThat(prompt.toLowerCase())
                .as("System prompt must mention ESCALATE for unsure cases")
                .containsAnyOf("eskaluj", "eskal");
    }

    @Test
    void systemPrompt_containsOffTopicRedirect_guardrail() {
        String prompt = promptProvider.systemPrompt();
        // Must handle off-topic by redirecting (AC-22)
        assertThat(prompt.toLowerCase())
                .as("System prompt must address off-topic questions")
                .containsAnyOf("niezwiązany", "temat", "przekier");
    }

    @Test
    void systemPrompt_containsMandatoryDisclaimerPhrase() {
        String prompt = promptProvider.systemPrompt();
        // AC-26: mandatory advisory disclaimer
        assertThat(prompt)
                .as("System prompt must include the mandatory advisory disclaimer")
                .containsAnyOf("Zastrzeżenie", "zastrzeżenie", "doradczy");
    }

    // ── Analysis prompts ──────────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(CaseType.class)
    void analysisPrompt_allCaseTypes_nonEmpty(CaseType caseType) {
        String prompt = promptProvider.analysisPrompt(caseType);
        assertThat(prompt)
                .as("Analysis prompt for %s must be non-empty", caseType)
                .isNotBlank();
    }

    @Test
    void analysisPrompt_complaint_mentionsComplaintContext() {
        String prompt = promptProvider.analysisPrompt(CaseType.COMPLAINT);
        assertThat(prompt.toLowerCase())
                .as("Complaint analysis prompt must be specific to complaint/warranty context")
                .containsAnyOf("reklamacj", "gwarancj", "rękojm");
    }

    @Test
    void analysisPrompt_return_mentionsReturnContext() {
        String prompt = promptProvider.analysisPrompt(CaseType.RETURN);
        assertThat(prompt.toLowerCase())
                .as("Return analysis prompt must be specific to return context")
                .containsAnyOf("zwrot", "odsprzedaż", "odsprzeday");
    }

    @Test
    void analysisPrompt_complaint_forbidsDecision() {
        String prompt = promptProvider.analysisPrompt(CaseType.COMPLAINT);
        // AC-10: vision model describes only — never decides
        assertThat(prompt.toLowerCase())
                .as("Analysis prompt must forbid making a decision")
                .containsAnyOf("opisujesz", "nie wydajesz", "tylko to, co widzisz", "opisuj");
    }

    @Test
    void analysisPrompt_return_forbidsDecision() {
        String prompt = promptProvider.analysisPrompt(CaseType.RETURN);
        assertThat(prompt.toLowerCase())
                .as("Return analysis prompt must forbid making a decision")
                .containsAnyOf("opisujesz", "nie wydajesz", "tylko to, co widzisz", "opisuj");
    }

    @Test
    void analysisPrompts_complaintAndReturnAreDistinct() {
        String complaint = promptProvider.analysisPrompt(CaseType.COMPLAINT);
        String returnPrompt = promptProvider.analysisPrompt(CaseType.RETURN);
        assertThat(complaint)
                .as("Complaint and return analysis prompts must be different")
                .isNotEqualTo(returnPrompt);
    }

    // ── Decision prompts ──────────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(CaseType.class)
    void decisionPrompt_allCaseTypes_nonEmpty(CaseType caseType) {
        String prompt = promptProvider.decisionPrompt(caseType);
        assertThat(prompt)
                .as("Decision prompt for %s must be non-empty", caseType)
                .isNotBlank();
    }

    @Test
    void decisionPrompt_complaint_mentionsProcedureInjectionPlaceholder() {
        String prompt = promptProvider.decisionPrompt(CaseType.COMPLAINT);
        // AC-16: procedure injected into decision prompt
        assertThat(prompt)
                .as("Decision prompt must contain procedure placeholder or reference")
                .containsAnyOf("{{procedureText}}", "procedura", "Procedura");
    }

    @Test
    void decisionPrompt_return_mentionsProcedureInjectionPlaceholder() {
        String prompt = promptProvider.decisionPrompt(CaseType.RETURN);
        assertThat(prompt)
                .as("Return decision prompt must contain procedure placeholder or reference")
                .containsAnyOf("{{procedureText}}", "procedura", "Procedura");
    }

    @Test
    void decisionPrompt_complaint_containsEscalateWhenUnsure() {
        String prompt = promptProvider.decisionPrompt(CaseType.COMPLAINT);
        assertThat(prompt.toLowerCase())
                .as("Complaint decision prompt must instruct ESCALATE when unsure")
                .containsAnyOf("eskaluj", "eskal");
    }

    @Test
    void decisionPrompt_return_containsEscalateWhenUnsure() {
        String prompt = promptProvider.decisionPrompt(CaseType.RETURN);
        assertThat(prompt.toLowerCase())
                .as("Return decision prompt must instruct ESCALATE when unsure")
                .containsAnyOf("eskaluj", "eskal");
    }

    @Test
    void decisionPrompts_containsMandatoryDisclaimer() {
        for (CaseType caseType : CaseType.values()) {
            String prompt = promptProvider.decisionPrompt(caseType);
            assertThat(prompt)
                    .as("Decision prompt for %s must include mandatory advisory disclaimer (AC-26)", caseType)
                    .containsAnyOf("Zastrzeżenie", "zastrzeżenie", "doradczy");
        }
    }

    @Test
    void decisionPrompts_complaintAndReturnAreDistinct() {
        String complaint = promptProvider.decisionPrompt(CaseType.COMPLAINT);
        String returnPrompt = promptProvider.decisionPrompt(CaseType.RETURN);
        assertThat(complaint)
                .as("Complaint and return decision prompts must be different")
                .isNotEqualTo(returnPrompt);
    }

    // ── Chat prompt ───────────────────────────────────────────────────────────

    @Test
    void chatPrompt_isNonEmpty() {
        String prompt = promptProvider.chatPrompt();
        assertThat(prompt).isNotBlank();
    }

    @Test
    void chatPrompt_containsAdvisoryGuardrail() {
        String prompt = promptProvider.chatPrompt();
        assertThat(prompt.toLowerCase())
                .as("Chat prompt must state advisory-only nature (AC-26)")
                .containsAnyOf("doradczy", "doradcz", "niewiążący");
    }

    @Test
    void chatPrompt_containsOffTopicHandling() {
        String prompt = promptProvider.chatPrompt();
        // AC-22: off-topic → polite redirect
        assertThat(prompt.toLowerCase())
                .as("Chat prompt must address off-topic handling")
                .containsAnyOf("niezwiązan", "temat", "odmaw");
    }

    @Test
    void chatPrompt_containsEscalateWhenUnsure() {
        String prompt = promptProvider.chatPrompt();
        assertThat(prompt.toLowerCase())
                .as("Chat prompt must instruct ESCALATE when unsure")
                .containsAnyOf("eskaluj", "eskal", "eskalacj");
    }

    @Test
    void chatPrompt_containsMandatoryDisclaimer() {
        String prompt = promptProvider.chatPrompt();
        assertThat(prompt)
                .as("Chat prompt must include mandatory advisory disclaimer (AC-26)")
                .containsAnyOf("Zastrzeżenie", "zastrzeżenie", "doradczy");
    }
}
