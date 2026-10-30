package com.framelab.common.error;

import java.util.List;

public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Object[] args;
    private final List<FieldErrorView> fieldErrors;

    protected ApiException(ErrorCode code, Object[] args, List<FieldErrorView> fieldErrors) {
        super(code.name());
        this.code = code;
        this.args = args == null ? new Object[0] : args;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public static ApiException of(ErrorCode code, Object... args) {
        return new ApiException(code, args, List.of());
    }

    public static ApiException validation(List<FieldErrorView> fieldErrors, Object... args) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, args, fieldErrors);
    }

    public ErrorCode code() {
        return code;
    }

    public Object[] args() {
        return args.clone();
    }

    public List<FieldErrorView> fieldErrors() {
        return fieldErrors;
    }
}
