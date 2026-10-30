package com.framelab.task.render;

final class ImageMath {

    private ImageMath() {
    }

    static float luma(float red, float green, float blue) {
        return 0.2126f * red + 0.7152f * green + 0.0722f * blue;
    }

    static float clamp01(float value) {
        return value < 0f ? 0f : Math.min(value, 1f);
    }

    static float smoothstep(float edge0, float edge1, float value) {
        float x = clamp01((value - edge0) / (edge1 - edge0));
        return x * x * (3 - 2 * x);
    }

    static float[] boxBlur(float[] source, int width, int height, int radius) {
        if (radius <= 0) {
            return source.clone();
        }
        int window = radius * 2 + 1;
        float[] horizontal = new float[source.length];
        float[] result = new float[source.length];

        for (int y = 0; y < height; y++) {
            int row = y * width;
            float sum = 0f;
            for (int offset = -radius; offset <= radius; offset++) {
                sum += source[row + clamp(offset, 0, width - 1)];
            }
            for (int x = 0; x < width; x++) {
                horizontal[row + x] = sum / window;
                int remove = row + clamp(x - radius, 0, width - 1);
                int add = row + clamp(x + radius + 1, 0, width - 1);
                sum += source[add] - source[remove];
            }
        }

        for (int x = 0; x < width; x++) {
            float sum = 0f;
            for (int offset = -radius; offset <= radius; offset++) {
                sum += horizontal[clamp(offset, 0, height - 1) * width + x];
            }
            for (int y = 0; y < height; y++) {
                result[y * width + x] = sum / window;
                int remove = clamp(y - radius, 0, height - 1) * width + x;
                int add = clamp(y + radius + 1, 0, height - 1) * width + x;
                sum += horizontal[add] - horizontal[remove];
            }
        }
        return result;
    }

    static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }
}
