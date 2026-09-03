package com.finex.matching;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.finex.common.domain.Order;
import com.finex.common.domain.Trade;
import com.finex.common.domain.enums.OrderStatus;
import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;
import com.finex.orderbook.OrderBook;

/**
 * Single-symbol, deterministic price-time-priority matching engine (Master Plan §8).
 *
 * <p>This is the baseline implementation (V1). It operates on a single {@link OrderBook}
 * and is intentionally single-threaded and simple: for each incoming order it walks the
 * opposite side of the book from the top, matching at the resting order's price until the
 * incoming order is fully filled, no more marketable liquidity exists, or (for MARKET
 * orders) the book is exhausted.</p>
 *
 * <p>Determinism is preserved by: fixed price-time priority, immutable {@link Order}
 * copies, and a monotonically increasing {@code tradeSequence} generator. Wall-clock time
 * is supplied by the caller, so tests can run with a fixed {@link Instant}.</p>
 */
public class MatchingEngine {

    private final String symbol;
    private final OrderBook book;
    private final AtomicLong tradeSequence;

    public MatchingEngine(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        this.symbol = symbol;
        this.book = new OrderBook(symbol);
        this.tradeSequence = new AtomicLong(0);
    }

    public String symbol() {
        return symbol;
    }

    public OrderBook orderBook() {
        return book;
    }

    public boolean cancelOrder(long orderId) {
        return book.cancelOrder(orderId);
    }

    /**
     * Matches an incoming order against the resting order book and returns the result.
     *
     * @param order the incoming order; must belong to this engine's symbol
     * @param now   timestamp to stamp on trades and updated orders
     */
    public MatchResult placeOrder(Order order, Instant now) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        if (!order.symbol().equals(symbol)) {
            throw new IllegalArgumentException("order symbol " + order.symbol() + " does not match engine symbol " + symbol);
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }

        List<Trade> trades = new ArrayList<>();
        Map<Long, Order> updatedOrders = new HashMap<>();
        Order current = order;

        while (current.remainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
            Optional<Order> top = topOfOppositeSide(current.side());
            if (top.isEmpty()) {
                break;
            }

            Order resting = top.get();
            if (!isMarketable(current, resting)) {
                break;
            }

            BigDecimal matchQty = current.remainingQuantity().min(resting.remainingQuantity());
            BigDecimal matchPrice = resting.price();

            long buyOrderId = current.side() == Side.BUY ? current.orderId() : resting.orderId();
            long sellOrderId = current.side() == Side.BUY ? resting.orderId() : current.orderId();
            long buyerAccountId = current.side() == Side.BUY ? current.accountId() : resting.accountId();
            long sellerAccountId = current.side() == Side.BUY ? resting.accountId() : current.accountId();

            long seq = tradeSequence.incrementAndGet();
            Trade trade = new Trade(
                    seq, buyOrderId, sellOrderId, symbol, matchPrice, matchQty,
                    now, buyerAccountId, sellerAccountId, seq);
            trades.add(trade);

            OrderStatus incomingStatus = afterFillStatus(current.remainingQuantity(), matchQty);
            current = current.withFill(matchQty, incomingStatus, now);

            OrderStatus restingStatus = afterFillStatus(resting.remainingQuantity(), matchQty);
            Order updatedResting = resting.withFill(matchQty, restingStatus, now);

            updatedOrders.put(current.orderId(), current);
            updatedOrders.put(updatedResting.orderId(), updatedResting);

            // Replace the resting order in the book without a TreeMap remove/re-insert.
            if (updatedResting.remainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
                book.replaceOrder(resting.orderId(), updatedResting);
            } else {
                book.cancelOrder(resting.orderId());
            }
        }

        if (current.type() == OrderType.LIMIT && current.remainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
            // A new limit order that has not been fully matched rests in the book.
            book.addOrder(current);
            updatedOrders.put(current.orderId(), current);
            return new MatchResult(current, List.copyOf(trades), true, Map.copyOf(updatedOrders));
        }

        if (current.type() == OrderType.MARKET && current.remainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
            // Unfilled market-order remainder is cancelled.
            current = current.cancelled(now);
        }

        updatedOrders.put(current.orderId(), current);
        return new MatchResult(current, List.copyOf(trades), false, Map.copyOf(updatedOrders));
    }

    private Optional<Order> topOfOppositeSide(Side side) {
        return side == Side.BUY ? book.bestAsk() : book.bestBid();
    }

    private static boolean isMarketable(Order incoming, Order resting) {
        if (incoming.type() == OrderType.MARKET) {
            return true;
        }
        // incoming is LIMIT
        if (incoming.side() == Side.BUY) {
            // buy limit is marketable if it is priced at or above the best ask
            return incoming.price().compareTo(resting.price()) >= 0;
        }
        // sell limit is marketable if it is priced at or below the best bid
        return incoming.price().compareTo(resting.price()) <= 0;
    }

    private static OrderStatus afterFillStatus(BigDecimal beforeQty, BigDecimal fillQty) {
        BigDecimal remaining = beforeQty.subtract(fillQty);
        return remaining.compareTo(BigDecimal.ZERO) == 0 ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
    }
}
