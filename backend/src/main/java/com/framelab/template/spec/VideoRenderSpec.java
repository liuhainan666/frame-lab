package com.framelab.template.spec;

import java.util.List;

import com.framelab.template.TemplateKind;

public record VideoRenderSpec(
        int version,
        TemplateKind kind,
        int durationMs,
        int fps,
        ResolutionSpec resolution,
        List<VideoSlotSpec> slots,
        LookSpec look,
        VideoAudioSpec audio,
        List<VideoTextSpec> text,
        boolean watermark) implements TemplateRenderSpec {

    public VideoRenderSpec {
        slots = slots == null ? List.of() : List.copyOf(slots);
        text = text == null ? List.of() : List.copyOf(text);
    }

    public int transitionCount() {
        return Math.max(0, slots.size() - 1);
    }

    public int contentDurationMs() {
        int total = 0;
        for (VideoSlotSpec slot : slots) {
            total += slot.durationMs();
        }
        for (int index = 1; index < slots.size(); index++) {
            total -= slots.get(index).transitionMs();
        }
        return total;
    }
}
