package com.finex.orderbook;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import com.finex.common.domain.Order;
import com.finex.common.domain.enums.OrderStatus;
import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;

/**
 * Baseline price-time-priority order book (Master Plan §9). Uses TreeMap for the
 * price-level index; within a level, orders are kept in `sequence` order (earliest first).
 * The matching engine (Phase 4) will consume the top of book and update order copies.
 *
 * <p>This implementation prioritises <em>correctness</em> and <em>determinism</em> over
 * performance. Alternative structures will be benchmarked in later phases (§9, §17).</p>
 *
 * <p>Price priority:</p>
 * <ul>
 *   <li>BUY: highest price first</li>
 *   <li>SELL: lowest price first</li>
 * </ul>
 * <p>Time priority: lower {@link Order#sequence()} first at the same price.</p>
 */
public class OrderBook {

    private final String symbol;

    // Bids: highest price first; asks: lowest price first. Lists sorted by sequence asc.
    private final NavigableMap<BigDecimal, List<Order>> bids;
    private final NavigableMap<BigDecimal, List<Order>> asks;

    // Fast id lookup for cancellation.
    private final Map<Long, Order> orderById;

    public OrderBook(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        this.symbol = symbol;
        this.bids = new TreeMap<>(Comparator.reverseOrder());
        this.asks = new TreeMap<>(Comparator.naturalOrder());
        this.orderById = new ConcurrentHashMap<>();
    }

    public String symbol() {
        return symbol;
    }

    /**
     * Adds a resting order to the book.
     *
     * @param order the order to add; must be a LIMIT order in a resting status with
     *              positive remaining quantity
     */
    public void addOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("order must not be null");
        }
        if (!order.symbol().equals(symbol)) {
            throw new IllegalArgumentException("order symbol " + order.symbol() + " does not match book symbol " + symbol);
        }
        if (order.type() != OrderType.LIMIT) {
            throw new IllegalArgumentException("only LIMIT orders can rest in the order book");
        }
        if (order.remainingQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("remaining quantity must be positive to add to book");
        }
        if (order.status() != OrderStatus.NEW && order.status() != OrderStatus.OPEN
                && order.status() != OrderStatus.PARTIALLY_FILLED) {
            throw new IllegalArgumentException("cannot add order with status " + order.status());
        }

        NavigableMap<BigDecimal, List<Order>> sideBook = sideBook(order.side());
        BigDecimal price = order.price();

        List<Order> level = sideBook.computeIfAbsent(price, p -> new ArrayList<>());
        int insertionPoint = findSequenceInsertionPoint(level, order.sequence());
        level.add(insertionPoint, order);
        orderById.put(order.orderId(), order);
    }

    /**
     * Replaces an existing resting order with a new copy (same id, price, and side) in-place.
     * Useful for updating the remaining quantity of a partially filled order without a
     * TreeMap remove/re-insert.
     *
     * @return true if the order was found and replaced
     */
    public boolean replaceOrder(long orderId, Order newOrder) {
        if (newOrder == null) {
            throw new IllegalArgumentException("newOrder must not be null");
        }
        Order old = orderById.get(orderId);
        if (old == null) {
            return false;
        }
        if (old.side() != newOrder.side()) {
            throw new IllegalArgumentException("cannot replace order with different side");
        }
        if (old.price().compareTo(newOrder.price()) != 0) {
            throw new IllegalArgumentException("cannot replace order with different price");
        }

        NavigableMap<BigDecimal, List<Order>> sideBook = sideBook(old.side());
        List<Order> level = sideBook.get(old.price());
        if (level == null) {
            return false;
        }
        for (int i = 0; i < level.size(); i++) {
            if (level.get(i).orderId() == orderId) {
                level.set(i, newOrder);
                orderById.put(orderId, newOrder);
                return true;
            }
        }
        return false;
    }

    /**
     * Cancels an order by id. Returns true if the order was found and removed.
     */
    public boolean cancelOrder(long orderId) {
        Order order = orderById.remove(orderId);
        if (order == null) {
            return false;
        }

        NavigableMap<BigDecimal, List<Order>> sideBook = sideBook(order.side());
        List<Order> level = sideBook.get(order.price());
        if (level == null) {
            return false;
        }
        boolean removed = level.remove(order);
        if (level.isEmpty()) {
            sideBook.remove(order.price());
        }
        return removed;
    }

    public Optional<Order> bestBid() {
        return firstOrder(bids);
    }

    public Optional<Order> bestAsk() {
        return firstOrder(asks);
    }

    public BigDecimal bestBidPrice() {
        return bids.isEmpty() ? null : bids.firstKey();
    }

    public BigDecimal bestAskPrice() {
        return asks.isEmpty() ? null : asks.firstKey();
    }

    public boolean isEmpty() {
        return bids.isEmpty() && asks.isEmpty();
    }

    public int bidCount() {
        return count(bids);
    }

    public int askCount() {
        return count(asks);
    }

    public int orderCount() {
        return orderById.size();
    }

    /**
     * Returns the order currently resting in the book with the given id, or empty if it
     * has been filled or cancelled.
     */
    public Optional<Order> findOrder(long orderId) {
        return Optional.ofNullable(orderById.get(orderId));
    }

    /**
     * Returns a flat view of all bids in priority order (highest price first, then
     * earliest sequence within a price).
     */
    public List<Order> getBids() {
        return flatView(bids);
    }

    /**
     * Returns a flat view of all asks in priority order (lowest price first, then
     * earliest sequence within a price).
     */
    public List<Order> getAsks() {
        return flatView(asks);
    }

    private NavigableMap<BigDecimal, List<Order>> sideBook(Side side) {
        return side == Side.BUY ? bids : asks;
    }

    private static Optional<Order> firstOrder(NavigableMap<BigDecimal, List<Order>> book) {
        if (book.isEmpty()) {
            return Optional.empty();
        }
        List<Order> topLevel = book.firstEntry().getValue();
        return topLevel.isEmpty() ? Optional.empty() : Optional.of(topLevel.get(0));
    }

    private static int count(NavigableMap<BigDecimal, List<Order>> book) {
        return book.values().stream().mapToInt(List::size).sum();
    }

    private static List<Order> flatView(NavigableMap<BigDecimal, List<Order>> book) {
        List<Order> result = new ArrayList<>(count(book));
        for (List<Order> level : book.values()) {
            result.addAll(level);
        }
        return Collections.unmodifiableList(result);
    }

    private static int findSequenceInsertionPoint(List<Order> level, long newSequence) {
        for (int i = level.size() - 1; i >= 0; i--) {
            if (level.get(i).sequence() <= newSequence) {
                return i + 1;
            }
        }
        return 0;
    }
}
