package com.framelab.template.spec;

import com.framelab.template.TemplateKind;

public record ImageRenderSpec(
        int version,
        TemplateKind kind,
        LookSpec look,
        PortraitSpec portrait,
        LayoutSpec layout,
        OverlaySpec overlay,
        boolean watermark) implements TemplateRenderSpec {
}
