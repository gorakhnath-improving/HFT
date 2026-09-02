package com.finex.marketdata;

/**
 * Publishes market-data events to zero or more {@link MarketDataListener}s.
 */
public interface MarketDataPublisher {

    void subscribe(MarketDataListener listener);

    void unsubscribe(MarketDataListener listener);

    void publish(MarketDataEvent event);
}
