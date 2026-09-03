package com.finex.risk;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

import com.finex.common.domain.Trade;
import com.finex.common.domain.enums.Side;

/**
 * Mutable per-account risk snapshot. Tracks cash, position, and open-order reservations.
 * Not thread-safe by itself; the owner (e.g. {@link com.finex.api.order.OrderService})
 * must serialize access per account.
 */
public class AccountRiskState {

    private final long accountId;
    private BigDecimal cash;
    private BigDecimal position;
    private final RiskConfig config;

    // orderId -> cash reserved for the whole remaining quantity (BUY orders only)
    private final Map<Long, BigDecimal> reservedCashByOrder = new ConcurrentHashMap<>();
    // orderId -> signed quantity reserved (positive BUY, negative SELL)
    private final Map<Long, BigDecimal> reservedPositionByOrder = new ConcurrentHashMap<>();
    // orderId -> reservation price used when the order was accepted
    private final Map<Long, BigDecimal> reservationPriceByOrder = new ConcurrentHashMap<>();

    // Running totals so reservedCash()/reservedPosition() are O(1) instead of a full
    // map scan on every risk validation. Updated whenever a reservation changes.
    private BigDecimal totalReservedCash = BigDecimal.ZERO;
    private BigDecimal totalReservedPosition = BigDecimal.ZERO;

    // Sliding window of order timestamps for rate limiting.
    private final Queue<Instant> orderTimestamps = new ArrayDeque<>();

    public AccountRiskState(long accountId, BigDecimal cash, BigDecimal position, RiskConfig config) {
        if (cash == null || cash.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("cash must not be negative");
        }
        if (position == null) {
            throw new IllegalArgumentException("position must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.accountId = accountId;
        this.cash = cash;
        this.position = position;
        this.config = config;
    }

    public long accountId() {
        return accountId;
    }

    public BigDecimal cash() {
        return cash;
    }

    public BigDecimal position() {
        return position;
    }

    public RiskConfig config() {
        return config;
    }

    /**
     * Returns the total cash currently reserved across all open orders.
     */
    public BigDecimal reservedCash() {
        return totalReservedCash;
    }

    /**
     * Returns the total signed position currently reserved across all open orders.
     */
    public BigDecimal reservedPosition() {
        return totalReservedPosition;
    }

    public BigDecimal availableCash() {
        return cash.subtract(totalReservedCash);
    }

    /**
     * Returns the projected position including current holdings and all reserved open orders.
     */
    public BigDecimal projectedPosition() {
        return position.add(totalReservedPosition);
    }

    public void addOrderTimestamp(Instant now) {
        orderTimestamps.add(now);
    }

    public int ordersInWindow(Instant now, Duration window) {
        pruneTimestamps(now, window);
        return orderTimestamps.size();
    }

    public void pruneTimestamps(Instant now, Duration window) {
        Instant cutoff = now.minus(window);
        while (!orderTimestamps.isEmpty() && orderTimestamps.peek().isBefore(cutoff)) {
            orderTimestamps.poll();
        }
    }

    /**
     * Reserves cash/position for an accepted order.
     *
     * @param orderId          the order id
     * @param reservationPrice price used for reservation (limit price or estimated market price)
     * @param quantity         remaining quantity of the order
     * @param side             BUY or SELL
     */
    public void reserveOrder(long orderId, BigDecimal reservationPrice, BigDecimal quantity, Side side) {
        BigDecimal signedQty = side == Side.BUY ? quantity : quantity.negate();
        BigDecimal cashToReserve = side == Side.BUY
                ? reservationPrice.multiply(quantity)
                : BigDecimal.ZERO;

        // If the order id is being reused (should not happen in normal flow), remove the
        // old contribution before adding the new one so the running totals stay accurate.
        BigDecimal oldCash = reservedCashByOrder.put(orderId, cashToReserve);
        BigDecimal oldPosition = reservedPositionByOrder.put(orderId, signedQty);
        reservationPriceByOrder.put(orderId, reservationPrice);

        if (oldCash != null) {
            totalReservedCash = totalReservedCash.subtract(oldCash);
        }
        if (oldPosition != null) {
            totalReservedPosition = totalReservedPosition.subtract(oldPosition);
        }
        totalReservedCash = totalReservedCash.add(cashToReserve);
        totalReservedPosition = totalReservedPosition.add(signedQty);
    }

    public boolean hasReservation(long orderId) {
        return reservedCashByOrder.containsKey(orderId);
    }

    /**
     * Releases the remaining reservation for an order (used on cancellation or when the
     * order is fully filled and no longer resting).
     */
    public void releaseOrder(long orderId) {
        BigDecimal oldCash = reservedCashByOrder.remove(orderId);
        BigDecimal oldPosition = reservedPositionByOrder.remove(orderId);
        reservationPriceByOrder.remove(orderId);

        if (oldCash != null) {
            totalReservedCash = totalReservedCash.subtract(oldCash);
        }
        if (oldPosition != null) {
            totalReservedPosition = totalReservedPosition.subtract(oldPosition);
        }
    }

    /**
     * Updates cash and position for a trade and reduces the corresponding reservation.
     */
    public void applyTrade(long orderId, Trade trade, Side side) {
        BigDecimal tradeValue = trade.price().multiply(trade.quantity());
        BigDecimal reservationPrice = reservationPriceByOrder.getOrDefault(orderId, trade.price());
        BigDecimal releaseCash = reservationPrice.multiply(trade.quantity());
        BigDecimal signedQty = side == Side.BUY ? trade.quantity() : trade.quantity().negate();

        if (side == Side.BUY) {
            cash = cash.subtract(tradeValue);
            position = position.add(trade.quantity());
            BigDecimal remaining = reservedCashByOrder.getOrDefault(orderId, BigDecimal.ZERO).subtract(releaseCash);
            updateCashReservation(orderId, remaining, releaseCash);
        } else {
            cash = cash.add(tradeValue);
            position = position.subtract(trade.quantity());
        }

        BigDecimal remainingPosition = reservedPositionByOrder.getOrDefault(orderId, BigDecimal.ZERO).subtract(signedQty);
        updatePositionReservation(orderId, remainingPosition, signedQty);
    }

    private void updateCashReservation(long orderId, BigDecimal remaining, BigDecimal released) {
        if (remaining.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal removed = reservedCashByOrder.remove(orderId);
            if (removed != null) {
                totalReservedCash = totalReservedCash.subtract(removed);
            }
        } else {
            BigDecimal previous = reservedCashByOrder.put(orderId, remaining);
            // The map value changed from previous to remaining; total changes by that delta.
            totalReservedCash = totalReservedCash.subtract(previous == null ? BigDecimal.ZERO : previous).add(remaining);
        }
    }

    private void updatePositionReservation(long orderId, BigDecimal remaining, BigDecimal signedQty) {
        if (remaining.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal removed = reservedPositionByOrder.remove(orderId);
            if (removed != null) {
                totalReservedPosition = totalReservedPosition.subtract(removed);
            }
        } else {
            BigDecimal previous = reservedPositionByOrder.put(orderId, remaining);
            totalReservedPosition = totalReservedPosition.subtract(previous == null ? BigDecimal.ZERO : previous).add(remaining);
        }
    }
}
