package com.finex.marketdata;

import java.time.Instant;

/**
 * Base type for all market-data events.
 */
public sealed interface MarketDataEvent permits BookUpdate, TradeEvent, ExecutionEvent {

    Instant timestamp();

    String symbol();
}
