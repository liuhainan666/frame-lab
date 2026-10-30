package com.framelab.template.spec;

public record VideoTextSpec(
        String textKey,
        int startMs,
        int endMs,
        double x,
        double y,
        int fontSize,
        String color,
        String align) {
}
