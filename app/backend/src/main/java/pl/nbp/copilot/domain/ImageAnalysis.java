package pl.nbp.copilot.domain;

/**
 * Immutable value object representing the multimodal image analysis result.
 * The vision model produces a neutral condition description; it never decides approve/reject.
 * ADR-003 §4; ADR-004 §4; TAC-004-05.
 *
 * <p>All flag fields use boxed {@link Boolean} so that {@code null} means "unknown/not determinable"
 * rather than false — absence of a flag is semantically distinct from a negative finding.</p>
 *
 * @param description     Textual description of the equipment's visible condition (Polish, Markdown-safe).
 * @param damageObserved  {@code true} if visible damage was observed; {@code false} if clearly none; {@code null} if undeterminable.
 * @param signsOfUse      {@code true} if signs of use were observed; {@code false} if clearly unused; {@code null} if undeterminable.
 * @param usableForResale {@code true} if the item appears resellable; {@code false} if clearly not; {@code null} if undeterminable.
 * @param confidence      Model's confidence in the analysis (may be null if not reported).
 */
public record ImageAnalysis(
        String description,
        Boolean damageObserved,
        Boolean signsOfUse,
        Boolean usableForResale,
        DecisionConfidence confidence
) {
}
