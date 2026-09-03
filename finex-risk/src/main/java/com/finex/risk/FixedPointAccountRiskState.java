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
import com.finex.common.numeric.FixedPoint;

public final class FixedPointAccountRiskState {

    private final long accountId;
    private long cash;
    private long position;
    private final Map<Long, Long> reservedCashByOrder = new ConcurrentHashMap<>();
    private final Map<Long, Long> reservedPositionByOrder = new ConcurrentHashMap<>();
    private final Map<Long, Long> reservationPriceByOrder = new ConcurrentHashMap<>();
    private long totalReservedCash;
    private long totalReservedPosition;
    private final Queue<Instant> orderTimestamps = new ArrayDeque<>();

    public FixedPointAccountRiskState(long accountId, BigDecimal cash, BigDecimal position) {
        this.accountId = accountId;
        this.cash = FixedPoint.toRawExact(cash);
        this.position = FixedPoint.toRawExact(position);
        if (this.cash < 0) {
            throw new IllegalArgumentException("cash must not be negative");
        }
    }

    public long accountId() {
        return accountId;
    }

    public long cashRaw() {
        return cash;
    }

    public long positionRaw() {
        return position;
    }

    public long reservedCashRaw() {
        return totalReservedCash;
    }

    public long reservedPositionRaw() {
        return totalReservedPosition;
    }

    public long projectedPositionRaw() {
        return Math.addExact(position, totalReservedPosition);
    }

    public long availableCashRaw() {
        return Math.subtractExact(cash, totalReservedCash);
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

    public void reserveOrder(long orderId, long reservationPrice, long quantity, Side side) {
        long signedQuantity = side == Side.BUY ? quantity : Math.negateExact(quantity);
        long cashToReserve = side == Side.BUY
                ? FixedPoint.multiplyExactRaw(reservationPrice, quantity)
                : 0;
        Long oldCash = reservedCashByOrder.put(orderId, cashToReserve);
        Long oldPosition = reservedPositionByOrder.put(orderId, signedQuantity);
        reservationPriceByOrder.put(orderId, reservationPrice);
        if (oldCash != null) {
            totalReservedCash = Math.subtractExact(totalReservedCash, oldCash);
        }
        if (oldPosition != null) {
            totalReservedPosition = Math.subtractExact(totalReservedPosition, oldPosition);
        }
        totalReservedCash = Math.addExact(totalReservedCash, cashToReserve);
        totalReservedPosition = Math.addExact(totalReservedPosition, signedQuantity);
    }

    public void releaseOrder(long orderId) {
        Long oldCash = reservedCashByOrder.remove(orderId);
        Long oldPosition = reservedPositionByOrder.remove(orderId);
        reservationPriceByOrder.remove(orderId);
        if (oldCash != null) {
            totalReservedCash = Math.subtractExact(totalReservedCash, oldCash);
        }
        if (oldPosition != null) {
            totalReservedPosition = Math.subtractExact(totalReservedPosition, oldPosition);
        }
    }

    public void applyTrade(long orderId, Trade trade, Side side) {
        long price = FixedPoint.toRawExact(trade.price());
        long quantity = FixedPoint.toRawExact(trade.quantity());
        long tradeValue = FixedPoint.multiplyExactRaw(price, quantity);
        long reservationPrice = reservationPriceByOrder.getOrDefault(orderId, price);
        long releaseCash = FixedPoint.multiplyExactRaw(reservationPrice, quantity);
        long signedQuantity = side == Side.BUY ? quantity : Math.negateExact(quantity);

        if (side == Side.BUY) {
            cash = Math.subtractExact(cash, tradeValue);
            position = Math.addExact(position, quantity);
            long remaining = Math.subtractExact(reservedCashByOrder.getOrDefault(orderId, 0L), releaseCash);
            updateCashReservation(orderId, remaining);
        } else {
            cash = Math.addExact(cash, tradeValue);
            position = Math.subtractExact(position, quantity);
        }

        long remainingPosition = Math.subtractExact(
                reservedPositionByOrder.getOrDefault(orderId, 0L), signedQuantity);
        updatePositionReservation(orderId, remainingPosition);
    }

    private void updateCashReservation(long orderId, long remaining) {
        Long previous = remaining == 0
                ? reservedCashByOrder.remove(orderId)
                : reservedCashByOrder.put(orderId, remaining);
        totalReservedCash = Math.subtractExact(totalReservedCash, previous == null ? 0 : previous);
        if (remaining != 0) {
            totalReservedCash = Math.addExact(totalReservedCash, remaining);
        }
    }

    private void updatePositionReservation(long orderId, long remaining) {
        Long previous = remaining == 0
                ? reservedPositionByOrder.remove(orderId)
                : reservedPositionByOrder.put(orderId, remaining);
        totalReservedPosition = Math.subtractExact(totalReservedPosition, previous == null ? 0 : previous);
        if (remaining != 0) {
            totalReservedPosition = Math.addExact(totalReservedPosition, remaining);
        }
    }
}
