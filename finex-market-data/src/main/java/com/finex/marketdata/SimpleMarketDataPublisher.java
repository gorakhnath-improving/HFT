package com.finex.marketdata;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Synchronous baseline publisher. Listeners are called on the caller's thread; async
 * dispatch can be layered on top later without changing the interface.
 */
public class SimpleMarketDataPublisher implements MarketDataPublisher {

    private final List<MarketDataListener> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void subscribe(MarketDataListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        listeners.add(listener);
    }

    @Override
    public void unsubscribe(MarketDataListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void publish(MarketDataEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        for (MarketDataListener listener : listeners) {
            listener.onEvent(event);
        }
    }

    @Override
    public boolean hasSubscribers() {
        return !listeners.isEmpty();
    }
}
