package com.framelab.template.spec;

public record LookSpec(
        String preset,
        String lut,
        Double lutStrength,
        Double exposure,
        Double contrast,
        Double saturation,
        Double temperature,
        Double tint,
        Double highlights,
        Double shadows,
        Double vignette,
        Double grain,
        Double bloom,
        Double clarity) {

    public LookSpec {
        lutStrength = lutStrength == null ? 1.0 : lutStrength;
        exposure = exposure == null ? 0.0 : exposure;
        contrast = contrast == null ? 1.0 : contrast;
        saturation = saturation == null ? 1.0 : saturation;
        temperature = temperature == null ? 0.0 : temperature;
        tint = tint == null ? 0.0 : tint;
        highlights = highlights == null ? 0.0 : highlights;
        shadows = shadows == null ? 0.0 : shadows;
        vignette = vignette == null ? 0.0 : vignette;
        grain = grain == null ? 0.0 : grain;
        bloom = bloom == null ? 0.0 : bloom;
        clarity = clarity == null ? 0.0 : clarity;
    }

    public boolean hasLut() {
        return lut != null && !lut.isBlank() && lutStrength > 0;
    }
}
