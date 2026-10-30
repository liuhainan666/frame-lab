package com.framelab.template;

import java.util.List;

public record TemplateLimits(String templateId, List<String> acceptedMimeTypes, long maxFileSizeBytes) {
}
