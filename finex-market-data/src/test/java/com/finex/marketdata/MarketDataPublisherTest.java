package com.finex.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.finex.common.domain.Trade;
import com.finex.common.domain.enums.OrderStatus;

import static org.assertj.core.api.Assertions.assertThat;

class MarketDataPublisherTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void publishesEventsToSubscribers() {
        SimpleMarketDataPublisher publisher = new SimpleMarketDataPublisher();
        List<MarketDataEvent> received = new ArrayList<>();
        publisher.subscribe(received::add);

        Trade trade = new Trade(
                1L, 1L, 2L, "BTC-USD", new BigDecimal("50000"), new BigDecimal("1"),
                NOW, 100L, 200L, 1L);

        publisher.publish(new TradeEvent("BTC-USD", trade, NOW));
        publisher.publish(new ExecutionEvent(1L, 100L, "BTC-USD", trade, OrderStatus.FILLED, NOW));
        publisher.publish(new ExecutionEvent(2L, 200L, "BTC-USD", trade, OrderStatus.FILLED, NOW));

        assertThat(received).hasSize(3);
        assertThat(received.get(0)).isInstanceOf(TradeEvent.class);
        assertThat(received.get(1)).isInstanceOf(ExecutionEvent.class);
        assertThat(received.get(2)).isInstanceOf(ExecutionEvent.class);
    }

    @Test
    void unsubscribeRemovesListener() {
        SimpleMarketDataPublisher publisher = new SimpleMarketDataPublisher();
        List<MarketDataEvent> received = new ArrayList<>();
        MarketDataListener listener = received::add;
        publisher.subscribe(listener);
        publisher.unsubscribe(listener);

        publisher.publish(new BookUpdate("BTC-USD", List.of(), List.of(), NOW));

        assertThat(received).isEmpty();
    }

    @Test
    void hasSubscribersReflectsCurrentListenerCount() {
        SimpleMarketDataPublisher publisher = new SimpleMarketDataPublisher();
        assertThat(publisher.hasSubscribers()).isFalse();

        MarketDataListener listener = event -> { };
        publisher.subscribe(listener);
        assertThat(publisher.hasSubscribers()).isTrue();

        publisher.unsubscribe(listener);
        assertThat(publisher.hasSubscribers()).isFalse();
    }
}
