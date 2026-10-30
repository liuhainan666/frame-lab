package com.framelab.task.render;

import java.awt.image.BufferedImage;

import com.framelab.template.spec.LookSpec;
import org.springframework.stereotype.Component;

@Component
public class ColorPipeline {

    private final LutRegistry lutRegistry;

    public ColorPipeline(LutRegistry lutRegistry) {
        this.lutRegistry = lutRegistry;
    }

    public void apply(BufferedImage image, LookSpec look) {
        if (look == null) {
            return;
        }
        boolean identity = !look.hasLut()
                && look.exposure() == 0
                && look.contrast() == 1
                && look.saturation() == 1
                && look.temperature() == 0
                && look.tint() == 0
                && look.highlights() == 0
                && look.shadows() == 0
                && look.clarity() == 0;
        if (identity) {
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

        applyWhiteBalance(red, green, blue, look);
        applyExposure(red, green, blue, look.exposure());
        applyContrast(red, green, blue, look.contrast());
        applySaturation(red, green, blue, look.saturation());
        applyHighlightsShadows(red, green, blue, look);
        applyLut(red, green, blue, look);
        applyClarity(red, green, blue, width, height, look.clarity());

        for (int index = 0; index < pixelsCount; index++) {
            int r = Math.round(ImageMath.clamp01(red[index]) * 255);
            int g = Math.round(ImageMath.clamp01(green[index]) * 255);
            int b = Math.round(ImageMath.clamp01(blue[index]) * 255);
            pixels[index] = (r << 16) | (g << 8) | b;
        }
        image.setRGB(0, 0, width, height, pixels, 0, width);
    }

    private void applyWhiteBalance(float[] red, float[] green, float[] blue, LookSpec look) {
        if (look.temperature() == 0 && look.tint() == 0) {
            return;
        }
        float redGain = 1f + look.temperature().floatValue() / 100f * 0.18f;
        float blueGain = 1f - look.temperature().floatValue() / 100f * 0.18f;
        float greenGain = 1f - look.tint().floatValue() / 100f * 0.08f;
        for (int index = 0; index < red.length; index++) {
            float oldLuma = ImageMath.luma(red[index], green[index], blue[index]);
            float nextRed = red[index] * redGain;
            float nextGreen = green[index] * greenGain;
            float nextBlue = blue[index] * blueGain;
            float nextLuma = ImageMath.luma(nextRed, nextGreen, nextBlue);
            if (nextLuma > 0.0001f) {
                float scale = oldLuma / nextLuma;
                nextRed *= scale;
                nextGreen *= scale;
                nextBlue *= scale;
            }
            red[index] = nextRed;
            green[index] = nextGreen;
            blue[index] = nextBlue;
        }
    }

    private void applyExposure(float[] red, float[] green, float[] blue, double exposure) {
        if (exposure == 0) {
            return;
        }
        float factor = (float) Math.pow(2, exposure);
        for (int index = 0; index < red.length; index++) {
            red[index] *= factor;
            green[index] *= factor;
            blue[index] *= factor;
        }
    }

    private void applyContrast(float[] red, float[] green, float[] blue, double contrast) {
        if (contrast == 1) {
            return;
        }
        float value = (float) contrast;
        for (int index = 0; index < red.length; index++) {
            red[index] = (red[index] - 0.5f) * value + 0.5f;
            green[index] = (green[index] - 0.5f) * value + 0.5f;
            blue[index] = (blue[index] - 0.5f) * value + 0.5f;
        }
    }

    private void applySaturation(float[] red, float[] green, float[] blue, double saturation) {
        if (saturation == 1) {
            return;
        }
        float value = (float) saturation;
        for (int index = 0; index < red.length; index++) {
            float luma = ImageMath.luma(red[index], green[index], blue[index]);
            red[index] = luma + (red[index] - luma) * value;
            green[index] = luma + (green[index] - luma) * value;
            blue[index] = luma + (blue[index] - luma) * value;
        }
    }

    private void applyHighlightsShadows(float[] red, float[] green, float[] blue, LookSpec look) {
        if (look.highlights() == 0 && look.shadows() == 0) {
            return;
        }
        float highlights = look.highlights().floatValue() / 100f * 0.22f;
        float shadows = look.shadows().floatValue() / 100f * 0.22f;
        for (int index = 0; index < red.length; index++) {
            float luma = ImageMath.luma(red[index], green[index], blue[index]);
            float highMask = ImageMath.smoothstep(0.5f, 1f, luma);
            float lowMask = 1f - ImageMath.smoothstep(0f, 0.5f, luma);
            float delta = highlights * highMask + shadows * lowMask;
            red[index] += delta;
            green[index] += delta;
            blue[index] += delta;
        }
    }

    private void applyLut(float[] red, float[] green, float[] blue, LookSpec look) {
        if (!look.hasLut()) {
            return;
        }
        CubeLut lut = lutRegistry.load(look.lut());
        if (lut == null) {
            return;
        }
        float strength = (float) Math.min(1, Math.max(0, look.lutStrength()));
        float[] mapped = new float[3];
        for (int index = 0; index < red.length; index++) {
            lut.sample(red[index], green[index], blue[index], mapped);
            red[index] += (mapped[0] - red[index]) * strength;
            green[index] += (mapped[1] - green[index]) * strength;
            blue[index] += (mapped[2] - blue[index]) * strength;
        }
    }

    private void applyClarity(float[] red, float[] green, float[] blue,
                              int width, int height, double clarity) {
        if (clarity <= 0) {
            return;
        }
        int pixelsCount = width * height;
        float[] luma = new float[pixelsCount];
        for (int index = 0; index < pixelsCount; index++) {
            luma[index] = ImageMath.luma(red[index], green[index], blue[index]);
        }
        int radius = Math.max(2, Math.min(width, height) / 100);
        float[] blurred = ImageMath.boxBlur(luma, width, height, radius);
        float strength = (float) clarity * 1.4f;
        for (int index = 0; index < pixelsCount; index++) {
            float detail = (luma[index] - blurred[index]) * strength;
            red[index] += detail;
            green[index] += detail;
            blue[index] += detail;
        }
    }
}
