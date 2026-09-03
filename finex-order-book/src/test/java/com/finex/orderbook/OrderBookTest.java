package com.finex.orderbook;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.finex.common.domain.Order;
import com.finex.common.domain.enums.OrderStatus;
import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderBookTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final String SYMBOL = "BTC-USD";

    @Test
    void emptyBookHasNoBestPrices() {
        OrderBook book = new OrderBook(SYMBOL);

        assertThat(book.bestBid()).isEmpty();
        assertThat(book.bestAsk()).isEmpty();
        assertThat(book.bestBidPrice()).isNull();
        assertThat(book.bestAskPrice()).isNull();
        assertThat(book.isEmpty()).isTrue();
    }

    @Test
    void bestBidIsHighestBuyPrice() {
        OrderBook book = new OrderBook(SYMBOL);
        book.addOrder(limitBuy(1, 100, new BigDecimal("50000")));
        book.addOrder(limitBuy(2, 101, new BigDecimal("51000")));
        book.addOrder(limitBuy(3, 102, new BigDecimal("49000")));

        assertThat(book.bestBid()).map(Order::price).hasValue(new BigDecimal("51000"));
        assertThat(book.bestBidPrice()).isEqualTo(new BigDecimal("51000"));
        assertThat(book.bestBid()).map(Order::orderId).hasValue(2L);
    }

    @Test
    void bestAskIsLowestSellPrice() {
        OrderBook book = new OrderBook(SYMBOL);
        book.addOrder(limitSell(1, 100, new BigDecimal("51000")));
        book.addOrder(limitSell(2, 101, new BigDecimal("50000")));
        book.addOrder(limitSell(3, 102, new BigDecimal("52000")));

        assertThat(book.bestAsk()).map(Order::price).hasValue(new BigDecimal("50000"));
        assertThat(book.bestAskPrice()).isEqualTo(new BigDecimal("50000"));
        assertThat(book.bestAsk()).map(Order::orderId).hasValue(2L);
    }

    @Test
    void timePriorityWithinSamePrice() {
        OrderBook book = new OrderBook(SYMBOL);
        book.addOrder(limitBuy(1, 10, new BigDecimal("50000")));
        book.addOrder(limitBuy(2, 30, new BigDecimal("50000")));
        book.addOrder(limitBuy(3, 20, new BigDecimal("50000")));

        List<Order> bids = book.getBids();
        assertThat(bids).map(Order::orderId).containsExactly(1L, 3L, 2L);
    }

    @Test
    void cancellationRemovesOrder() {
        OrderBook book = new OrderBook(SYMBOL);
        book.addOrder(limitBuy(1, 10, new BigDecimal("50000")));
        book.addOrder(limitBuy(2, 20, new BigDecimal("50000")));

        boolean removed = book.cancelOrder(1L);

        assertThat(removed).isTrue();
        assertThat(book.bidCount()).isEqualTo(1);
        assertThat(book.bestBid()).map(Order::orderId).hasValue(2L);
        assertThat(book.cancelOrder(1L)).isFalse();
    }

    @Test
    void cancellationOfOnlyOrderAtPriceLevelRemovesTheLevel() {
        OrderBook book = new OrderBook(SYMBOL);
        book.addOrder(limitBuy(1, 10, new BigDecimal("50000")));
        book.addOrder(limitBuy(2, 20, new BigDecimal("51000")));

        book.cancelOrder(1L);

        assertThat(book.bestBidPrice()).isEqualTo(new BigDecimal("51000"));
        assertThat(book.bidCount()).isEqualTo(1);
    }

    @Test
    void rejectsNonLimitOrder() {
        OrderBook book = new OrderBook(SYMBOL);
        Order market = new Order(
                1L, "cid-1", 100L, SYMBOL, Side.BUY, OrderType.MARKET,
                null, new BigDecimal("1"), new BigDecimal("1"),
                1L, T0, OrderStatus.OPEN);

        assertThatThrownBy(() -> book.addOrder(market))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only LIMIT orders");
    }

    @Test
    void rejectsOrderWithWrongSymbol() {
        OrderBook book = new OrderBook(SYMBOL);
        Order otherSymbol = new Order(
                1L, "cid-1", 100L, "ETH-USD", Side.BUY, OrderType.LIMIT,
                new BigDecimal("100"), new BigDecimal("1"), new BigDecimal("1"),
                1L, T0, OrderStatus.OPEN);

        assertThatThrownBy(() -> book.addOrder(otherSymbol))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void repeatedIdenticalInputsProduceSameBookSnapshot() {
        List<Order> orders = List.of(
                limitBuy(1, 1, new BigDecimal("50000")),
                limitBuy(2, 2, new BigDecimal("50000")),
                limitSell(3, 3, new BigDecimal("51000")),
                limitSell(4, 4, new BigDecimal("50000"))
        );

        List<Order> snapshot1 = buildSnapshot(orders);
        List<Order> snapshot2 = buildSnapshot(orders);

        assertThat(snapshot1).map(Order::orderId).containsExactlyElementsOf(
                snapshot2.stream().map(Order::orderId).toList());
    }

    @Test
    void bidAndAskViewsAreSortedByPriority() {
        OrderBook book = new OrderBook(SYMBOL);
        book.addOrder(limitBuy(1, 1, new BigDecimal("50000")));
        book.addOrder(limitBuy(2, 2, new BigDecimal("51000")));
        book.addOrder(limitSell(3, 3, new BigDecimal("51000")));
        book.addOrder(limitSell(4, 4, new BigDecimal("50000")));

        assertThat(book.getBids()).map(Order::orderId).containsExactly(2L, 1L);
        assertThat(book.getAsks()).map(Order::orderId).containsExactly(4L, 3L);
    }

    private static List<Order> buildSnapshot(List<Order> orders) {
        OrderBook book = new OrderBook(SYMBOL);
        orders.forEach(book::addOrder);
        List<Order> bids = book.getBids();
        List<Order> asks = book.getAsks();
        List<Order> snapshot = new java.util.ArrayList<>(bids.size() + asks.size());
        snapshot.addAll(bids);
        snapshot.addAll(asks);
        return snapshot;
    }

    @Test
    void replaceOrderUpdatesQuantityInPlace() {
        OrderBook book = new OrderBook(SYMBOL);
        Order original = limitBuy(1, 1, new BigDecimal("50000"));
        book.addOrder(original);

        Order partiallyFilled = new Order(
                1L, "cid-1", 100L, SYMBOL, Side.BUY, OrderType.LIMIT,
                new BigDecimal("50000"), new BigDecimal("1"), new BigDecimal("0.5"),
                1L, T0, OrderStatus.PARTIALLY_FILLED);

        assertThat(book.replaceOrder(1L, partiallyFilled)).isTrue();
        assertThat(book.findOrder(1L)).hasValue(partiallyFilled);
        assertThat(book.getBids()).map(Order::remainingQuantity).containsExactly(new BigDecimal("0.5"));
    }

    @Test
    void replaceOrderRejectsDifferentPriceOrSide() {
        OrderBook book = new OrderBook(SYMBOL);
        book.addOrder(limitBuy(1, 1, new BigDecimal("50000")));

        Order differentPrice = limitBuy(1, 1, new BigDecimal("51000"));
        assertThatThrownBy(() -> book.replaceOrder(1L, differentPrice))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different price");
    }

    private static Order limitBuy(long orderId, long sequence, BigDecimal price) {
        return new Order(
                orderId, "cid-" + orderId, 100L, SYMBOL, Side.BUY, OrderType.LIMIT,
                price, BigDecimal.ONE, BigDecimal.ONE,
                sequence, T0, OrderStatus.OPEN);
    }

    private static Order limitSell(long orderId, long sequence, BigDecimal price) {
        return new Order(
                orderId, "cid-" + orderId, 100L, SYMBOL, Side.SELL, OrderType.LIMIT,
                price, BigDecimal.ONE, BigDecimal.ONE,
                sequence, T0, OrderStatus.OPEN);
    }
}
