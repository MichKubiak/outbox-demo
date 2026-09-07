package com.example.outbox.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.outbox.config.ClockConfig;
import com.example.outbox.entity.OrderStatus;
import com.example.outbox.service.CreatedOrder;
import com.example.outbox.service.OrderService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
@Import(ClockConfig.class)
class OrderControllerTest {

    private static final UUID ORDER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final OffsetDateTime CREATED_AT =
            OffsetDateTime.of(2026, 9, 7, 10, 15, 30, 0, ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void should_returnCreated_when_requestIsValid() throws Exception {
        // given
        given(orderService.createOrder("123")).willReturn(created("123"));

        // when
        // then
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("123")))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.customerId").value("123"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-07T10:15:30Z"));
    }

    @Test
    void should_acceptCustomerId_when_valueContainsUnicode() throws Exception {
        // given
        given(orderService.createOrder("客户-123")).willReturn(created("客户-123"));

        // when
        // then
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("客户-123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value("客户-123"));
    }

    @ParameterizedTest(name = "{index}: [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @MethodSource("oversizedCustomerIds")
    void should_returnBadRequest_when_customerIdIsInvalid(String customerId) throws Exception {
        // given
        // when
        // then
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(customerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/orders"));
        then(orderService).should(never()).createOrder(anyString());
    }

    @Test
    void should_returnBadRequest_when_bodyIsMalformedJson() throws Exception {
        // given
        // when
        // then
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        then(orderService).should(never()).createOrder(anyString());
    }

    @Test
    void should_returnUnsupportedMediaType_when_contentTypeIsNotJson() throws Exception {
        // given
        // when
        // then
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("customerId=123"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        then(orderService).should(never()).createOrder(anyString());
    }

    @Test
    void should_returnMethodNotAllowed_when_methodIsGet() throws Exception {
        // given
        // when
        // then
        mockMvc.perform(get("/orders"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        then(orderService).should(never()).createOrder(anyString());
    }

    @Test
    void should_returnNotAcceptable_when_acceptHeaderIsUnsupported() throws Exception {
        // given
        // when
        // then
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE)
                        .content(body("123")))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
        then(orderService).should(never()).createOrder(anyString());
    }

    @Test
    void should_returnApiErrorWithoutStackTrace_when_serviceThrows() throws Exception {
        // given
        given(orderService.createOrder("123")).willThrow(new IllegalStateException("connection reset"));

        // when
        String responseBody = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("123")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // then
        assertThat(responseBody)
                .doesNotContain("IllegalStateException", "connection reset", "com.example.outbox", "\tat ");
    }

    private static Stream<String> oversizedCustomerIds() {
        return Stream.of("x".repeat(65));
    }

    private static CreatedOrder created(String customerId) {
        return new CreatedOrder(ORDER_ID, customerId, OrderStatus.CREATED, CREATED_AT);
    }

    private static String body(String customerId) {
        if (customerId == null) {
            return "{\"customerId\":null}";
        }
        return "{\"customerId\":\"" + customerId + "\"}";
    }
}
