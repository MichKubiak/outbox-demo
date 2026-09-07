package com.example.outbox.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.example.outbox.config.OutboxProperties;
import com.example.outbox.repository.OutboxEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

@ExtendWith(MockitoExtension.class)
class OutboxBacklogHealthIndicatorTest {

    private static final Instant NOW = Instant.parse("2026-09-07T10:15:30Z");
    private static final long WARN_AGE_SECONDS = 60L;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private OutboxEventRepository repository;

    private OutboxBacklogHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        OutboxProperties properties = new OutboxProperties(true, 5000L, 100, WARN_AGE_SECONDS);
        indicator = new OutboxBacklogHealthIndicator(repository, properties, clock);
    }

    @Test
    void should_reportUp_when_backlogEmpty() {
        // given
        given(repository.findOldestUnprocessedCreatedAt()).willReturn(Optional.empty());

        // when
        Health health = indicator.health();

        // then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("oldestUnprocessedAgeSeconds", 0L);
    }

    @ParameterizedTest(name = "{index}: age {0}s")
    @ValueSource(longs = {0L, 1L, 59L, 60L})
    void should_reportUp_when_backlogWithinThreshold(long ageSeconds) {
        // given
        given(repository.findOldestUnprocessedCreatedAt()).willReturn(Optional.of(oldest(ageSeconds)));

        // when
        Health health = indicator.health();

        // then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void should_reportDown_when_oldestEventExceedsThreshold() {
        // given
        given(repository.findOldestUnprocessedCreatedAt()).willReturn(Optional.of(oldest(61L)));

        // when
        Health health = indicator.health();

        // then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void should_reportMeasuredAge_when_backlogIsStale() {
        // given
        given(repository.findOldestUnprocessedCreatedAt()).willReturn(Optional.of(oldest(180L)));

        // when
        Health health = indicator.health();

        // then
        assertThat(health.getDetails())
                .containsEntry("oldestUnprocessedAgeSeconds", 180L)
                .containsEntry("warnAgeSeconds", WARN_AGE_SECONDS);
    }

    private Instant oldest(long ageSeconds) {
        return NOW.minusSeconds(ageSeconds);
    }
}
