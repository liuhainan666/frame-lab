package com.framelab.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "validation-failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "malformed-request"),

    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "auth-required"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "token-expired"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "token-invalid"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "invalid-credentials"),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "refresh-token-invalid"),

    FORBIDDEN(HttpStatus.FORBIDDEN, "forbidden"),

    TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "template-not-found"),
    ASSET_NOT_FOUND(HttpStatus.NOT_FOUND, "asset-not-found"),
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "task-not-found"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "not-found"),

    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed"),

    ASSET_IN_USE(HttpStatus.CONFLICT, "asset-in-use"),
    TASK_NOT_READY(HttpStatus.CONFLICT, "task-not-ready"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "idempotency-key-reused"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "idempotency-conflict"),

    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "file-too-large"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type"),
    INVALID_IMAGE(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-image"),

    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "rate-limited"),

    TEMPLATE_SPEC_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "template-spec-invalid"),
    VIDEO_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "video-unavailable"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "service-unavailable");

    private final HttpStatus status;
    private final String typeSlug;

    ErrorCode(HttpStatus status, String typeSlug) {
        this.status = status;
        this.typeSlug = typeSlug;
    }

    public HttpStatus status() {
        return status;
    }

    public String typeSlug() {
        return typeSlug;
    }

    public String titleKey() {
        return "error." + name() + ".title";
    }

    public String detailKey() {
        return "error." + name() + ".detail";
    }
}
