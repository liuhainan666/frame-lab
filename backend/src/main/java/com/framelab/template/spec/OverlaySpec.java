package com.framelab.template.spec;

public record OverlaySpec(String path, Double opacity) {

    public OverlaySpec {
        opacity = opacity == null ? 1.0 : opacity;
    }
}
