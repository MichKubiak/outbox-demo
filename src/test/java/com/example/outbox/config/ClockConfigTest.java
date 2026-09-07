package com.example.outbox.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

    @Test
    void should_provideUtcClock_when_beanIsCreated() {
        // given
        ClockConfig config = new ClockConfig();

        // when
        Clock clock = config.clock();

        // then
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void should_provideSystemClock_when_beanIsCreated() {
        // given
        ClockConfig config = new ClockConfig();

        // when
        Clock clock = config.clock();

        // then
        assertThat(clock).isEqualTo(Clock.systemUTC());
    }
}
