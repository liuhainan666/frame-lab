package com.framelab.template.spec;

public record VideoAudioSpec(String path, Double volume, int fadeOutMs) {

    public VideoAudioSpec {
        volume = volume == null ? 1.0 : volume;
    }
}
