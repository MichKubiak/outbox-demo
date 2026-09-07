package com.example.outbox.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OutboxPropertiesTest {

    @Test
    void should_exposeConfiguredValues_when_allValuesAreValid() {
        // given
        // when
        OutboxProperties properties = new OutboxProperties(true, 5000L, 100, 60L);

        // then
        assertThat(properties)
                .extracting(OutboxProperties::enabled, OutboxProperties::fixedDelayMs, OutboxProperties::batchSize,
                        OutboxProperties::backlogWarnAgeSeconds)
                .containsExactly(true, 5000L, 100, 60L);
    }

    @Test
    void should_allowDisabledPublisher_when_enabledIsFalse() {
        // given
        // when
        OutboxProperties properties = new OutboxProperties(false, 5000L, 100, 60L);

        // then
        assertThat(properties.enabled()).isFalse();
    }

    @ParameterizedTest(name = "{index}: delay={0} batch={1} warnAge={2}")
    @CsvSource({"1,1,0", "1,2147483647,0", "9223372036854775807,100,9223372036854775807"})
    void should_accept_when_valuesAreAtBoundary(long fixedDelayMs, int batchSize, long backlogWarnAgeSeconds) {
        // given
        // when
        // then
        assertThatCode(() -> new OutboxProperties(true, fixedDelayMs, batchSize, backlogWarnAgeSeconds))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{index}: delay={0} batch={1} warnAge={2} -> {3}")
    @CsvSource({
            "0,100,60,fixed-delay-ms",
            "-1,100,60,fixed-delay-ms",
            "-9223372036854775808,100,60,fixed-delay-ms",
            "5000,0,60,batch-size",
            "5000,-1,60,batch-size",
            "5000,-2147483648,60,batch-size",
            "5000,100,-1,backlog-warn-age-seconds"
    })
    void should_rejectConfiguration_when_valueIsOutOfRange(long fixedDelayMs, int batchSize,
                                                           long backlogWarnAgeSeconds, String property) {
        // given
        // when
        // then
        assertThatThrownBy(() -> new OutboxProperties(true, fixedDelayMs, batchSize, backlogWarnAgeSeconds))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outbox.publisher." + property);
    }
}
