package com.example.outbox.web;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String INTERNAL_MESSAGE = "Internal server error";
    private static final String MALFORMED_MESSAGE = "Request body is not readable JSON";
    private static final String RATE_LIMIT_MESSAGE = "Too many requests";

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ApiError error = build(statusCode, codeFor(ex, statusCode), messageFor(ex, statusCode), request);
        return super.handleExceptionInternal(ex, error, headers, statusCode, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode statusCode,
                                                          WebRequest request) {
        HttpHeaders responseHeaders = new HttpHeaders();
        if (headers != null) {
            responseHeaders.putAll(headers);
        }
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        Object payload = body instanceof ApiError
                ? body
                : build(statusCode, ErrorCode.forStatus(statusCode), reasonFor(statusCode), request);
        return new ResponseEntity<>(payload, responseHeaders, statusCode);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> handleRateLimitExceeded(RateLimitExceededException ex, WebRequest request) {
        log.warn("Rate limit exceeded for {}", path(request));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(ex.getSecondsToWait()));
        ApiError error = build(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMIT_EXCEEDED, RATE_LIMIT_MESSAGE, request);
        return new ResponseEntity<>(error, headers, HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled failure for {}", path(request), ex);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ApiError error = build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, INTERNAL_MESSAGE, request);
        return new ResponseEntity<>(error, headers, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ApiError build(HttpStatusCode status, ErrorCode code, String message, WebRequest request) {
        return new ApiError(OffsetDateTime.now(clock), status.value(), code, message, path(request),
                MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    private static ErrorCode codeFor(Exception ex, HttpStatusCode status) {
        if (ex instanceof MethodArgumentNotValidException) {
            return ErrorCode.VALIDATION_FAILED;
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return ErrorCode.MALFORMED_REQUEST;
        }
        return ErrorCode.forStatus(status);
    }

    private static String messageFor(Exception ex, HttpStatusCode status) {
        if (ex instanceof MethodArgumentNotValidException validationFailure) {
            return describe(validationFailure);
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return MALFORMED_MESSAGE;
        }
        return reasonFor(status);
    }

    private static String describe(MethodArgumentNotValidException ex) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();
        if (fieldErrors.isEmpty()) {
            return "Request validation failed";
        }
        return fieldErrors.stream()
                .sorted((left, right) -> left.getField().compareTo(right.getField()))
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
    }

    private static String reasonFor(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved == null ? INTERNAL_MESSAGE : resolved.getReasonPhrase();
    }

    private static String path(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return servletWebRequest.getRequest().getRequestURI();
        }
        return request.getDescription(false);
    }
}
