package com.framelab.task.render;

import java.awt.image.BufferedImage;
import java.util.Random;

import com.framelab.template.spec.LookSpec;
import org.springframework.stereotype.Component;

@Component
public class GlobalEffects {

    public void apply(BufferedImage image, LookSpec look, String seed) {
        if (look == null || (look.vignette() <= 0 && look.grain() <= 0 && look.bloom() <= 0)) {
            return;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int pixelsCount = width * height;
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        float[] red = new float[pixelsCount];
        float[] green = new float[pixelsCount];
        float[] blue = new float[pixelsCount];
        for (int index = 0; index < pixelsCount; index++) {
            int pixel = pixels[index];
            red[index] = ((pixel >> 16) & 0xFF) / 255f;
            green[index] = ((pixel >> 8) & 0xFF) / 255f;
            blue[index] = (pixel & 0xFF) / 255f;
        }

        if (look.bloom() > 0) {
            applyBloom(red, green, blue, width, height, look.bloom().floatValue());
        }
        if (look.vignette() > 0) {
            applyVignette(red, green, blue, width, height, look.vignette().floatValue());
        }
        if (look.grain() > 0) {
            applyGrain(red, green, blue, look.grain().floatValue(), seed);
        }

        for (int index = 0; index < pixelsCount; index++) {
            int r = Math.round(ImageMath.clamp01(red[index]) * 255);
            int g = Math.round(ImageMath.clamp01(green[index]) * 255);
            int b = Math.round(ImageMath.clamp01(blue[index]) * 255);
            pixels[index] = (r << 16) | (g << 8) | b;
        }
        image.setRGB(0, 0, width, height, pixels, 0, width);
    }

    private void applyBloom(float[] red, float[] green, float[] blue,
                            int width, int height, float strength) {
        int pixelsCount = width * height;
        float[] brightRed = new float[pixelsCount];
        float[] brightGreen = new float[pixelsCount];
        float[] brightBlue = new float[pixelsCount];
        for (int index = 0; index < pixelsCount; index++) {
            float luma = ImageMath.luma(red[index], green[index], blue[index]);
            float weight = Math.max(0f, (luma - 0.72f) / 0.28f);
            brightRed[index] = red[index] * weight;
            brightGreen[index] = green[index] * weight;
            brightBlue[index] = blue[index] * weight;
        }
        int radius = Math.max(3, Math.min(width, height) / 60);
        float[] blurredRed = ImageMath.boxBlur(brightRed, width, height, radius);
        float[] blurredGreen = ImageMath.boxBlur(brightGreen, width, height, radius);
        float[] blurredBlue = ImageMath.boxBlur(brightBlue, width, height, radius);
        float amount = strength * 0.35f;
        for (int index = 0; index < pixelsCount; index++) {
            red[index] = 1 - (1 - red[index]) * (1 - blurredRed[index] * amount);
            green[index] = 1 - (1 - green[index]) * (1 - blurredGreen[index] * amount);
            blue[index] = 1 - (1 - blue[index]) * (1 - blurredBlue[index] * amount);
        }
    }

    private void applyVignette(float[] red, float[] green, float[] blue,
                               int width, int height, float strength) {
        float centerX = width / 2f;
        float centerY = height / 2f;
        float normalization = (float) Math.sqrt(centerX * centerX + centerY * centerY);
        for (int y = 0; y < height; y++) {
            float ny = (y - centerY) / normalization;
            for (int x = 0; x < width; x++) {
                float nx = (x - centerX) / normalization;
                float distance = (float) Math.sqrt(nx * nx + ny * ny);
                float factor = 1f - strength * ImageMath.smoothstep(0.55f, 1.05f, distance);
                int index = y * width + x;
                red[index] *= factor;
                green[index] *= factor;
                blue[index] *= factor;
            }
        }
    }

    private void applyGrain(float[] red, float[] green, float[] blue, float strength, String seed) {
        Random random = new Random(seed == null ? 0 : seed.hashCode());
        float amount = strength * 0.12f;
        for (int index = 0; index < red.length; index++) {
            float noise = (random.nextFloat() * 2 - 1) * amount;
            red[index] += noise;
            green[index] += noise;
            blue[index] += noise;
        }
    }
}
