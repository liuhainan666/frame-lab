package com.framelab.task.dto;

import java.util.List;

public record TaskSubmitRequest(String templateId, List<String> assetIds, String clientRequestId) {
}
