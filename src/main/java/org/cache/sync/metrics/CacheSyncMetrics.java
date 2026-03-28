package org.cache.sync.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.util.concurrent.atomic.AtomicLong;

public class CacheSyncMetrics implements MeterBinder {

    private final AtomicLong publishedMessages = new AtomicLong(0);
    private final AtomicLong consumedMessages = new AtomicLong(0);
    private final AtomicLong failedMessages = new AtomicLong(0);
    private final AtomicLong pendingSize = new AtomicLong(0);
    private final AtomicLong lag = new AtomicLong(0);

    @Override
    public void bindTo(MeterRegistry registry) {
        Counter.builder("cache.sync.messages.published")
                .description("Total number of published cache clean messages")
                .register(registry)
                .increment(publishedMessages.get());

        Counter.builder("cache.sync.messages.consumed")
                .description("Total number of consumed cache clean messages")
                .register(registry)
                .increment(consumedMessages.get());

        Counter.builder("cache.sync.messages.failed")
                .description("Total number of failed cache clean messages")
                .register(registry)
                .increment(failedMessages.get());

        Gauge.builder("cache.sync.pending.size", pendingSize, AtomicLong::get)
                .description("Current number of pending messages in PEL")
                .register(registry);

        Gauge.builder("cache.sync.lag", lag, AtomicLong::get)
                .description("Lag between last consumed message and latest stream message")
                .register(registry);
    }

    public void incrementPublishedMessages() {
        publishedMessages.incrementAndGet();
    }

    public void incrementConsumedMessages() {
        consumedMessages.incrementAndGet();
    }

    public void incrementFailedMessages() {
        failedMessages.incrementAndGet();
    }

    public void setPendingSize(long size) {
        pendingSize.set(size);
    }

    public void setLag(long lag) {
        this.lag.set(lag);
    }

}
