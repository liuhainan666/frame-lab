package com.framelab.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import com.framelab.common.error.ApiException;
import com.framelab.common.error.ErrorCode;
import com.framelab.config.StorageProperties;
import org.junit.jupiter.api.Test;

class ImageNormalizerTest {

    private final ImageNormalizer normalizer = new ImageNormalizer(new StorageProperties());

    @Test
    void normalizesPngWithoutChangingSmallDimensions() throws Exception {
        NormalizedImage normalized = normalizer.normalize(image("png", 100, 80, BufferedImage.TYPE_INT_ARGB),
                "image/png", List.of("image/jpeg", "image/png"));

        assertThat(normalized.mimeType()).isEqualTo("image/png");
        assertThat(normalized.extension()).isEqualTo("png");
        assertThat(normalized.width()).isEqualTo(100);
        assertThat(normalized.height()).isEqualTo(80);
        assertThat(ImageIO.read(new ByteArrayInputStream(normalized.bytes()))).isNotNull();
    }

    @Test
    void scalesLongestSideToTheNormalizedLimit() throws Exception {
        NormalizedImage normalized = normalizer.normalize(image("jpg", 5000, 10, BufferedImage.TYPE_INT_RGB),
                "image/jpeg", List.of("image/jpeg", "image/png"));

        assertThat(normalized.mimeType()).isEqualTo("image/jpeg");
        assertThat(normalized.width()).isEqualTo(4096);
        assertThat(normalized.height()).isEqualTo(8);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(normalized.bytes()));
        assertThat(decoded.getWidth()).isEqualTo(4096);
        assertThat(decoded.getHeight()).isEqualTo(8);
    }

    @Test
    void appliesExifOrientationSix() throws Exception {
        byte[] source = withExifOrientation(image("jpg", 100, 60, BufferedImage.TYPE_INT_RGB), 6);

        NormalizedImage normalized = normalizer.normalize(source, "image/jpeg",
                List.of("image/jpeg", "image/png"));

        assertThat(normalized.width()).isEqualTo(60);
        assertThat(normalized.height()).isEqualTo(100);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(normalized.bytes()));
        assertThat(decoded.getWidth()).isEqualTo(60);
        assertThat(decoded.getHeight()).isEqualTo(100);
    }

    @Test
    void rejectsFakeContentAndUnsupportedDeclaredTypes() {
        assertThatThrownBy(() -> normalizer.normalize("not-an-image".getBytes(), "image/jpeg",
                List.of("image/jpeg", "image/png")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_IMAGE));

        assertThatThrownBy(() -> normalizer.normalize("<svg/>".getBytes(), "image/svg+xml",
                List.of("image/jpeg", "image/png")))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    private byte[] image(String format, int width, int height, int type) throws IOException {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, Math.max(1, width / 2), Math.max(1, height / 2));
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }

    private byte[] withExifOrientation(byte[] jpeg, int orientation) throws IOException {
        byte[] tiff = new byte[] {
                'M', 'M', 0, 42, 0, 0, 0, 8,
                0, 1,
                0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0,
                0, 0, 0, 0
        };
        int payloadLength = 6 + tiff.length;
        int segmentLength = payloadLength + 2;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(jpeg, 0, 2);
        output.write(0xFF);
        output.write(0xE1);
        output.write((segmentLength >> 8) & 0xFF);
        output.write(segmentLength & 0xFF);
        output.write('E');
        output.write('x');
        output.write('i');
        output.write('f');
        output.write(0);
        output.write(0);
        output.write(tiff);
        output.write(jpeg, 2, jpeg.length - 2);
        return output.toByteArray();
    }
}
