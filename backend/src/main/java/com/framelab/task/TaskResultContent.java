package com.framelab.task;

import org.springframework.core.io.Resource;

public record TaskResultContent(TaskRow task, Resource resource) {
}
