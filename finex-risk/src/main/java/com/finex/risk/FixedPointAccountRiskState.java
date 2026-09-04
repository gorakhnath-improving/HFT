package com.finex.risk;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Queue;

import com.finex.common.domain.Trade;
import com.finex.common.domain.enums.Side;
import com.finex.common.numeric.FixedPoint;

public final class FixedPointAccountRiskState {

    private final long accountId;
    private long cash;
    private long position;
    private final LongLongHashMap reservations = new LongLongHashMap();
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
        long oldCash = reservations.put(orderId, cashToReserve, signedQuantity, reservationPrice);
        if (reservations.previousPresent()) {
            totalReservedCash = Math.subtractExact(totalReservedCash, oldCash);
            totalReservedPosition = Math.subtractExact(
                    totalReservedPosition, reservations.previousSecondaryValue());
        }
        totalReservedCash = Math.addExact(totalReservedCash, cashToReserve);
        totalReservedPosition = Math.addExact(totalReservedPosition, signedQuantity);
    }

    public void releaseOrder(long orderId) {
        long oldCash = reservations.remove(orderId);
        if (reservations.previousPresent()) {
            totalReservedCash = Math.subtractExact(totalReservedCash, oldCash);
            totalReservedPosition = Math.subtractExact(
                    totalReservedPosition, reservations.previousSecondaryValue());
        }
    }

    public void applyTrade(long orderId, Trade trade, Side side) {
        long price = FixedPoint.toRawExact(trade.price());
        long quantity = FixedPoint.toRawExact(trade.quantity());
        long tradeValue = FixedPoint.multiplyExactRaw(price, quantity);
        long reservedCash = reservations.get(orderId);
        boolean hadReservation = reservations.previousPresent();
        long reservedPosition = reservations.previousSecondaryValue();
        long reservationPrice = hadReservation ? reservations.previousTertiaryValue() : price;
        long releaseCash = FixedPoint.multiplyExactRaw(reservationPrice, quantity);
        long signedQuantity = side == Side.BUY ? quantity : Math.negateExact(quantity);
        long remainingCash = side == Side.BUY ? Math.subtractExact(reservedCash, releaseCash) : reservedCash;
        long remainingPosition = Math.subtractExact(reservedPosition, signedQuantity);

        if (side == Side.BUY) {
            cash = Math.subtractExact(cash, tradeValue);
            position = Math.addExact(position, quantity);
        } else {
            cash = Math.addExact(cash, tradeValue);
            position = Math.subtractExact(position, quantity);
        }

        if (hadReservation) {
            totalReservedCash = Math.subtractExact(totalReservedCash, reservedCash);
            totalReservedPosition = Math.subtractExact(totalReservedPosition, reservedPosition);
        }
        if (remainingCash == 0 && remainingPosition == 0) {
            if (hadReservation) {
                reservations.remove(orderId);
            }
        } else {
            reservations.put(orderId, remainingCash, remainingPosition, reservationPrice);
            totalReservedCash = Math.addExact(totalReservedCash, remainingCash);
            totalReservedPosition = Math.addExact(totalReservedPosition, remainingPosition);
        }
    }
}
