package pl.nbp.copilot.application.port;

import pl.nbp.copilot.domain.CaseType;

/**
 * Port for loading company procedure documents.
 * Procedure text is injected into the decision prompt per AC-16 and ADR-003 §6.
 */
public interface PolicyProvider {

    /**
     * Returns the full text of the procedure document for the given case type.
     * The returned text is ready to be injected verbatim into the decision prompt.
     *
     * @param caseType COMPLAINT → complaint procedure; RETURN → return procedure
     * @return procedure document text (Polish Markdown)
     * @throws IllegalStateException if the document cannot be loaded
     */
    String procedureText(CaseType caseType);
}
