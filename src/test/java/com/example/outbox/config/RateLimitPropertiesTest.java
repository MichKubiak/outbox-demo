package com.example.outbox.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RateLimitPropertiesTest {

    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);
    private static final Duration SWEEP_INTERVAL = Duration.ofMinutes(5);

    @Test
    void should_exposeConfiguredValues_when_allValuesAreValid() {
        // given
        // when
        RateLimitProperties properties =
                new RateLimitProperties(100, REFILL_PERIOD, List.of("10.0.0.0/8"), 1000, SWEEP_INTERVAL);

        // then
        assertThat(properties)
                .extracting(RateLimitProperties::capacity, RateLimitProperties::refillPeriod,
                        RateLimitProperties::maxTrackedClients, RateLimitProperties::sweepInterval)
                .containsExactly(100, REFILL_PERIOD, 1000, SWEEP_INTERVAL);
        assertThat(properties.trustedProxies()).containsExactly("10.0.0.0/8");
    }

    @Test
    void should_useEmptyList_when_trustedProxiesIsNull() {
        // given
        // when
        RateLimitProperties properties = new RateLimitProperties(100, REFILL_PERIOD, null, 1000, SWEEP_INTERVAL);

        // then
        assertThat(properties.trustedProxies()).isEmpty();
    }

    @Test
    void should_copyTrustedProxies_when_sourceListIsMutated() {
        // given
        List<String> source = new ArrayList<>(List.of("10.0.0.0/8"));
        RateLimitProperties properties = new RateLimitProperties(100, REFILL_PERIOD, source, 1000, SWEEP_INTERVAL);

        // when
        source.add("0.0.0.0/0");

        // then
        assertThat(properties.trustedProxies()).containsExactly("10.0.0.0/8");
    }

    @Test
    void should_rejectMutation_when_trustedProxiesIsModified() {
        // given
        RateLimitProperties properties =
                new RateLimitProperties(100, REFILL_PERIOD, List.of("10.0.0.0/8"), 1000, SWEEP_INTERVAL);

        // when
        // then
        assertThatThrownBy(() -> properties.trustedProxies().add("0.0.0.0/0"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_accept_when_valuesAreAtBoundary() {
        // given
        // when
        // then
        assertThatCode(() -> new RateLimitProperties(1, Duration.ofMillis(1), List.of(), 1, Duration.ofMillis(1)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("invalidConfigurations")
    void should_rejectConfiguration_when_valueIsOutOfRange(String property, int capacity, Duration refillPeriod,
                                                           int maxTrackedClients, Duration sweepInterval) {
        // given
        // when
        // then
        assertThatThrownBy(() -> new RateLimitProperties(capacity, refillPeriod, List.of(), maxTrackedClients,
                sweepInterval))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ratelimit." + property);
    }

    private static Stream<Arguments> invalidConfigurations() {
        return Stream.of(
                Arguments.of("capacity", 0, REFILL_PERIOD, 1000, SWEEP_INTERVAL),
                Arguments.of("capacity", -1, REFILL_PERIOD, 1000, SWEEP_INTERVAL),
                Arguments.of("refill-period", 100, null, 1000, SWEEP_INTERVAL),
                Arguments.of("refill-period", 100, Duration.ZERO, 1000, SWEEP_INTERVAL),
                Arguments.of("refill-period", 100, Duration.ofSeconds(-1), 1000, SWEEP_INTERVAL),
                Arguments.of("max-tracked-clients", 100, REFILL_PERIOD, 0, SWEEP_INTERVAL),
                Arguments.of("max-tracked-clients", 100, REFILL_PERIOD, -1, SWEEP_INTERVAL),
                Arguments.of("sweep-interval", 100, REFILL_PERIOD, 1000, null),
                Arguments.of("sweep-interval", 100, REFILL_PERIOD, 1000, Duration.ZERO),
                Arguments.of("sweep-interval", 100, REFILL_PERIOD, 1000, Duration.ofSeconds(-1)));
    }
}
