package com.framelab.task.render;

import java.awt.image.BufferedImage;

import com.framelab.template.spec.PortraitSpec;
import org.springframework.stereotype.Component;

@Component
public class PortraitProcessor {

    private static final float HIGH_FREQUENCY_RETURN = 0.72f;

    public void apply(BufferedImage image, PortraitSpec spec) {
        if (spec == null || !spec.enabled()) {
            return;
        }
        if (spec.skinSmooth() <= 0 && spec.skinBrighten() <= 0 && spec.warmth() <= 0) {
            return;
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int pixelsCount = width * height;
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        float[] red = new float[pixelsCount];
        float[] green = new float[pixelsCount];
        float[] blue = new float[pixelsCount];
        float[] mask = new float[pixelsCount];
        int skinPixels = 0;
        for (int index = 0; index < pixelsCount; index++) {
            int pixel = pixels[index];
            float r = ((pixel >> 16) & 0xFF) / 255f;
            float g = ((pixel >> 8) & 0xFF) / 255f;
            float b = (pixel & 0xFF) / 255f;
            red[index] = r;
            green[index] = g;
            blue[index] = b;
            float y = 0.299f * r + 0.587f * g + 0.114f * b;
            float cb = 127.5f - 43.03f * r - 84.47f * g + 127.5f * b;
            float cr = 127.5f + 127.5f * r - 106.76f * g - 20.74f * b;
            if (y >= 0.15f && y <= 0.95f && cb >= 77 && cb <= 127 && cr >= 133 && cr <= 173) {
                mask[index] = 1f;
                skinPixels++;
            }
        }
        if (skinPixels < pixelsCount / 200) {
            return;
        }

        mask = morph(mask, width, height, true);
        mask = morph(mask, width, height, false);
        mask = morph(mask, width, height, false);
        mask = morph(mask, width, height, true);
        mask = ImageMath.boxBlur(mask, width, height, Math.max(4, Math.min(width, height) / 100));

        int smoothRadius = Math.max(3, Math.min(width, height) / 150);
        float[] lowRed = ImageMath.boxBlur(red, width, height, smoothRadius);
        float[] lowGreen = ImageMath.boxBlur(green, width, height, smoothRadius);
        float[] lowBlue = ImageMath.boxBlur(blue, width, height, smoothRadius);

        float smooth = spec.skinSmooth().floatValue();
        float brighten = spec.skinBrighten().floatValue();
        float warmth = spec.warmth().floatValue();
        for (int index = 0; index < pixelsCount; index++) {
            float coverage = mask[index];
            if (coverage <= 0.001f) {
                continue;
            }
            float mix = coverage * smooth;
            float highRed = red[index] - lowRed[index];
            float highGreen = green[index] - lowGreen[index];
            float highBlue = blue[index] - lowBlue[index];
            float smoothRed = lowRed[index] + highRed * HIGH_FREQUENCY_RETURN;
            float smoothGreen = lowGreen[index] + highGreen * HIGH_FREQUENCY_RETURN;
            float smoothBlue = lowBlue[index] + highBlue * HIGH_FREQUENCY_RETURN;

            float lift = coverage * brighten * 0.35f;
            red[index] = red[index] * (1 - mix) + smoothRed * mix + lift + coverage * warmth * 0.2f;
            green[index] = green[index] * (1 - mix) + smoothGreen * mix + lift;
            blue[index] = blue[index] * (1 - mix) + smoothBlue * mix + lift;
        }

        for (int index = 0; index < pixelsCount; index++) {
            int r = Math.round(ImageMath.clamp01(red[index]) * 255);
            int g = Math.round(ImageMath.clamp01(green[index]) * 255);
            int b = Math.round(ImageMath.clamp01(blue[index]) * 255);
            pixels[index] = (r << 16) | (g << 8) | b;
        }
        image.setRGB(0, 0, width, height, pixels, 0, width);
    }

    private float[] morph(float[] mask, int width, int height, boolean erode) {
        float[] result = new float[mask.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float value = erode ? 1f : 0f;
                for (int dy = -1; dy <= 1; dy++) {
                    int sampleY = ImageMath.clamp(y + dy, 0, height - 1);
                    for (int dx = -1; dx <= 1; dx++) {
                        int sampleX = ImageMath.clamp(x + dx, 0, width - 1);
                        float sample = mask[sampleY * width + sampleX];
                        value = erode ? Math.min(value, sample) : Math.max(value, sample);
                    }
                }
                result[y * width + x] = value;
            }
        }
        return result;
    }
}
