package com.finex.api.metrics;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Records business-level metrics for the FinEx API. Can be used standalone (with a
 * {@link SimpleMeterRegistry}) in tests or injected as a Spring bean with a Prometheus
 * registry.
 */
@Service
public class MetricsService {

    private final Counter ordersSubmitted;
    private final Counter ordersRejected;
    private final Counter ordersCancelled;
    private final Counter trades;
    private final Timer orderLatency;

    public MetricsService() {
        this(new SimpleMeterRegistry());
    }

    @Autowired
    public MetricsService(MeterRegistry registry) {
        this.ordersSubmitted = registry.counter("finex.orders.submitted");
        this.ordersRejected = registry.counter("finex.orders.rejected");
        this.ordersCancelled = registry.counter("finex.orders.cancelled");
        this.trades = registry.counter("finex.trades");
        this.orderLatency = registry.timer("finex.order.latency");
    }

    public void recordSubmitted(int tradeCount) {
        ordersSubmitted.increment();
        if (tradeCount > 0) {
            trades.increment(tradeCount);
        }
    }

    public void recordRejected() {
        ordersRejected.increment();
    }

    public void recordCancelled() {
        ordersCancelled.increment();
    }

    public void recordLatency(Duration duration) {
        orderLatency.record(duration);
    }

    // Exposed for tests.
    public double submittedCount() {
        return ordersSubmitted.count();
    }

    public double rejectedCount() {
        return ordersRejected.count();
    }

    public double cancelledCount() {
        return ordersCancelled.count();
    }

    public double tradeCount() {
        return trades.count();
    }
}
