package com.framelab.task.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.util.List;

import javax.imageio.ImageIO;

import com.framelab.template.TemplateKind;
import com.framelab.template.spec.ImageRenderSpec;
import com.framelab.template.spec.LayoutSpec;
import com.framelab.template.spec.LookSpec;
import com.framelab.template.spec.PortraitSpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EffectRenderingTest {

    private final LutRegistry lutRegistry = new LutRegistry();
    private final ColorPipeline colorPipeline = new ColorPipeline(lutRegistry);
    private final PortraitProcessor portraitProcessor = new PortraitProcessor();
    private final GlobalEffects globalEffects = new GlobalEffects();
    private final ResultRenderer resultRenderer = new ResultRenderer();
    private final EffectResultRenderer effectRenderer = new EffectResultRenderer(
            colorPipeline, portraitProcessor, globalEffects, resultRenderer);

    @Test
    void parsesAndSamplesIdentityCubeLut() throws Exception {
        CubeLut lut = CubeLut.parse(new StringReader("""
                TITLE "identity"
                LUT_3D_SIZE 2
                0 0 0
                1 0 0
                0 1 0
                1 1 0
                0 0 1
                1 0 1
                0 1 1
                1 1 1
                """));

        float[] out = new float[3];
        lut.sample(0.5f, 0.5f, 0.5f, out);
        assertThat(out).containsExactly(0.5f, 0.5f, 0.5f);

        lut.sample(2f, -1f, 0.25f, out);
        assertThat(out).containsExactly(1f, 0f, 0.25f);
    }

    @Test
    void rejectsIncompleteCubeLut() {
        assertThatThrownBy(() -> CubeLut.parse(new StringReader("""
                LUT_3D_SIZE 2
                0 0 0
                1 1 1
                """))).isInstanceOf(java.io.IOException.class);
    }

    @Test
    void loadsBundledFilmLutAndChangesColor() {
        BufferedImage image = solid(128, 128, 128);
        LookSpec look = new LookSpec(null, "luts/film-warm-v1.cube", 1.0, null, null, null,
                null, null, null, null, null, null, null, null);

        colorPipeline.apply(image, look);

        Color color = new Color(image.getRGB(0, 0));
        assertThat(color.getRed() == 128 && color.getGreen() == 128 && color.getBlue() == 128).isFalse();
    }

    @Test
    void appliesExposureAndSaturation() {
        BufferedImage gray = solid(64, 64, 64);
        colorPipeline.apply(gray, new LookSpec(null, null, null, 1.0, null, null,
                null, null, null, null, null, null, null, null));
        assertThat(new Color(gray.getRGB(0, 0)).getRed()).isEqualTo(128);

        BufferedImage colorful = solid(200, 40, 40);
        colorPipeline.apply(colorful, new LookSpec(null, null, null, null, null, 0.0,
                null, null, null, null, null, null, null, null));
        Color grayValue = new Color(colorful.getRGB(0, 0));
        assertThat(Math.abs(grayValue.getRed() - grayValue.getGreen())).isLessThanOrEqualTo(1);
        assertThat(Math.abs(grayValue.getGreen() - grayValue.getBlue())).isLessThanOrEqualTo(1);
    }

    @Test
    void portraitBrightensSkinTone() {
        BufferedImage image = solid(200, 150, 120);
        portraitProcessor.apply(image, new PortraitSpec(true, 0.0, 0.2, 0.0));

        Color center = new Color(image.getRGB(16, 16));
        assertThat(center.getRed()).isGreaterThan(200);
    }

    @Test
    void portraitLeavesNonSkinImageUntouched() {
        BufferedImage image = solid(20, 80, 210);
        portraitProcessor.apply(image, new PortraitSpec(true, 0.4, 0.2, 0.1));

        assertThat(image.getRGB(0, 0)).isEqualTo(new Color(20, 80, 210).getRGB());
    }

    @Test
    void globalEffectsDarkenCornersAndAreDeterministic() {
        BufferedImage image = white(32, 32);
        globalEffects.apply(image, new LookSpec(null, null, null, null, null, null,
                null, null, null, null, 0.5, 0.05, null, null), "task-1");

        int center = new Color(image.getRGB(16, 16)).getRed();
        int corner = new Color(image.getRGB(0, 0)).getRed();
        assertThat(corner).isLessThan(center);

        BufferedImage same = white(32, 32);
        globalEffects.apply(same, new LookSpec(null, null, null, null, null, null,
                null, null, null, null, 0.5, 0.05, null, null), "task-1");
        assertThat(same.getRGB(0, 0)).isEqualTo(image.getRGB(0, 0));

        BufferedImage other = white(32, 32);
        globalEffects.apply(other, new LookSpec(null, null, null, null, null, null,
                null, null, null, null, 0.5, 0.05, null, null), "task-2");
        assertThat(differentPixels(other, image)).isGreaterThan(0);
    }

    private int differentPixels(BufferedImage left, BufferedImage right) {
        int count = 0;
        for (int y = 0; y < left.getHeight(); y++) {
            for (int x = 0; x < left.getWidth(); x++) {
                if (left.getRGB(x, y) != right.getRGB(x, y)) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    void effectRendererProducesJpegAtCanvasSize() throws Exception {
        BufferedImage source = solid(200, 100, 50);
        ImageRenderSpec spec = new ImageRenderSpec(1, TemplateKind.IMAGE,
                new LookSpec(null, "luts/film-warm-v1.cube", 0.8, null, null, null,
                        null, null, null, null, 0.1, 0.03, null, null),
                new PortraitSpec(false, null, null, null),
                new LayoutSpec("legacy", 12), null, false);

        byte[] jpeg = effectRenderer.render(List.of(source), spec, 64, 48,
                List.of(new LayoutSlot(0, 0, 64, 48)), "task-abc12345");

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(decoded.getWidth()).isEqualTo(64);
        assertThat(decoded.getHeight()).isEqualTo(48);
    }

    private BufferedImage solid(int red, int green, int blue) {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(red, green, blue));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        return image;
    }

    private BufferedImage white(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }
}
