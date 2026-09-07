package com.example.outbox.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public enum ErrorCode {

    VALIDATION_FAILED,
    MALFORMED_REQUEST,
    UNSUPPORTED_MEDIA_TYPE,
    NOT_ACCEPTABLE,
    NOT_FOUND,
    METHOD_NOT_ALLOWED,
    RATE_LIMIT_EXCEEDED,
    INTERNAL_ERROR;

    public static ErrorCode forStatus(HttpStatusCode status) {
        if (status.equals(HttpStatus.BAD_REQUEST)) {
            return VALIDATION_FAILED;
        }
        if (status.equals(HttpStatus.NOT_FOUND)) {
            return NOT_FOUND;
        }
        if (status.equals(HttpStatus.METHOD_NOT_ALLOWED)) {
            return METHOD_NOT_ALLOWED;
        }
        if (status.equals(HttpStatus.NOT_ACCEPTABLE)) {
            return NOT_ACCEPTABLE;
        }
        if (status.equals(HttpStatus.UNSUPPORTED_MEDIA_TYPE)) {
            return UNSUPPORTED_MEDIA_TYPE;
        }
        if (status.equals(HttpStatus.TOO_MANY_REQUESTS)) {
            return RATE_LIMIT_EXCEEDED;
        }
        return INTERNAL_ERROR;
    }
}
