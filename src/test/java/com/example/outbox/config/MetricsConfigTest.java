package com.example.outbox.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricsConfigTest {

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        new MetricsConfig().commonTagsCustomizer("outbox", "local").customize(registry);
    }

    @Test
    void should_tagMeterWithApplicationAndEnvironment_when_meterRegistered() {
        // given
        registry.counter("orders.created").increment();

        // when
        double count = registry.get("orders.created")
                .tags("application", "outbox", "environment", "local")
                .counter()
                .count();

        // then
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void should_keepMeterOwnTags_when_commonTagsApplied() {
        // given
        registry.counter("orders.created", "outcome", "success").increment();

        // when
        var tags = registry.get("orders.created").counter().getId().getTags();

        // then
        assertThat(tags).extracting("key")
                .containsExactlyInAnyOrder("application", "environment", "outcome");
    }
}
