package pl.nbp.copilot.web.dto;

import java.util.List;

/**
 * Image upload constraints returned by {@link MetadataResponse}.
 * Reflects the server-side configuration so the frontend can enforce them client-side.
 * ADR-001 §4; ADR-000 §7.
 *
 * @param acceptedTypes List of accepted MIME types (e.g. ["image/jpeg", "image/png", "image/webp"]).
 * @param maxBytes      Maximum allowed upload size in bytes.
 */
public record ImageConstraints(
        List<String> acceptedTypes,
        long maxBytes
) {
}
