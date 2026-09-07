package com.example.outbox.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

class ErrorCodeTest {

    @ParameterizedTest(name = "{index}: {0} -> {1}")
    @CsvSource({
            "400,VALIDATION_FAILED",
            "404,NOT_FOUND",
            "405,METHOD_NOT_ALLOWED",
            "406,NOT_ACCEPTABLE",
            "415,UNSUPPORTED_MEDIA_TYPE",
            "429,RATE_LIMIT_EXCEEDED",
            "500,INTERNAL_ERROR"
    })
    void should_mapStatusToCode_when_statusIsHandled(int status, ErrorCode expected) {
        // given
        // when
        ErrorCode code = ErrorCode.forStatus(HttpStatusCode.valueOf(status));

        // then
        assertThat(code).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{index}: status {0}")
    @ValueSource(ints = {401, 403, 409, 418, 503, 599})
    void should_fallBackToInternalError_when_statusIsNotMapped(int status) {
        // given
        // when
        ErrorCode code = ErrorCode.forStatus(HttpStatusCode.valueOf(status));

        // then
        assertThat(code).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void should_matchEnumConstant_when_statusIsGivenAsHttpStatus() {
        // given
        // when
        ErrorCode code = ErrorCode.forStatus(HttpStatus.TOO_MANY_REQUESTS);

        // then
        assertThat(code).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(ErrorCode.class)
    void should_expose_when_codeIsUsedAsStableContract(ErrorCode code) {
        // given
        // when
        // then
        assertThat(ErrorCode.valueOf(code.name())).isSameAs(code);
    }

    @Test
    void should_declareEightCodes_when_enumIsInspected() {
        // given
        // when
        // then
        assertThat(ErrorCode.values()).containsExactly(
                ErrorCode.VALIDATION_FAILED,
                ErrorCode.MALFORMED_REQUEST,
                ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                ErrorCode.NOT_ACCEPTABLE,
                ErrorCode.NOT_FOUND,
                ErrorCode.METHOD_NOT_ALLOWED,
                ErrorCode.RATE_LIMIT_EXCEEDED,
                ErrorCode.INTERNAL_ERROR);
    }
}
