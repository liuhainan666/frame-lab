package com.framelab.common.error;

import java.util.ArrayList;
import java.util.List;

import com.framelab.common.i18n.MessageResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemFactory problemFactory;
    private final MessageResolver messages;

    public GlobalExceptionHandler(ProblemFactory problemFactory, MessageResolver messages) {
        this.problemFactory = problemFactory;
        this.messages = messages;
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ProblemDetail> handleRateLimit(RateLimitException ex, HttpServletRequest request) {
        ProblemDetail problem = problemFactory.create(ex.code(), ex.args(), List.of(), request);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.retryAfterSeconds()))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException ex, HttpServletRequest request) {
        ProblemDetail problem = problemFactory.create(ex.code(), ex.args(), ex.fieldErrors(), request);
        return response(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        return response(problemFactory.create(ErrorCode.VALIDATION_FAILED, new Object[0],
                fieldErrors(ex.getBindingResult()), request));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleHandlerValidation(HandlerMethodValidationException ex,
                                                                 HttpServletRequest request) {
        return response(problemFactory.create(ErrorCode.VALIDATION_FAILED, new Object[0], List.of(), request));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex,
                                                                   HttpServletRequest request) {
        List<FieldErrorView> errors = ex.getConstraintViolations().stream()
                .map(violation -> new FieldErrorView(lastNode(violation.getPropertyPath().toString()),
                        messages.get("validation.invalid")))
                .toList();
        return response(problemFactory.create(ErrorCode.VALIDATION_FAILED, new Object[0], errors, request));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ProblemDetail> handleMissingPart(MissingServletRequestPartException ex,
                                                           HttpServletRequest request) {
        List<FieldErrorView> errors = List.of(new FieldErrorView(ex.getRequestPartName(),
                requiredMessage(ex.getRequestPartName())));
        return response(problemFactory.create(ErrorCode.VALIDATION_FAILED, new Object[0], errors, request));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                HttpServletRequest request) {
        List<FieldErrorView> errors = List.of(new FieldErrorView(ex.getParameterName(),
                requiredMessage(ex.getParameterName())));
        return response(problemFactory.create(ErrorCode.VALIDATION_FAILED, new Object[0], errors, request));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        List<FieldErrorView> errors = List.of(new FieldErrorView(ex.getName(),
                messages.get("validation.invalid")));
        return response(problemFactory.create(ErrorCode.VALIDATION_FAILED, new Object[0], errors, request));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleTooLarge(MaxUploadSizeExceededException ex,
                                                        HttpServletRequest request) {
        return response(problemFactory.create(ErrorCode.FILE_TOO_LARGE, new Object[0], List.of(), request));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ProblemDetail> handleMultipart(MultipartException ex, HttpServletRequest request) {
        return response(problemFactory.create(ErrorCode.MALFORMED_REQUEST, new Object[0], List.of(), request));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex,
                                                          HttpServletRequest request) {
        return response(problemFactory.create(ErrorCode.MALFORMED_REQUEST, new Object[0], List.of(), request));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMediaType(HttpMediaTypeNotSupportedException ex,
                                                         HttpServletRequest request) {
        return response(problemFactory.create(ErrorCode.UNSUPPORTED_MEDIA_TYPE, new Object[0], List.of(), request));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethod(HttpRequestMethodNotSupportedException ex,
                                                      HttpServletRequest request) {
        return response(problemFactory.create(ErrorCode.METHOD_NOT_ALLOWED, new Object[0], List.of(), request));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ProblemDetail> handleNotFound(Exception ex, HttpServletRequest request) {
        return response(problemFactory.create(ErrorCode.NOT_FOUND, new Object[0], List.of(), request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return response(problemFactory.create(ErrorCode.FORBIDDEN, new Object[0], List.of(), request));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> handleDataAccess(DataAccessException ex, HttpServletRequest request) {
        log.error("database.unavailable traceId={}", problemFactory.traceId(request), ex);
        return response(problemFactory.create(ErrorCode.SERVICE_UNAVAILABLE, new Object[0], List.of(), request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("request.failed traceId={}", problemFactory.traceId(request), ex);
        return response(problemFactory.create(ErrorCode.INTERNAL_ERROR, new Object[0], List.of(), request));
    }

    private ResponseEntity<ProblemDetail> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private List<FieldErrorView> fieldErrors(BindingResult bindingResult) {
        List<FieldErrorView> errors = new ArrayList<>();
        for (FieldError fieldError : bindingResult.getFieldErrors()) {
            errors.add(new FieldErrorView(fieldError.getField(), fieldMessage(fieldError)));
        }
        for (ObjectError objectError : bindingResult.getGlobalErrors()) {
            errors.add(new FieldErrorView(objectError.getObjectName(), messages.get("validation.invalid")));
        }
        return errors;
    }

    private String fieldMessage(FieldError fieldError) {
        String key = "validation." + fieldError.getField() + "." + rule(fieldError.getCode());
        return messages.exists(key) ? messages.get(key) : messages.get("validation.invalid");
    }

    private String rule(String constraintCode) {
        if (constraintCode == null) {
            return "invalid";
        }
        return switch (constraintCode) {
            case "NotBlank", "NotNull", "NotEmpty" -> "required";
            case "Email" -> "email";
            case "Size", "Length" -> "size";
            case "Pattern" -> "pattern";
            case "Min", "Max", "Range", "DecimalMin", "DecimalMax" -> "range";
            case "Positive", "PositiveOrZero" -> "positive";
            default -> "invalid";
        };
    }

    private String requiredMessage(String field) {
        String key = "validation." + field + ".required";
        return messages.exists(key) ? messages.get(key) : messages.get("validation.invalid");
    }

    private String lastNode(String propertyPath) {
        int index = propertyPath.lastIndexOf('.');
        return index >= 0 ? propertyPath.substring(index + 1) : propertyPath;
    }
}
