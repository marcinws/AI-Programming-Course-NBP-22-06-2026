package pl.nbp.copilot.domain;

/**
 * Confidence level of an AI-generated decision or image analysis.
 * ADR-003 §4; ADR-004 §4.
 */
public enum DecisionConfidence {
    /** Low confidence — the model is uncertain; ESCALATE is strongly preferred. */
    LOW,
    /** Medium confidence — the model has reasonable but not full certainty. */
    MEDIUM,
    /** High confidence — the model is strongly certain of its assessment. */
    HIGH
}
