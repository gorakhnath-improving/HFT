package com.finex.marketdata;

/**
 * Consumer of market-data events.
 */
public interface MarketDataListener {

    void onEvent(MarketDataEvent event);
}
