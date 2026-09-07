package com.example.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import ch.qos.logback.classic.Level;
import com.example.outbox.support.LogCapture;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherSchedulerTest {

    @Mock
    private OutboxPublisher publisher;

    private SimpleMeterRegistry meterRegistry;
    private OutboxPublisherScheduler scheduler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        scheduler = new OutboxPublisherScheduler(publisher, meterRegistry);
    }

    @Test
    void should_advanceCountersByPublishedCount_when_tickSucceeds() {
        // given
        given(publisher.publishBatch()).willReturn(4);

        // when
        scheduler.tick();

        // then
        assertThat(meterRegistry.get("outbox.events.published").counter().count()).isEqualTo(4.0);
    }

    @Test
    void should_recordDuration_when_tickSucceeds() {
        // given
        given(publisher.publishBatch()).willReturn(1);

        // when
        scheduler.tick();

        // then
        assertThat(meterRegistry.get("outbox.publish.duration").timer().count()).isEqualTo(1L);
    }

    @Test
    void should_refreshBacklogGauge_when_tickSucceeds() {
        // given
        given(publisher.publishBatch()).willReturn(0);

        // when
        scheduler.tick();

        // then
        then(publisher).should().refreshBacklogGauge();
    }

    @Test
    void should_notThrow_when_publishBatchFails() {
        // given
        willThrow(new QueryTimeoutException("database down")).given(publisher).publishBatch();

        // when
        // then
        assertThatCode(() -> scheduler.tick()).doesNotThrowAnyException();
    }

    @Test
    void should_logError_when_publishBatchFails() {
        // given
        willThrow(new QueryTimeoutException("database down")).given(publisher).publishBatch();

        // when
        try (LogCapture logs = LogCapture.attachTo(OutboxPublisherScheduler.class)) {
            scheduler.tick();

            // then
            assertThat(logs.messagesAt(Level.ERROR)).hasSize(1);
        }
    }

    @Test
    void should_notAdvanceCounters_when_publishBatchFails() {
        // given
        willThrow(new QueryTimeoutException("database down")).given(publisher).publishBatch();

        // when
        scheduler.tick();

        // then
        assertThat(meterRegistry.get("outbox.events.published").counter().count()).isEqualTo(0.0);
        assertThat(meterRegistry.get("outbox.publish.duration").timer().count()).isEqualTo(0L);
        then(publisher).should(never()).refreshBacklogGauge();
    }
}
