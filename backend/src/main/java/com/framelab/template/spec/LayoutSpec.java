package com.framelab.template.spec;

public record LayoutSpec(String engine, Integer gap) {

    public LayoutSpec {
        engine = engine == null || engine.isBlank() ? "legacy" : engine;
        gap = gap == null ? 12 : gap;
    }
}
