package com.framelab.task.dto;

public record TaskFailureView(String stage, String code, String message, boolean simulated) {
}
