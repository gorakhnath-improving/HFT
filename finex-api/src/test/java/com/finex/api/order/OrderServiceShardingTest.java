package com.finex.api.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;
import com.finex.eventlog.EventStore;
import com.finex.eventlog.InMemoryEventStore;

import static org.assertj.core.api.Assertions.assertThat;

class OrderServiceShardingTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void differentSymbolsAreRoutedToIndependentEngines() {
        EventStore store = new InMemoryEventStore();
        OrderService service = new OrderService(store, 4);

        service.submitOrder(new OrderRequest(
                "cid-s1", "BTC-USD", Side.SELL, OrderType.LIMIT,
                new BigDecimal("50000"), new BigDecimal("1"), 200L), NOW);
        service.submitOrder(new OrderRequest(
                "cid-b1", "BTC-USD", Side.BUY, OrderType.LIMIT,
                new BigDecimal("50000"), new BigDecimal("1"), 100L), NOW);
        service.submitOrder(new OrderRequest(
                "cid-s2", "ETH-USD", Side.SELL, OrderType.LIMIT,
                new BigDecimal("3000"), new BigDecimal("2"), 200L), NOW);

        Optional<OrderResponse> btcSell = service.getOrder(1L);
        Optional<OrderResponse> btcBuy = service.getOrder(3L);
        Optional<OrderResponse> ethSell = service.getOrder(5L);

        assertThat(btcSell).isPresent();
        assertThat(btcBuy).isPresent();
        assertThat(ethSell).isPresent();

        assertThat(btcSell.get().symbol()).isEqualTo("BTC-USD");
        assertThat(btcBuy.get().symbol()).isEqualTo("BTC-USD");
        assertThat(ethSell.get().symbol()).isEqualTo("ETH-USD");

        // BTC buy matched the BTC sell -> both BTC orders are FILLED.
        assertThat(btcSell.get().status().name()).isEqualTo("FILLED");
        assertThat(btcBuy.get().status().name()).isEqualTo("FILLED");
        // ETH sell rests in its own shard.
        assertThat(ethSell.get().status().name()).isEqualTo("OPEN");
    }
}
