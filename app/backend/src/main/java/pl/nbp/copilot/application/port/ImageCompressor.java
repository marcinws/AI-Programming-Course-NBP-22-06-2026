package pl.nbp.copilot.application.port;

import java.io.IOException;

/**
 * Port for server-side image compression and resizing before the LLM vision call.
 * ADR-001 §6; AC-09; TAC-001-03.
 *
 * <p>Implementations must:
 * <ul>
 *   <li>Cap the long side of the image at {@code app.image.max-dimension-px}.</li>
 *   <li>Re-encode the output to {@code app.image.target-format} (default JPEG).</li>
 *   <li>Ensure the output byte count is ≤ the input byte count for same-format inputs
 *       at reasonable dimensions.</li>
 *   <li>Accept WebP input and re-encode to JPEG even if the JRE lacks a native WebP decoder.</li>
 * </ul>
 * </p>
 */
public interface ImageCompressor {

    /**
     * Compresses and optionally resizes the given image bytes.
     *
     * @param inputBytes raw image bytes (JPEG, PNG, or WebP)
     * @param mimeType   MIME type of the input (e.g. {@code "image/jpeg"})
     * @return optimized image bytes in the configured target format
     * @throws IOException if the image cannot be read or written
     */
    byte[] compress(byte[] inputBytes, String mimeType) throws IOException;
}
