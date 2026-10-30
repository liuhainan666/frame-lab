package com.framelab.template.spec;

public record PortraitSpec(boolean enabled, Double skinSmooth, Double skinBrighten, Double warmth) {

    public PortraitSpec {
        skinSmooth = skinSmooth == null ? 0.0 : skinSmooth;
        skinBrighten = skinBrighten == null ? 0.0 : skinBrighten;
        warmth = warmth == null ? 0.0 : warmth;
    }
}
