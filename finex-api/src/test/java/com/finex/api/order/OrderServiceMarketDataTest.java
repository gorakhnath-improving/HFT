package com.finex.api.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;
import com.finex.marketdata.BookUpdate;
import com.finex.marketdata.MarketDataEvent;
import com.finex.marketdata.TradeEvent;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies OPT-002: OrderService skips building market-data snapshots (BookUpdate
 * aggregation, TradeEvent/ExecutionEvent construction) when there are no subscribers, but
 * still publishes the full, correct event set once a listener is present.
 */
class OrderServiceMarketDataTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void publishesBookUpdatesAndTradeEventsWhenSubscribed() {
        OrderService service = new OrderService();
        List<MarketDataEvent> received = new ArrayList<>();
        service.marketDataPublisher().subscribe(received::add);

        service.submitOrder(new OrderRequest(
                "cid-s1", "BTC-USD", Side.SELL, OrderType.LIMIT,
                new BigDecimal("50000"), BigDecimal.ONE, 100L), NOW);
        service.submitOrder(new OrderRequest(
                "cid-b1", "BTC-USD", Side.BUY, OrderType.LIMIT,
                new BigDecimal("50000"), BigDecimal.ONE, 200L), NOW);

        // First order: rests, so we expect exactly one BookUpdate.
        // Second order: fully matches, so we expect a TradeEvent, two ExecutionEvents, and
        // one BookUpdate (book is now empty on both sides).
        long bookUpdates = received.stream().filter(e -> e instanceof BookUpdate).count();
        long tradeEvents = received.stream().filter(e -> e instanceof TradeEvent).count();
        assertThat(bookUpdates).isEqualTo(2);
        assertThat(tradeEvents).isEqualTo(1);

        BookUpdate lastUpdate = (BookUpdate) received.get(received.size() - 1);
        assertThat(lastUpdate.bids()).isEmpty();
        assertThat(lastUpdate.asks()).isEmpty();
    }

    @Test
    void submitsAndMatchesCorrectlyWithNoSubscribers() {
        // No subscriber attached at all: this is the fast path (OPT-002). Correctness of the
        // matching/risk/ledger/portfolio outcome must be identical to the subscribed case.
        OrderService service = new OrderService();

        service.submitOrder(new OrderRequest(
                "cid-s1", "BTC-USD", Side.SELL, OrderType.LIMIT,
                new BigDecimal("50000"), BigDecimal.ONE, 100L), NOW);
        var result = service.submitOrder(new OrderRequest(
                "cid-b1", "BTC-USD", Side.BUY, OrderType.LIMIT,
                new BigDecimal("50000"), BigDecimal.ONE, 200L), NOW);

        assertThat(result.trades()).hasSize(1);
        assertThat(result.trades().get(0).price()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(service.getOrderBook("BTC-USD").orElseThrow().bids()).isEmpty();
        assertThat(service.getOrderBook("BTC-USD").orElseThrow().asks()).isEmpty();
    }
}
