package pl.nbp.copilot.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ThumbnailatorImageCompressor}.
 * ADR-001 §6; AC-09; TAC-001-03.
 *
 * <p>Test images are generated programmatically via {@link BufferedImage}/{@link ImageIO};
 * no external files are required.</p>
 */
class ThumbnailatorImageCompressorTest {

    private static final int MAX_DIMENSION_PX = 200;
    private static final String TARGET_FORMAT = "jpeg";

    private ThumbnailatorImageCompressor compressor;
    private ImageProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ImageProperties(
                10_485_760L,
                MAX_DIMENSION_PX,
                TARGET_FORMAT,
                java.util.List.of("image/jpeg", "image/png", "image/webp")
        );
        compressor = new ThumbnailatorImageCompressor(properties);
    }

    // ── Dimension invariants ──────────────────────────────────────────────────

    @Test
    void compress_largeJpeg_longSideCappedAtMaxDimension() throws IOException {
        byte[] input = createJpeg(400, 300);

        byte[] result = compressor.compress(input, "image/jpeg");

        BufferedImage img = readImage(result);
        assertThat(Math.max(img.getWidth(), img.getHeight()))
                .as("long side should be ≤ maxDimensionPx")
                .isLessThanOrEqualTo(MAX_DIMENSION_PX);
    }

    @Test
    void compress_tallJpeg_longSideCappedAtMaxDimension() throws IOException {
        byte[] input = createJpeg(150, 500); // taller than wide

        byte[] result = compressor.compress(input, "image/jpeg");

        BufferedImage img = readImage(result);
        assertThat(Math.max(img.getWidth(), img.getHeight()))
                .as("long side (height) should be ≤ maxDimensionPx")
                .isLessThanOrEqualTo(MAX_DIMENSION_PX);
    }

    @Test
    void compress_smallJpeg_notUpscaled() throws IOException {
        // Image already smaller than max → should not be upscaled
        byte[] input = createJpeg(100, 80);

        byte[] result = compressor.compress(input, "image/jpeg");

        BufferedImage img = readImage(result);
        assertThat(img.getWidth()).as("width should not exceed original").isLessThanOrEqualTo(100);
        assertThat(img.getHeight()).as("height should not exceed original").isLessThanOrEqualTo(80);
    }

    @Test
    void compress_largePng_longSideCappedAtMaxDimension() throws IOException {
        byte[] input = createPng(600, 200);

        byte[] result = compressor.compress(input, "image/png");

        BufferedImage img = readImage(result);
        assertThat(Math.max(img.getWidth(), img.getHeight()))
                .as("long side should be ≤ maxDimensionPx for PNG input")
                .isLessThanOrEqualTo(MAX_DIMENSION_PX);
    }

    // ── Format invariant: output is always JPEG ───────────────────────────────

    @Test
    void compress_pngInput_outputIsJpeg() throws IOException {
        byte[] input = createPng(300, 200);

        byte[] result = compressor.compress(input, "image/png");

        // JPEG magic bytes: FF D8 FF
        assertThat(result).as("output should start with JPEG magic bytes").startsWith(
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF
        );
    }

    @Test
    void compress_jpegInput_outputIsJpeg() throws IOException {
        byte[] input = createJpeg(300, 200);

        byte[] result = compressor.compress(input, "image/jpeg");

        assertThat(result).as("output should start with JPEG magic bytes").startsWith(
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF
        );
    }

    // ── WebP re-encoded to JPEG ───────────────────────────────────────────────

    @Test
    void compress_webpMimeType_outputIsJpegOrNotThrown() throws IOException {
        // WebP decode may be unavailable in plain JRE.
        // The compressor should either:
        //   a) Succeed and return JPEG-encoded bytes (if JRE supports WebP via ImageIO plugins), or
        //   b) Fall back and still produce output without throwing an unchecked exception.
        // This test verifies the "accept WebP and re-encode" path exists and does not blow up.
        // We use a valid JPEG payload with a webp mime hint to exercise the fallback path.
        byte[] jpegPayload = createJpeg(100, 80);

        // The compressor accepts WebP mime; since the JRE may lack a WebP decoder,
        // it should fall back to treating the bytes as an opaque image and attempt re-encode.
        // We assert: either the output is valid JPEG, or an IOException (not RuntimeException) is thrown.
        try {
            byte[] result = compressor.compress(jpegPayload, "image/webp");
            assertThat(result).as("output should start with JPEG magic bytes").startsWith(
                    (byte) 0xFF, (byte) 0xD8, (byte) 0xFF
            );
        } catch (IOException e) {
            // Acceptable: WebP decode path threw IOException (JRE without WebP plugin)
            assertThat(e).as("only IOException is acceptable, not a runtime exception").isInstanceOf(IOException.class);
        }
    }

    // ── Output size invariant ─────────────────────────────────────────────────

    @Test
    void compress_largeJpeg_outputSmallerOrEqualToInput() throws IOException {
        // Create a large image; Thumbnailator should produce a smaller JPEG
        byte[] input = createJpeg(800, 600);

        byte[] result = compressor.compress(input, "image/jpeg");

        assertThat(result.length)
                .as("compressed output should be smaller than the large input")
                .isLessThanOrEqualTo(input.length);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private byte[] createJpeg(int width, int height) throws IOException {
        return encode(width, height, "jpg", BufferedImage.TYPE_INT_RGB);
    }

    private byte[] createPng(int width, int height) throws IOException {
        return encode(width, height, "png", BufferedImage.TYPE_INT_ARGB);
    }

    private byte[] encode(int width, int height, String format, int imageType) throws IOException {
        BufferedImage img = new BufferedImage(width, height, imageType);
        Graphics2D g = img.createGraphics();
        // Fill with a gradient to have non-trivial content for compression
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width / 2, height);
        g.setColor(Color.RED);
        g.fillRect(width / 2, 0, width / 2, height);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private BufferedImage readImage(byte[] bytes) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(img).as("output bytes must be a valid image readable by ImageIO").isNotNull();
        return img;
    }
}
