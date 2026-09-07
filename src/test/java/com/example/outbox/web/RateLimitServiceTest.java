package com.example.outbox.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.outbox.config.RateLimitProperties;
import com.example.outbox.support.MutableClock;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RateLimitServiceTest {

    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

    private final MutableClock clock = MutableClock.at("2026-09-07T10:00:00Z");

    @Test
    void should_allowRequest_when_underCapacity() {
        // given
        RateLimitService service = service(5, 100);

        // when
        ConsumptionProbe probe = service.tryConsume("10.0.0.1");

        // then
        assertThat(probe.isConsumed()).isTrue();
    }

    @Test
    void should_rejectRequest_when_capacityExhausted() {
        // given
        RateLimitService service = service(3, 100);
        consume(service, "10.0.0.1", 3);

        // when
        ConsumptionProbe probe = service.tryConsume("10.0.0.1");

        // then
        assertThat(probe.isConsumed()).isFalse();
    }

    @Test
    void should_isolateBuckets_when_differentClientIps() {
        // given
        RateLimitService service = service(2, 100);
        consume(service, "10.0.0.1", 2);

        // when
        ConsumptionProbe probe = service.tryConsume("10.0.0.2");

        // then
        assertThat(probe.isConsumed()).isTrue();
    }

    @Test
    void should_rejectHundredFirstRequest_when_withinOneRefillWindow() {
        // given
        RateLimitService service = service(100, 1000);
        List<Boolean> outcomes = consume(service, "10.0.0.1", 100);

        // when
        ConsumptionProbe probe = service.tryConsume("10.0.0.1");

        // then
        assertThat(outcomes).containsOnly(true);
        assertThat(probe.isConsumed()).isFalse();
    }

    @Test
    void should_allowAgain_when_refillWindowElapsed() {
        // given
        RateLimitService service = service(2, 100);
        consume(service, "10.0.0.1", 2);

        // when
        clock.advance(REFILL_PERIOD);
        ConsumptionProbe probe = service.tryConsume("10.0.0.1");

        // then
        assertThat(probe.isConsumed()).isTrue();
    }

    @Test
    void should_reportSecondsToWait_when_rejected() {
        // given
        RateLimitService service = service(1, 100);
        consume(service, "10.0.0.1", 1);

        // when
        ConsumptionProbe probe = service.tryConsume("10.0.0.1");

        // then
        assertThat(RateLimitFilter.secondsToWait(probe.getNanosToWaitForRefill())).isBetween(1L, 60L);
    }

    @Test
    void should_evictBucket_when_idleBeyondRetention() {
        // given
        RateLimitService service = service(5, 100);
        service.tryConsume("10.0.0.1");

        // when
        clock.advance(Duration.ofMinutes(5));
        service.tryConsume("10.0.0.2");

        // then
        assertThat(service.trackedClients()).isEqualTo(1);
    }

    @Test
    void should_stopGrowing_when_maxTrackedClientsReached() {
        // given
        RateLimitService service = service(5, 3);

        // when
        for (int i = 1; i <= 10; i++) {
            service.tryConsume("10.0.0." + i);
        }

        // then
        assertThat(service.trackedClients()).isEqualTo(3);
    }

    @Test
    void should_stillLimit_when_overflowBucketIsUsed() {
        // given
        RateLimitService service = service(2, 1);
        service.tryConsume("10.0.0.1");
        service.tryConsume("10.0.0.2");
        service.tryConsume("10.0.0.3");

        // when
        ConsumptionProbe probe = service.tryConsume("10.0.0.4");

        // then
        assertThat(probe.isConsumed()).isFalse();
    }

    @Test
    void should_forgetAllClients_when_cleared() {
        // given
        RateLimitService service = service(2, 100);
        consume(service, "10.0.0.1", 2);

        // when
        service.clear();
        ConsumptionProbe probe = service.tryConsume("10.0.0.1");

        // then
        assertThat(probe.isConsumed()).isTrue();
    }

    private RateLimitService service(int capacity, int maxTrackedClients) {
        RateLimitProperties properties = new RateLimitProperties(capacity, REFILL_PERIOD, List.of(),
                maxTrackedClients, Duration.ofMinutes(1));
        return new RateLimitService(properties, clock);
    }

    private List<Boolean> consume(RateLimitService service, String key, int times) {
        List<Boolean> outcomes = new java.util.ArrayList<>();
        for (int i = 0; i < times; i++) {
            outcomes.add(service.tryConsume(key).isConsumed());
        }
        return outcomes;
    }
}
