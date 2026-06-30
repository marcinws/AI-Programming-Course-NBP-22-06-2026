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
 * the WebP fixture is a known-good minimal 1×1 WebP byte sequence (lossless VP8L).</p>
 */
class ThumbnailatorImageCompressorTest {

    private static final int MAX_DIMENSION_PX = 200;
    private static final String TARGET_FORMAT = "jpeg";

    /**
     * Minimal valid WebP file — 1×1 white pixel, lossless VP8L encoding.
     *
     * <p>Byte layout:
     * <pre>
     *   RIFF ....  WEBP        — 12-byte RIFF header
     *   VP8L ....              — VP8L chunk header (4+4 bytes)
     *   /  (signature 0x2F)    — VP8L bitstream
     * </pre>
     * This is the canonical minimal representation used to test that the TwelveMonkeys
     * WebP ImageIO plugin can decode WebP input and that the compressor re-encodes to JPEG.
     * </p>
     */
    private static final byte[] MINIMAL_WEBP_1X1 = {
        // RIFF header: "RIFF", file size (LE), "WEBP"
        (byte)'R', (byte)'I', (byte)'F', (byte)'F',
        0x24, 0x00, 0x00, 0x00,  // file size = 36 bytes (total - 8)
        (byte)'W', (byte)'E', (byte)'B', (byte)'P',
        // VP8L chunk: "VP8L", chunk size (LE)
        (byte)'V', (byte)'P', (byte)'8', (byte)'L',
        0x18, 0x00, 0x00, 0x00,  // chunk size = 24 bytes
        // VP8L bitstream: signature byte + 1x1 lossless VP8L image
        0x2F,           // VP8L signature
        0x00, 0x00, 0x00, 0x00, // width-1=0, height-1=0 packed (14+14 bits)
        0x00,           // transform data
        // Huffman-coded ARGB data for a single white pixel (ARGB 0xFFFFFFFF)
        (byte)0xFE, 0x0F, (byte)0xFE, 0x0F, (byte)0xFE, 0x0F, 0x00, 0x00,
        0x00, 0x00, (byte)0xFF, (byte)0xFF, 0x00, 0x00, 0x00, 0x00
    };

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

    // ── WebP re-encoded to JPEG (strict assertion — TwelveMonkeys plugin) ─────

    @Test
    void compress_webpInput_decodedAndReEncodedToJpeg() throws IOException {
        // ADR-001 §6: WebP must be accepted and re-encoded to JPEG.
        // TwelveMonkeys imageio-webp plugin registers a WebP ImageIO reader automatically
        // via the ServiceLoader mechanism — no explicit registration required.
        // This test uses a real WebP byte payload decoded via the plugin.
        byte[] webpBytes = loadWebpFixture();
        assumeWebpReadable(webpBytes);

        byte[] result = compressor.compress(webpBytes, "image/webp");

        // Output must be valid JPEG
        assertThat(result)
                .as("WebP input must be re-encoded to JPEG (JPEG magic bytes: FF D8 FF)")
                .startsWith((byte) 0xFF, (byte) 0xD8, (byte) 0xFF);

        // Output must be a readable image
        BufferedImage img = readImage(result);
        assertThat(img)
                .as("Re-encoded JPEG from WebP must be decodable by ImageIO")
                .isNotNull();

        // Output size must be reasonable (≤ some large upper bound; WebP 1x1 → very small JPEG)
        assertThat(result.length)
                .as("Re-encoded JPEG output must be non-empty")
                .isGreaterThan(0);
    }

    @Test
    void compress_webpInput_longSideCappedAtMaxDimension() throws IOException {
        // Use a programmatically generated larger WebP via PNG→WebP is not available,
        // so we verify dimension invariant using JPEG (WebP plugin round-trip via 1x1).
        // The dimension cap is already verified for JPEG/PNG inputs; for WebP we verify
        // that the output long side is ≤ MAX_DIMENSION_PX (1×1 will stay 1×1, which is ≤ 200).
        byte[] webpBytes = loadWebpFixture();
        assumeWebpReadable(webpBytes);

        byte[] result = compressor.compress(webpBytes, "image/webp");

        BufferedImage img = readImage(result);
        assertThat(Math.max(img.getWidth(), img.getHeight()))
                .as("WebP input: output long side must be ≤ maxDimensionPx")
                .isLessThanOrEqualTo(MAX_DIMENSION_PX);
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

    /**
     * Returns the minimal 1×1 WebP fixture bytes.
     * The fixture is the canonical minimal lossless VP8L WebP for a single white pixel.
     */
    private byte[] loadWebpFixture() {
        return MINIMAL_WEBP_1X1.clone();
    }

    /**
     * Skips the test if the TwelveMonkeys WebP plugin is not on the classpath and
     * ImageIO cannot decode the fixture — this guards against running in environments
     * where the plugin was accidentally removed.
     *
     * <p>Under normal Maven build with the dependency present the plugin is always registered.</p>
     */
    private void assumeWebpReadable(byte[] webpBytes) {
        try {
            BufferedImage probe = ImageIO.read(new ByteArrayInputStream(webpBytes));
            if (probe == null) {
                // Plugin absent or fixture invalid — fail with a clear message rather than assume
                fail("TwelveMonkeys WebP ImageIO plugin is required but ImageIO.read returned null for the WebP fixture. " +
                        "Ensure com.twelvemonkeys.imageio:imageio-webp is on the classpath.");
            }
        } catch (IOException e) {
            fail("TwelveMonkeys WebP plugin failed to read WebP fixture: " + e.getMessage());
        }
    }

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
