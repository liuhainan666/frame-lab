package com.framelab.template.spec;

import com.framelab.template.TemplateKind;

public sealed interface TemplateRenderSpec permits ImageRenderSpec, VideoRenderSpec {

    int version();

    TemplateKind kind();
}
