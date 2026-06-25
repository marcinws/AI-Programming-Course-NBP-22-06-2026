package pl.nbp.copilot.domain;

/** Outcome of the AI-assisted service decision. */
public enum DecisionOutcome {
    /** Approve the complaint or return request. */
    APPROVE,
    /** Reject the complaint or return request. */
    REJECT,
    /**
     * Escalate to a human supervisor — used when the AI lacks confidence
     * or the case is ambiguous.
     */
    ESCALATE
}
