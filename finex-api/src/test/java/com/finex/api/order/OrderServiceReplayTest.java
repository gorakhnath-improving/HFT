package com.finex.api.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;
import com.finex.eventlog.EventStore;
import com.finex.eventlog.ReplayEngine;

import static org.assertj.core.api.Assertions.assertThat;

class OrderServiceReplayTest {

    private static final Instant T1 = Instant.parse("2026-01-01T00:00:01Z");
    private static final Instant T2 = Instant.parse("2026-01-01T00:00:02Z");
    private static final Instant T3 = Instant.parse("2026-01-01T00:00:03Z");
    private static final Instant T4 = Instant.parse("2026-01-01T00:00:04Z");

    @Test
    void replayReconstructsOrderBook() {
        OrderService original = new OrderService();

        original.submitOrder(new OrderRequest(
                "cid-s1", "BTC-USD", Side.SELL, OrderType.LIMIT,
                new BigDecimal("50000"), new BigDecimal("5"), 200L), T1);
        original.submitOrder(new OrderRequest(
                "cid-b1", "BTC-USD", Side.BUY, OrderType.LIMIT,
                new BigDecimal("49000"), new BigDecimal("3"), 100L), T2);
        original.submitOrder(new OrderRequest(
                "cid-b2", "BTC-USD", Side.BUY, OrderType.LIMIT,
                new BigDecimal("50000"), new BigDecimal("2"), 100L), T3);
        original.cancelOrder(1L, T4);

        Optional<OrderBookView> originalBook = original.getOrderBook("BTC-USD");
        assertThat(originalBook).isPresent();
        List<OrderResponse> originalBids = originalBook.get().bids();
        List<OrderResponse> originalAsks = originalBook.get().asks();

        EventStore store = original.eventStore();
        OrderService replayed = new OrderService(store);
        ReplayEngine.replay(store, replayed);

        Optional<OrderBookView> replayedBook = replayed.getOrderBook("BTC-USD");
        assertThat(replayedBook).isPresent();
        assertThat(replayedBook.get().bids()).isEqualTo(originalBids);
        assertThat(replayedBook.get().asks()).isEqualTo(originalAsks);

        assertThat(replayed.getOrder(2L)).isEqualTo(original.getOrder(2L));
        assertThat(replayed.getOrder(3L)).isEqualTo(original.getOrder(3L));

        // Phase 15: verify ledger and portfolio are also reconstructed identically.
        assertThat(replayed.ledger().entries()).isEqualTo(original.ledger().entries());
        assertThat(replayed.portfolio(100L)).isEqualTo(original.portfolio(100L));
        assertThat(replayed.portfolio(200L)).isEqualTo(original.portfolio(200L));
    }
}
