package com.finex.marketdata;

/**
 * Publishes market-data events to zero or more {@link MarketDataListener}s.
 */
public interface MarketDataPublisher {

    void subscribe(MarketDataListener listener);

    void unsubscribe(MarketDataListener listener);

    void publish(MarketDataEvent event);

    /**
     * Returns true if at least one listener is currently subscribed. Callers on a
     * latency-sensitive path should check this before doing any work to build an event,
     * since {@link #publish(MarketDataEvent)} is a no-op with zero subscribers.
     */
    boolean hasSubscribers();
}
