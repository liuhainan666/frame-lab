package com.framelab.task.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class ResultRendererTest {

    private final LayoutPlanner planner = new LayoutPlanner();
    private final ResultRenderer renderer = new ResultRenderer();

    @Test
    void rendersJpegAtCanvasSizeWithWatermarkPixels() throws Exception {
        int width = 320;
        int height = 240;
        List<BufferedImage> images = List.of(solid(100, 100, new Color(20, 50, 120)),
                solid(80, 120, new Color(8, 8, 8)));
        List<LayoutSlot> slots = planner.plan(2, width, height);

        byte[] jpeg = renderer.render(images, width, height, slots, "0f0c8f5e-7a2d-4b9c-9a61-2d8f3b6c1e44");

        assertThat(jpeg[0]).isEqualTo((byte) 0xFF);
        assertThat(jpeg[1]).isEqualTo((byte) 0xD8);
        BufferedImage rendered = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(rendered.getWidth()).isEqualTo(width);
        assertThat(rendered.getHeight()).isEqualTo(height);

        int bright = 0;
        for (int y = height - 45; y < height - 5; y++) {
            for (int x = width - 150; x < width - 5; x++) {
                Color pixel = new Color(rendered.getRGB(x, y));
                if (pixel.getRed() > 180 && pixel.getGreen() > 180 && pixel.getBlue() > 180) {
                    bright++;
                }
            }
        }
        assertThat(bright).isGreaterThan(0);
    }

    @Test
    void appliesSingleImageBottomShade() throws Exception {
        int width = 240;
        int height = 320;
        byte[] jpeg = renderer.render(List.of(solid(120, 80, new Color(220, 220, 220))),
                width, height, planner.plan(1, width, height), "abcdef12-0000-0000-0000-000000000000");

        BufferedImage rendered = ImageIO.read(new ByteArrayInputStream(jpeg));
        Color top = new Color(rendered.getRGB(20, 20));
        Color bottom = new Color(rendered.getRGB(20, height - 20));
        assertThat(top.getRed()).isGreaterThan(150);
        assertThat(bottom.getRed()).isLessThan(top.getRed());
    }

    private BufferedImage solid(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
