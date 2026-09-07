package com.example.outbox.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CreateOrderRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void openValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void should_trimCustomerId_when_valueHasSurroundingWhitespace() {
        // given
        // when
        CreateOrderRequest request = new CreateOrderRequest("   123   ");

        // then
        assertThat(request.customerId()).isEqualTo("123");
    }

    @Test
    void should_keepNull_when_customerIdIsNull() {
        // given
        // when
        CreateOrderRequest request = new CreateOrderRequest(null);

        // then
        assertThat(request.customerId()).isNull();
    }

    @ParameterizedTest(name = "{index}: [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void should_reportViolation_when_customerIdIsBlank(String customerId) {
        // given
        CreateOrderRequest request = new CreateOrderRequest(customerId);

        // when
        var violations = validator.validate(request);

        // then
        assertThat(violations).isNotEmpty();
    }

    @Test
    void should_reportViolation_when_customerIdExceedsSixtyFourCharacters() {
        // given
        CreateOrderRequest request = new CreateOrderRequest("x".repeat(65));

        // when
        var violations = validator.validate(request);

        // then
        assertThat(violations).hasSize(1);
    }

    @Test
    void should_accept_when_paddedCustomerIdTrimsToSixtyFourCharacters() {
        // given
        CreateOrderRequest request = new CreateOrderRequest("  " + "x".repeat(64) + "  ");

        // when
        var violations = validator.validate(request);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    void should_accept_when_customerIdContainsUnicode() {
        // given
        CreateOrderRequest request = new CreateOrderRequest("客户-123");

        // when
        var violations = validator.validate(request);

        // then
        assertThat(violations).isEmpty();
    }
}
