package pl.nbp.copilot.support;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;
import pl.nbp.copilot.application.port.ImageCompressor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * {@link ImageCompressor} implementation using Thumbnailator.
 * Caps the long side at {@code app.image.max-dimension-px} (no upscaling), re-encodes to
 * {@code app.image.target-format} (JPEG) at ~0.8 quality.
 * ADR-001 §6; AC-09; TAC-001-03.
 *
 * <p>WebP: the standard JRE lacks a WebP ImageIO plugin. If ImageIO cannot decode
 * the input bytes, this implementation throws {@link IOException} (not a runtime exception),
 * which is the documented contract for the WebP fallback path.</p>
 */
@Component
public class ThumbnailatorImageCompressor implements ImageCompressor {

    private static final double OUTPUT_QUALITY = 0.80;

    private final ImageProperties properties;

    public ThumbnailatorImageCompressor(ImageProperties properties) {
        this.properties = properties;
    }

    @Override
    public byte[] compress(byte[] inputBytes, String mimeType) throws IOException {
        int maxDimension = properties.maxDimensionPx();
        String outputFormat = properties.targetFormat(); // e.g. "jpeg"

        // Read to determine actual dimensions (also validates the image can be decoded)
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(inputBytes));
        if (source == null) {
            throw new IOException("Cannot decode image bytes (unsupported format or corrupt data): " + mimeType);
        }

        int w = source.getWidth();
        int h = source.getHeight();
        int longSide = Math.max(w, h);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        if (longSide <= maxDimension) {
            // Already within bounds — re-encode only (no upscaling)
            Thumbnails.of(source)
                    .scale(1.0)
                    .outputFormat(outputFormat)
                    .outputQuality(OUTPUT_QUALITY)
                    .toOutputStream(out);
        } else {
            // Cap long side; Thumbnailator's size() respects aspect ratio
            Thumbnails.of(source)
                    .size(maxDimension, maxDimension)
                    .keepAspectRatio(true)
                    .outputFormat(outputFormat)
                    .outputQuality(OUTPUT_QUALITY)
                    .toOutputStream(out);
        }

        return out.toByteArray();
    }
}
