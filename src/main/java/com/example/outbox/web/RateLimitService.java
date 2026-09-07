package com.example.outbox.web;

import com.example.outbox.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class RateLimitService {

    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final int capacity;
    private final Duration refillPeriod;
    private final int maxTrackedClients;
    private final long sweepIntervalMillis;
    private final long retentionMillis;
    private final Clock clock;
    private final TimeMeter timeMeter;
    private final Bucket overflowBucket;
    private final AtomicLong nextSweepAt;

    public RateLimitService(RateLimitProperties properties, Clock clock) {
        this.capacity = properties.capacity();
        this.refillPeriod = properties.refillPeriod();
        this.maxTrackedClients = properties.maxTrackedClients();
        this.sweepIntervalMillis = properties.sweepInterval().toMillis();
        this.retentionMillis = properties.refillPeriod().toMillis() * 2;
        this.clock = clock;
        this.timeMeter = new ClockTimeMeter(clock);
        this.overflowBucket = newBucket();
        this.nextSweepAt = new AtomicLong(clock.millis() + sweepIntervalMillis);
    }

    public ConsumptionProbe tryConsume(String key) {
        long now = clock.millis();
        sweepIfDue(now);
        Entry entry = entries.get(key);
        if (entry == null) {
            if (entries.size() >= maxTrackedClients) {
                sweep(now);
            }
            if (entries.size() >= maxTrackedClients) {
                return overflowBucket.tryConsumeAndReturnRemaining(1);
            }
            entry = entries.computeIfAbsent(key, ignored -> new Entry(newBucket(), now));
        }
        entry.touch(now);
        return entry.bucket().tryConsumeAndReturnRemaining(1);
    }

    int trackedClients() {
        return entries.size();
    }

    void clear() {
        entries.clear();
        nextSweepAt.set(clock.millis() + sweepIntervalMillis);
    }

    private void sweepIfDue(long now) {
        long due = nextSweepAt.get();
        if (now < due) {
            return;
        }
        if (nextSweepAt.compareAndSet(due, now + sweepIntervalMillis)) {
            sweep(now);
        }
    }

    private void sweep(long now) {
        entries.entrySet().removeIf(entry -> now - entry.getValue().lastAccess() > retentionMillis);
    }

    private Bucket newBucket() {
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, refillPeriod)
                .build();
        return Bucket.builder()
                .addLimit(bandwidth)
                .withCustomTimePrecision(timeMeter)
                .build();
    }

    private static final class Entry {

        private final Bucket bucket;
        private volatile long lastAccess;

        private Entry(Bucket bucket, long lastAccess) {
            this.bucket = bucket;
            this.lastAccess = lastAccess;
        }

        private Bucket bucket() {
            return bucket;
        }

        private long lastAccess() {
            return lastAccess;
        }

        private void touch(long now) {
            this.lastAccess = now;
        }
    }

    private static final class ClockTimeMeter implements TimeMeter {

        private final Clock clock;

        private ClockTimeMeter(Clock clock) {
            this.clock = clock;
        }

        @Override
        public long currentTimeNanos() {
            return clock.millis() * NANOS_PER_MILLI;
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
