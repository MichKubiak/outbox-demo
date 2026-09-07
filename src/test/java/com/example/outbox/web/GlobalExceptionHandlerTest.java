package com.example.outbox.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.example.outbox.controller.OrderController;
import com.example.outbox.controller.dto.CreateOrderRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private static final Instant NOW = Instant.parse("2026-09-07T10:15:30Z");
    private static final String OBJECT_NAME = "createOrderRequest";

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private WebRequest nonServletRequest;

    private GlobalExceptionHandler handler;
    private ServletWebRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(clock);
        request = new ServletWebRequest(new MockHttpServletRequest("POST", "/orders"), new MockHttpServletResponse());
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void should_returnInternalError_when_exceptionIsUnhandled() {
        // given
        // when
        ResponseEntity<ApiError> response = handler.handleUnexpected(new IllegalStateException("boom"), request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody())
                .extracting(ApiError::status, ApiError::code, ApiError::message, ApiError::path)
                .containsExactly(500, ErrorCode.INTERNAL_ERROR, "Internal server error", "/orders");
    }

    @Test
    void should_hideExceptionDetails_when_exceptionIsUnhandled() {
        // given
        IllegalStateException failure = new IllegalStateException("jdbc connection reset by peer");

        // when
        ResponseEntity<ApiError> response = handler.handleUnexpected(failure, request);

        // then
        assertThat(response.getBody().message())
                .doesNotContain("jdbc", "connection reset", "IllegalStateException", "com.example.outbox");
    }

    @Test
    void should_useJsonContentType_when_exceptionIsUnhandled() {
        // given
        // when
        ResponseEntity<ApiError> response = handler.handleUnexpected(new IllegalStateException("boom"), request);

        // then
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void should_stampTimestampFromClock_when_errorIsBuilt() {
        // given
        // when
        ResponseEntity<ApiError> response = handler.handleUnexpected(new IllegalStateException("boom"), request);

        // then
        assertThat(response.getBody().timestamp()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    void should_includeCorrelationId_when_mdcIsPopulated() {
        // given
        MDC.put(CorrelationIdFilter.MDC_KEY, "abc-123");

        // when
        ResponseEntity<ApiError> response = handler.handleUnexpected(new IllegalStateException("boom"), request);

        // then
        assertThat(response.getBody().correlationId()).isEqualTo("abc-123");
    }

    @Test
    void should_omitCorrelationId_when_mdcIsEmpty() {
        // given
        // when
        ResponseEntity<ApiError> response = handler.handleUnexpected(new IllegalStateException("boom"), request);

        // then
        assertThat(response.getBody().correlationId()).isNull();
    }

    @Test
    void should_useRequestDescription_when_requestIsNotServletBased() {
        // given
        given(nonServletRequest.getDescription(false)).willReturn("uri=/orders");

        // when
        ResponseEntity<ApiError> response =
                handler.handleUnexpected(new IllegalStateException("boom"), nonServletRequest);

        // then
        assertThat(response.getBody().path()).isEqualTo("uri=/orders");
    }

    @Test
    void should_returnTooManyRequests_when_rateLimitExceeded() {
        // given
        // when
        ResponseEntity<ApiError> response =
                handler.handleRateLimitExceeded(new RateLimitExceededException(7L), request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody())
                .extracting(ApiError::status, ApiError::code, ApiError::message)
                .containsExactly(429, ErrorCode.RATE_LIMIT_EXCEEDED, "Too many requests");
    }

    @Test
    void should_setRetryAfterHeader_when_rateLimitExceeded() {
        // given
        // when
        ResponseEntity<ApiError> response =
                handler.handleRateLimitExceeded(new RateLimitExceededException(7L), request);

        // then
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("7");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void should_reportValidationFailure_when_bodyIsInvalid() throws Exception {
        // given
        MethodArgumentNotValidException failure = validationFailure(
                new FieldError(OBJECT_NAME, "customerId", "must not be blank"));

        // when
        ApiError error = internal(failure, HttpStatus.BAD_REQUEST);

        // then
        assertThat(error)
                .extracting(ApiError::status, ApiError::code, ApiError::message)
                .containsExactly(400, ErrorCode.VALIDATION_FAILED, "customerId: must not be blank");
    }

    @Test
    void should_listFieldErrorsSortedByField_when_multipleViolations() throws Exception {
        // given
        MethodArgumentNotValidException failure = validationFailure(
                new FieldError(OBJECT_NAME, "customerId", "must not be blank"),
                new FieldError(OBJECT_NAME, "amount", "must be positive"));

        // when
        ApiError error = internal(failure, HttpStatus.BAD_REQUEST);

        // then
        assertThat(error.message()).isEqualTo("amount: must be positive; customerId: must not be blank");
    }

    @Test
    void should_reportGenericValidationFailure_when_noFieldErrorsPresent() throws Exception {
        // given
        MethodArgumentNotValidException failure = validationFailure();

        // when
        ApiError error = internal(failure, HttpStatus.BAD_REQUEST);

        // then
        assertThat(error.message()).isEqualTo("Request validation failed");
    }

    @Test
    void should_reportMalformedRequest_when_bodyIsNotReadableJson() {
        // given
        HttpMessageNotReadableException failure = new HttpMessageNotReadableException(
                "Unexpected end of input", new MockHttpInputMessage("{".getBytes()));

        // when
        ApiError error = internal(failure, HttpStatus.BAD_REQUEST);

        // then
        assertThat(error)
                .extracting(ApiError::code, ApiError::message)
                .containsExactly(ErrorCode.MALFORMED_REQUEST, "Request body is not readable JSON");
    }

    @ParameterizedTest(name = "{index}: {0} -> {1}")
    @CsvSource({
            "404,NOT_FOUND,Not Found",
            "405,METHOD_NOT_ALLOWED,Method Not Allowed",
            "406,NOT_ACCEPTABLE,Not Acceptable",
            "415,UNSUPPORTED_MEDIA_TYPE,Unsupported Media Type"
    })
    void should_buildErrorFromStatus_when_frameworkRaisesFailure(int status, ErrorCode expectedCode,
                                                                 String expectedMessage) {
        // given
        // when
        ApiError error = created(null, null, HttpStatusCode.valueOf(status));

        // then
        assertThat(error)
                .extracting(ApiError::status, ApiError::code, ApiError::message, ApiError::path)
                .containsExactly(status, expectedCode, expectedMessage, "/orders");
    }

    @Test
    void should_fallBackToInternalMessage_when_statusIsNotStandard() {
        // given
        // when
        ApiError error = created(null, null, HttpStatusCode.valueOf(599));

        // then
        assertThat(error)
                .extracting(ApiError::status, ApiError::code, ApiError::message)
                .containsExactly(599, ErrorCode.INTERNAL_ERROR, "Internal server error");
    }

    @Test
    void should_keepExistingBody_when_bodyIsAlreadyApiError() {
        // given
        ApiError body = new ApiError(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), 400,
                ErrorCode.VALIDATION_FAILED, "customerId: must not be blank", "/orders", "abc-123");

        // when
        ApiError error = created(body, null, HttpStatus.BAD_REQUEST);

        // then
        assertThat(error).isSameAs(body);
    }

    @Test
    void should_preserveFrameworkHeaders_when_responseIsBuilt() {
        // given
        HttpHeaders headers = new HttpHeaders();
        headers.setAllow(Set.of(HttpMethod.POST));

        // when
        ResponseEntity<Object> response =
                handler.createResponseEntity(null, headers, HttpStatus.METHOD_NOT_ALLOWED, request);

        // then
        assertThat(response.getHeaders().getAllow()).containsExactly(HttpMethod.POST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    private ApiError internal(Exception failure, HttpStatusCode status) {
        ResponseEntity<Object> response =
                handler.handleExceptionInternal(failure, null, new HttpHeaders(), status, request);
        return (ApiError) response.getBody();
    }

    private ApiError created(Object body, HttpHeaders headers, HttpStatusCode status) {
        return (ApiError) handler.createResponseEntity(body, headers, status, request).getBody();
    }

    private static MethodArgumentNotValidException validationFailure(FieldError... fieldErrors) throws Exception {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new CreateOrderRequest("123"), OBJECT_NAME);
        for (FieldError fieldError : fieldErrors) {
            bindingResult.addError(fieldError);
        }
        MethodParameter parameter = new MethodParameter(
                OrderController.class.getMethod("createOrder", CreateOrderRequest.class), 0);
        return new MethodArgumentNotValidException(parameter, bindingResult);
    }
}
