package com.framelab.task.render;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CubeLut {

    private final int size;
    private final float[] table;

    private CubeLut(int size, float[] table) {
        this.size = size;
        this.table = table;
    }

    public static CubeLut parse(Reader source) throws IOException {
        BufferedReader reader = new BufferedReader(source);
        int size = -1;
        List<float[]> values = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String upper = trimmed.toUpperCase(Locale.ROOT);
            if (upper.startsWith("LUT_3D_SIZE")) {
                size = parseSize(trimmed);
                continue;
            }
            if (upper.startsWith("TITLE") || upper.startsWith("DOMAIN_MIN")
                    || upper.startsWith("DOMAIN_MAX")) {
                continue;
            }
            if (upper.startsWith("LUT_1D_SIZE")) {
                throw new IOException("Only 3D LUTs are supported");
            }
            String[] parts = trimmed.split("\\s+");
            if (parts.length != 3) {
                throw new IOException("Invalid LUT row");
            }
            values.add(new float[]{
                    Float.parseFloat(parts[0]),
                    Float.parseFloat(parts[1]),
                    Float.parseFloat(parts[2])});
        }
        if (size < 2 || size > 64) {
            throw new IOException("Invalid LUT size");
        }
        if (values.size() != size * size * size) {
            throw new IOException("LUT row count does not match size");
        }
        float[] table = new float[size * size * size * 3];
        for (int index = 0; index < values.size(); index++) {
            float[] value = values.get(index);
            table[index * 3] = value[0];
            table[index * 3 + 1] = value[1];
            table[index * 3 + 2] = value[2];
        }
        return new CubeLut(size, table);
    }

    private static int parseSize(String line) throws IOException {
        String[] parts = line.split("\\s+");
        if (parts.length < 2) {
            throw new IOException("Invalid LUT size line");
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            throw new IOException("Invalid LUT size value", ex);
        }
    }

    public void sample(float red, float green, float blue, float[] out) {
        float r = clamp01(red) * (size - 1);
        float g = clamp01(green) * (size - 1);
        float b = clamp01(blue) * (size - 1);
        int r0 = (int) Math.floor(r);
        int g0 = (int) Math.floor(g);
        int b0 = (int) Math.floor(b);
        int r1 = Math.min(r0 + 1, size - 1);
        int g1 = Math.min(g0 + 1, size - 1);
        int b1 = Math.min(b0 + 1, size - 1);
        float fr = r - r0;
        float fg = g - g0;
        float fb = b - b0;

        for (int channel = 0; channel < 3; channel++) {
            float c000 = value(r0, g0, b0, channel);
            float c100 = value(r1, g0, b0, channel);
            float c010 = value(r0, g1, b0, channel);
            float c110 = value(r1, g1, b0, channel);
            float c001 = value(r0, g0, b1, channel);
            float c101 = value(r1, g0, b1, channel);
            float c011 = value(r0, g1, b1, channel);
            float c111 = value(r1, g1, b1, channel);

            float c00 = c000 + (c100 - c000) * fr;
            float c10 = c010 + (c110 - c010) * fr;
            float c01 = c001 + (c101 - c001) * fr;
            float c11 = c011 + (c111 - c011) * fr;
            float c0 = c00 + (c10 - c00) * fg;
            float c1 = c01 + (c11 - c01) * fg;
            out[channel] = c0 + (c1 - c0) * fb;
        }
    }

    private float value(int r, int g, int b, int channel) {
        int index = ((b * size + g) * size + r) * 3 + channel;
        return table[index];
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
