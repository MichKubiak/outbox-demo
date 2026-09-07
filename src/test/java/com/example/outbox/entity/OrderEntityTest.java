package com.example.outbox.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class OrderEntityTest {

    private static final OffsetDateTime CREATED_AT =
            OffsetDateTime.of(2026, 9, 7, 10, 15, 30, 0, ZoneOffset.UTC);

    @Test
    void should_copyCustomerIdAndTimestamp_when_created() {
        // given
        // when
        OrderEntity order = OrderEntity.create("123", CREATED_AT);

        // then
        assertThat(order)
                .extracting(OrderEntity::getCustomerId, OrderEntity::getCreatedAt)
                .containsExactly("123", CREATED_AT);
    }

    @Test
    void should_useCreatedStatus_when_created() {
        // given
        // when
        OrderEntity order = OrderEntity.create("123", CREATED_AT);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void should_leaveIdUnset_when_notPersisted() {
        // given
        // when
        OrderEntity order = OrderEntity.create("123", CREATED_AT);

        // then
        assertThat(order.getId()).isNull();
    }

    @ParameterizedTest(name = "{index}: missing {0}")
    @CsvSource({"customerId,true", "createdAt,false"})
    void should_throwNullPointer_when_requiredArgumentIsNull(String field, boolean customerIdIsNull) {
        // given
        String customerId = customerIdIsNull ? null : "123";
        OffsetDateTime createdAt = customerIdIsNull ? CREATED_AT : null;

        // when
        // then
        assertThatThrownBy(() -> OrderEntity.create(customerId, createdAt))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(field);
    }

    @ParameterizedTest(name = "{index}: [{0}]")
    @ValueSource(strings = {"1", "客户-123", "x", "  padded  ", "0"})
    void should_keepCustomerIdVerbatim_when_valueIsAccepted(String customerId) {
        // given
        // when
        OrderEntity order = OrderEntity.create(customerId, CREATED_AT);

        // then
        assertThat(order.getCustomerId()).isEqualTo(customerId);
    }

    @Test
    void should_preserveOffset_when_timestampIsNotUtc() {
        // given
        OffsetDateTime createdAt = CREATED_AT.withOffsetSameInstant(ZoneOffset.ofHours(2));

        // when
        OrderEntity order = OrderEntity.create("123", createdAt);

        // then
        assertThat(order.getCreatedAt()).isEqualTo(createdAt);
        assertThat(order.getCreatedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(2));
    }
}
