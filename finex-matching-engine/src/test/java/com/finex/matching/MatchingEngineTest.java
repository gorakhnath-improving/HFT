package com.finex.matching;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.finex.common.domain.Order;
import com.finex.common.domain.Trade;
import com.finex.common.domain.enums.OrderStatus;
import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchingEngineTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String SYMBOL = "BTC-USD";

    @Test
    void fullFillOfIncomingBySingleRestingOrder() {
        MatchingEngine engine = new MatchingEngine(SYMBOL);

        engine.placeOrder(sellLimit(1L, 1L, new BigDecimal("50000"), BigDecimal.ONE), NOW);
        MatchResult result = engine.placeOrder(buyLimit(2L, 2L, new BigDecimal("50000"), BigDecimal.ONE), NOW);

        assertThat(result.trades()).hasSize(1);
        assertThat(result.order().status()).isEqualTo(OrderStatus.FILLED);
        assertThat(result.order().remainingQuantity()).isEqualTo(BigDecimal.ZERO);
        assertThat(result.addedToBook()).isFalse();
        assertThat(engine.orderBook().isEmpty()).isTrue();
    }

    @Test
    void partialFillOfIncomingThenRests() {
        MatchingEngine engine = new MatchingEngine(SYMBOL);

        engine.placeOrder(sellLimit(1L, 1L, new BigDecimal("50000"), BigDecimal.ONE), NOW);
        MatchResult result = engine.placeOrder(buyLimit(2L, 2L, new BigDecimal("50000"), new BigDecimal("3")), NOW);

        assertThat(result.trades()).hasSize(1);
        assertThat(result.order().status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        assertThat(result.order().remainingQuantity()).isEqualTo(new BigDecimal("2"));
        assertThat(result.addedToBook()).isTrue();
        assertThat(engine.orderBook().bestBid()).map(Order::orderId).hasValue(2L);
    }

    @Test
    void partialFillOfRestingIncomingFullyFilled() {
        MatchingEngine engine = new MatchingEngine(SYMBOL);

        engine.placeOrder(sellLimit(1L, 1L, new BigDecimal("50000"), new BigDecimal("3")), NOW);
        MatchResult result = engine.placeOrder(buyLimit(2L, 2L, new BigDecimal("50000"), BigDecimal.ONE), NOW);

        assertThat(result.trades()).hasSize(1);
        assertThat(result.order().status()).isEqualTo(OrderStatus.FILLED);
        assertThat(result.order().remainingQuantity()).isEqualTo(BigDecimal.ZERO);
        assertThat(engine.orderBook().bestAsk()).map(Order::orderId).hasValue(1L);
        assertThat(engine.orderBook().bestAsk()).map(Order::remainingQuantity).hasValue(new BigDecimal("2"));
    }

    @Test
    void multipleFillsAcrossPriceLevels() {
        MatchingEngine engine = new MatchingEngine(SYMBOL);

        engine.placeOrder(sellLimit(1L, 1L, new BigDecimal("50000"), BigDecimal.ONE), NOW);
        engine.placeOrder(sellLimit(2L, 2L, new BigDecimal("50100"), BigDecimal.ONE), NOW);

        MatchResult result = engine.placeOrder(
                buyLimit(3L, 3L, new BigDecimal("50100"), new BigDecimal("2")), NOW);

        assertThat(result.trades()).hasSize(2);
        assertThat(result.trades()).map(Trade::price)
                .containsExactly(new BigDecimal("50000"), new BigDecimal("50100"));
        assertThat(result.order().status()).isEqualTo(OrderStatus.FILLED);
        assertThat(engine.orderBook().isEmpty()).isTrue();
    }

    @Test
    void marketOrderFullyFillsAgainstRestingLiquidity() {
        MatchingEngine engine = new MatchingEngine(SYMBOL);

        engine.placeOrder(sellLimit(1L, 1L, new BigDecimal("50000"), new BigDecimal("2")), NOW);
        MatchResult result = engine.placeOrder(buyMarket(2L, 2L, new BigDecimal("2")), NOW);

        assertThat(result.trades()).hasSize(1);
        assertThat(result.order().status()).isEqualTo(OrderStatus.FILLED);
        assertThat(result.trades().get(0).price()).isEqualTo(new BigDecimal("50000"));
        assertThat(engine.orderBook().isEmpty()).isTrue();
    }

    @Test
    void marketOrderCancelledWhenNoLiquidity() {
        MatchingEngine engine = new MatchingEngine(SYMBOL);

        MatchResult result = engine.placeOrder(buyMarket(1L, 1L, BigDecimal.ONE), NOW);

        assertThat(result.trades()).isEmpty();
        assertThat(result.order().status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.order().remainingQuantity()).isEqualTo(BigDecimal.ONE);
        assertThat(result.addedToBook()).isFalse();
    }

    @Test
    void marketOrderPartialFillThenCancelled() {
        MatchingEngine engine = new MatchingEngine(SYMBOL);

        engine.placeOrder(sellLimit(1L, 1L, new BigDecimal("50000"), BigDecimal.ONE), NOW);
        MatchResult result = engine.placeOrder(buyMarket(2L, 2L, new BigDecimal("2")), NOW);

        assertThat(result.trades()).hasSize(1);
        assertThat(result.order().status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.order().remainingQuantity()).isEqualTo(BigDecimal.ONE);
    }

    @Test
    void limitOrderNotMarketableRests() {
        MatchingEngine engine = new MatchingEngine(SYMBOL);

        engine.placeOrder(sellLimit(1L, 1L, new BigDecimal("51000"), BigDecimal.ONE), NOW);
        MatchResult result = engine.placeOrder(buyLimit(2L, 2L, new BigDecimal("50000"), BigDecimal.ONE), NOW);

        assertThat(result.trades()).isEmpty();
        assertThat(result.addedToBook()).isTrue();
        assertThat(engine.orderBook().bestBid()).map(Order::orderId).hasValue(2L);
        assertThat(engine.orderBook().bestAsk()).map(Order::orderId).hasValue(1L);
    }

    @Test
    void buyLimitOnlyMatchesAsksAtOrBelowItsPrice() {
        MatchingEngine engine = new MatchingEngine(SYMBOL);

        engine.placeOrder(sellLimit(1L, 1L, new BigDecimal("50000"), BigDecimal.ONE), NOW);
        engine.placeOrder(sellLimit(2L, 2L, new BigDecimal("52000"), BigDecimal.ONE), NOW);

        MatchResult result = engine.placeOrder(
                buyLimit(3L, 3L, new BigDecimal("51000"), new BigDecimal("2")), NOW);

        assertThat(result.trades()).hasSize(1);
        assertThat(result.trades().get(0).price()).isEqualTo(new BigDecimal("50000"));
        assertThat(result.order().remainingQuantity()).isEqualTo(BigDecimal.ONE);
        assertThat(result.addedToBook()).isTrue();
        assertThat(engine.orderBook().bestAsk()).map(Order::orderId).hasValue(2L);
    }

    @Test
    void sellLimitOnlyMatchesBidsAtOrAboveItsPrice() {
        MatchingEngine engine = new MatchingEngine(SYMBOL);

        engine.placeOrder(buyLimit(1L, 1L, new BigDecimal("52000"), BigDecimal.ONE), NOW);
        engine.placeOrder(buyLimit(2L, 2L, new BigDecimal("50000"), BigDecimal.ONE), NOW);

        MatchResult result = engine.placeOrder(
                sellLimit(3L, 3L, new BigDecimal("51000"), new BigDecimal("2")), NOW);

        assertThat(result.trades()).hasSize(1);
        assertThat(result.trades().get(0).price()).isEqualTo(new BigDecimal("52000"));
        assertThat(result.order().remainingQuantity()).isEqualTo(BigDecimal.ONE);
        assertThat(engine.orderBook().bestBid()).map(Order::orderId).hasValue(2L);
    }

    @Test
    void samePriceTimePriorityRestingOrderHitFirst() {
        MatchingEngine engine = new MatchingEngine(SYMBOL);

        engine.placeOrder(sellLimit(1L, 1L, new BigDecimal("50000"), BigDecimal.ONE), NOW);
        engine.placeOrder(sellLimit(2L, 2L, new BigDecimal("50000"), BigDecimal.ONE), NOW);

        MatchResult result = engine.placeOrder(
                buyLimit(3L, 3L, new BigDecimal("50000"), BigDecimal.ONE), NOW);

        assertThat(result.trades()).hasSize(1);
        assertThat(result.trades().get(0).sellOrderId()).isEqualTo(1L);
        assertThat(engine.orderBook().bestAsk()).map(Order::orderId).hasValue(2L);
    }

    @Test
    void cancellationRemovesRestingOrder() {
        MatchingEngine engine = new MatchingEngine(SYMBOL);

        engine.placeOrder(sellLimit(1L, 1L, new BigDecimal("50000"), BigDecimal.ONE), NOW);
        boolean cancelled = engine.cancelOrder(1L);

        assertThat(cancelled).isTrue();
        assertThat(engine.orderBook().isEmpty()).isTrue();
    }

    @Test
    void rejectsOrderWithWrongSymbol() {
        MatchingEngine engine = new MatchingEngine(SYMBOL);

        Order other = new Order(
                1L, "cid-1", 100L, "ETH-USD", Side.SELL, OrderType.LIMIT,
                new BigDecimal("50000"), BigDecimal.ONE, BigDecimal.ONE,
                1L, NOW, OrderStatus.OPEN);

        assertThatThrownBy(() -> engine.placeOrder(other, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void deterministicSameInputProducesSameTradesAndBook() {
        MatchingEngine engine = new MatchingEngine(SYMBOL);
        Order sell = sellLimit(1L, 1L, new BigDecimal("50000"), new BigDecimal("2"));
        Order buy = buyLimit(2L, 2L, new BigDecimal("50000"), new BigDecimal("2"));

        MatchResult result1 = engine.placeOrder(sell, NOW);
        engine.placeOrder(buy, NOW);

        MatchingEngine engine2 = new MatchingEngine(SYMBOL);
        MatchResult result2a = engine2.placeOrder(sell, NOW);
        MatchResult result2b = engine2.placeOrder(buy, NOW);

        assertThat(result1.trades()).hasSize(0); // sell rests
        assertThat(result2a.trades()).hasSize(0);
        assertThat(result2b.trades()).hasSize(1);

        List<Trade> trades1 = result1.trades();
        List<Trade> trades2 = result2b.trades();

        assertThat(engine.orderBook().bestBid()).isEmpty();
        assertThat(engine2.orderBook().bestBid()).isEmpty();
        assertThat(engine.orderBook().isEmpty()).isTrue();
        assertThat(engine2.orderBook().isEmpty()).isTrue();
        assertThat(trades1).hasSize(0);
        assertThat(trades2).hasSize(1);
        assertThat(trades2.get(0).price()).isEqualTo(new BigDecimal("50000"));
        assertThat(trades2.get(0).quantity()).isEqualTo(new BigDecimal("2"));
    }

    @Test
    void restingOrderReinsertedAfterPartialFillMaintainsPriority() {
        MatchingEngine engine = new MatchingEngine(SYMBOL);

        engine.placeOrder(sellLimit(1L, 1L, new BigDecimal("50000"), new BigDecimal("3")), NOW);
        MatchResult first = engine.placeOrder(buyLimit(2L, 2L, new BigDecimal("50000"), BigDecimal.ONE), NOW);
        MatchResult second = engine.placeOrder(buyLimit(3L, 3L, new BigDecimal("50000"), new BigDecimal("2")), NOW);

        assertThat(first.trades()).hasSize(1);
        assertThat(second.trades()).hasSize(1);
        assertThat(second.trades().get(0).price()).isEqualTo(new BigDecimal("50000"));
        assertThat(second.order().status()).isEqualTo(OrderStatus.FILLED);
        assertThat(engine.orderBook().isEmpty()).isTrue();
    }

    private static Order buyLimit(long orderId, long sequence, BigDecimal price, BigDecimal qty) {
        return new Order(
                orderId, "cid-" + orderId, 100L, SYMBOL, Side.BUY, OrderType.LIMIT,
                price, qty, qty, sequence, NOW, OrderStatus.OPEN);
    }

    private static Order sellLimit(long orderId, long sequence, BigDecimal price, BigDecimal qty) {
        return new Order(
                orderId, "cid-" + orderId, 100L, SYMBOL, Side.SELL, OrderType.LIMIT,
                price, qty, qty, sequence, NOW, OrderStatus.OPEN);
    }

    private static Order buyMarket(long orderId, long sequence, BigDecimal qty) {
        return new Order(
                orderId, "cid-" + orderId, 100L, SYMBOL, Side.BUY, OrderType.MARKET,
                null, qty, qty, sequence, NOW, OrderStatus.OPEN);
    }
}
